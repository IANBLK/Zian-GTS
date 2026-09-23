package com.zianblk.ziangts.v2.persistence

import com.zianblk.ziangts.v2.domain.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Durable V2 test/reference store. Production NeoForge adapters may map the same
 * contracts to SavedData, but the core does not depend on Minecraft persistence.
 */
class DurableMarketStore(private val path: Path) : OfferBook, ProceedsStore {
    private val offers = linkedMapOf<OfferId, TradeOffer>()
    private val proceeds = linkedMapOf<Pair<UUID, ProceedsKey>, Long>()

    init { load() }

    override fun find(id: OfferId) = offers[id]
    override fun containsPokemon(pokemonId: UUID) = offers.values.any { it.pokemon.pokemonId == pokemonId }
    override fun countOwnedBy(owner: UUID) = offers.values.count { it.owner.playerId == owner }
    override fun all() = offers.values.toList()

    override fun add(offer: TradeOffer) {
        require(offer.id !in offers)
        require(!containsPokemon(offer.pokemon.pokemonId))
        offers[offer.id] = offer
        persistOrRollback { offers.remove(offer.id) }
    }

    override fun remove(id: OfferId): TradeOffer? {
        val old = offers.remove(id) ?: return null
        persistOrRollback { offers[id] = old }
        return old
    }

    override fun balance(owner: UUID, key: ProceedsKey) = proceeds[owner to key] ?: 0L

    override fun credit(owner: UUID, key: ProceedsKey, amount: Long) {
        require(amount > 0)
        val pair = owner to key
        val old = proceeds[pair]
        proceeds[pair] = Math.addExact(old ?: 0L, amount)
        persistOrRollback { if (old == null) proceeds.remove(pair) else proceeds[pair] = old }
    }

    override fun debit(owner: UUID, key: ProceedsKey, amount: Long) {
        require(amount > 0)
        val pair = owner to key
        val old = balance(owner, key)
        require(old >= amount)
        val next = old - amount
        if (next == 0L) proceeds.remove(pair) else proceeds[pair] = next
        persistOrRollback { proceeds[pair] = old }
    }

    override fun balances(owner: UUID): Map<ProceedsKey, Long> =
        proceeds.filterKeys { it.first == owner }.mapKeys { it.key.second }

    private fun persistOrRollback(rollback: () -> Unit) {
        try { persist() } catch (e: Exception) { rollback(); throw e }
    }

    private fun persist() {
        Files.createDirectories(path.toAbsolutePath().parent)
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        val bytes = serialize().toByteArray(Charsets.UTF_8)
        FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use {
            var b = ByteBuffer.wrap(bytes)
            while (b.hasRemaining()) it.write(b)
            it.force(true)
        }
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
        if (!System.getProperty("os.name", "").lowercase().startsWith("windows")) {
            FileChannel.open(path.toAbsolutePath().parent, StandardOpenOption.READ).use { it.force(true) }
        }
    }

    private fun serialize(): String = buildString {
        appendLine("ZIANGTS_V2|3")
        offers.values.forEach { o ->
            appendLine(listOf("O", o.id.value, o.owner.playerId, enc(o.owner.displayName),
                o.payment.adapter, o.payment.currency, o.payment.amount, o.pokemon.pokemonId,
                enc(o.pokemon.species), o.pokemon.level, o.pokemon.shiny, o.pokemon.alpha,
                enc(o.pokemon.serialized), o.publishedAt.toEpochMilli(), o.expiresAt.toEpochMilli(),
                enc(o.pokemon.gender), enc(o.pokemon.nature), enc(o.pokemon.ability),
                enc(o.pokemon.ivs.joinToString(",")), enc(o.pokemon.moves.joinToString("\u0000")),
                o.pokemon.legendary).joinToString("|"))
        }
        proceeds.forEach { (pair, amount) ->
            appendLine(listOf("P", pair.first, pair.second.adapter, pair.second.currency, amount).joinToString("|"))
        }
    }

    private fun load() {
        if (!Files.exists(path)) return
        val lines = Files.readAllLines(path, Charsets.UTF_8)
        val version = when (lines.firstOrNull()) {
            "ZIANGTS_V2|1" -> 1
            "ZIANGTS_V2|2" -> 2
            "ZIANGTS_V2|3" -> 3
            else -> throw IllegalArgumentException("unsupported/corrupt V2 market store")
        }
        lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
            val p = line.split("|")
            when (p[0]) {
                "O" -> {
                    require((version == 1 && p.size == 15) || (version >= 2 && p.size == 21))
                    val offer = TradeOffer(OfferId(UUID.fromString(p[1])),
                        OfferOwner(UUID.fromString(p[2]), dec(p[3])),
                        PaymentSpec(p[4], p[5], p[6].toLong()),
                        PokemonEnvelope(
                            pokemonId = UUID.fromString(p[7]), species = if (version >= 3) dec(p[8]) else p[8], level = p[9].toInt(),
                            shiny = p[10].toBooleanStrict(), alpha = p[11].toBooleanStrict(),
                            serialized = dec(p[12]),
                            gender = if (version >= 2) dec(p[15]) else "UNKNOWN",
                            nature = if (version >= 2) dec(p[16]) else "unknown",
                            ability = if (version >= 2) dec(p[17]) else "unknown",
                            ivs = if (version >= 2) dec(p[18]).split(",").filter { it.isNotBlank() }.map { it.toInt() }.let { if (it.size == 6) it else List(6) { 0 } } else List(6) { 0 },
                            moves = if (version >= 2) dec(p[19]).split("\u0000").filter { it.isNotBlank() } else emptyList(),
                            legendary = if (version >= 2) p[20].toBooleanStrict() else isLegendarySpecies(p[8])
                        ),
                        Instant.ofEpochMilli(p[13].toLong()), Instant.ofEpochMilli(p[14].toLong()))
                    require(offer.id !in offers && !containsPokemon(offer.pokemon.pokemonId))
                    offers[offer.id] = offer
                }
                "P" -> {
                    require(p.size == 5)
                    val pair = UUID.fromString(p[1]) to ProceedsKey(p[2], p[3])
                    require(pair !in proceeds)
                    proceeds[pair] = p[4].toLong().also { require(it > 0) }
                }
                else -> error("unknown V2 market record")
            }
        }
    }

    private fun isLegendarySpecies(species: String): Boolean =
        species.substringAfter(':') in setOf(
            "articuno","zapdos","moltres","mewtwo","mew","raikou","entei","suicune","lugia","ho_oh","celebi",
            "regirock","regice","registeel","latias","latios","kyogre","groudon","rayquaza","jirachi","deoxys",
            "uxie","mesprit","azelf","dialga","palkia","heatran","regigigas","giratina","cresselia","phione","manaphy","darkrai","shaymin","arceus",
            "cobalion","terrakion","virizion","tornadus","thundurus","reshiram","zekrom","landorus","kyurem","keldeo","meloetta","genesect",
            "xerneas","yveltal","zygarde","diancie","hoopa","volcanion","type_null","silvally","tapu_koko","tapu_lele","tapu_bulu","tapu_fini",
            "cosmog","cosmoem","solgaleo","lunala","nihilego","buzzwole","pheromosa","xurkitree","celesteela","kartana","guzzlord","necrozma",
            "magearna","marshadow","poipole","naganadel","stakataka","blacephalon","zeraora","meltan","melmetal","zacian","zamazenta","eternatus",
            "kubfu","urshifu","zarude","regieleki","regidrago","glastrier","spectrier","calyrex","enamorus","wo_chien","chien_pao","ting_lu",
            "chi_yu","koraidon","miraidon","okidogi","munkidori","fezandipiti","ogerpon","terapagos","pecharunt"
        )

    private fun enc(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))
    private fun dec(s: String) = String(Base64.getUrlDecoder().decode(s), Charsets.UTF_8)
}
