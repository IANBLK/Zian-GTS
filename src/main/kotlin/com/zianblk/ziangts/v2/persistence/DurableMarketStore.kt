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
        appendLine("ZIANGTS_V2|1")
        offers.values.forEach { o ->
            appendLine(listOf("O", o.id.value, o.owner.playerId, enc(o.owner.displayName),
                o.payment.adapter, o.payment.currency, o.payment.amount, o.pokemon.pokemonId,
                o.pokemon.species, o.pokemon.level, o.pokemon.shiny, o.pokemon.alpha,
                enc(o.pokemon.serialized), o.publishedAt.toEpochMilli(), o.expiresAt.toEpochMilli()).joinToString("|"))
        }
        proceeds.forEach { (pair, amount) ->
            appendLine(listOf("P", pair.first, pair.second.adapter, pair.second.currency, amount).joinToString("|"))
        }
    }

    private fun load() {
        if (!Files.exists(path)) return
        val lines = Files.readAllLines(path, Charsets.UTF_8)
        require(lines.firstOrNull() == "ZIANGTS_V2|1") { "unsupported/corrupt V2 market store" }
        lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
            val p = line.split("|")
            when (p[0]) {
                "O" -> {
                    require(p.size == 15)
                    val offer = TradeOffer(OfferId(UUID.fromString(p[1])),
                        OfferOwner(UUID.fromString(p[2]), dec(p[3])),
                        PaymentSpec(p[4], p[5], p[6].toLong()),
                        PokemonEnvelope(
                            pokemonId = UUID.fromString(p[7]), species = p[8], level = p[9].toInt(),
                            shiny = p[10].toBooleanStrict(), alpha = p[11].toBooleanStrict(),
                            serialized = dec(p[12])
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

    private fun enc(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))
    private fun dec(s: String) = String(Base64.getUrlDecoder().decode(s), Charsets.UTF_8)
}
