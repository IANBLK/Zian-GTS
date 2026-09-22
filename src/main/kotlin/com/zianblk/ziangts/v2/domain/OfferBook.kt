package com.zianblk.ziangts.v2.domain

import java.time.Instant
import java.util.UUID

enum class OfferFilter { ALL, SHINY, ALPHA, LEGENDARY, LEGENDARY_SHINY, OWN }
enum class OfferOwnership { ANY, MINE, OTHERS }
enum class OfferSort { NEWEST, OLDEST, PRICE_LOW, PRICE_HIGH, LEVEL_LOW, LEVEL_HIGH }

data class MarketQuery(
    val viewer: UUID,
    val page: Int = 1,
    val pageSize: Int = 6,
    val filter: OfferFilter = OfferFilter.ALL,
    val sort: OfferSort = OfferSort.NEWEST,
    val ownership: OfferOwnership = OfferOwnership.ANY,
    val now: Instant = Instant.now()
) {
    init {
        require(page >= 1)
        require(pageSize in 1..50)
    }
}

interface OfferBook {
    fun find(id: OfferId): TradeOffer?
    fun containsPokemon(pokemonId: UUID): Boolean
    fun countOwnedBy(owner: UUID): Int
    fun add(offer: TradeOffer)
    fun remove(id: OfferId): TradeOffer?
    fun all(): List<TradeOffer>
}

class InMemoryOfferBook : OfferBook {
    private val offers = linkedMapOf<OfferId, TradeOffer>()

    override fun find(id: OfferId) = offers[id]
    override fun containsPokemon(pokemonId: UUID) = offers.values.any { it.pokemon.pokemonId == pokemonId }
    override fun countOwnedBy(owner: UUID) = offers.values.count { it.owner.playerId == owner }
    override fun all() = offers.values.toList()

    override fun add(offer: TradeOffer) {
        require(offer.id !in offers) { "duplicate offer id" }
        require(!containsPokemon(offer.pokemon.pokemonId)) { "pokemon already offered" }
        offers[offer.id] = offer
    }

    override fun remove(id: OfferId): TradeOffer? = offers.remove(id)
}


/**
 * Pure market projection used by server commands/UI. It never mutates OfferBook.
 * Expired offers remain visible to their owner for retrieval but are excluded from the public market.
 */
data class MarketView(val offers: List<TradeOffer>, val total: Int, val page: Int, val pageSize: Int)

fun OfferBook.query(query: MarketQuery): MarketView {
    val visible = all().asSequence()
        .filter { offer ->
            when (query.filter) {
                OfferFilter.OWN -> offer.owner.playerId == query.viewer
                else -> offer.stateAt(query.now) == OfferState.CURRENT
            }
        }
        .filter { offer ->
            when (query.ownership) {
                OfferOwnership.ANY -> true
                OfferOwnership.MINE -> offer.owner.playerId == query.viewer
                OfferOwnership.OTHERS -> offer.owner.playerId != query.viewer
            }
        }
        .filter { offer ->
            when (query.filter) {
                OfferFilter.ALL, OfferFilter.OWN -> true
                OfferFilter.SHINY -> offer.pokemon.shiny
                OfferFilter.ALPHA -> offer.pokemon.alpha
                OfferFilter.LEGENDARY -> isLegendarySpecies(offer.pokemon.species)
                OfferFilter.LEGENDARY_SHINY -> offer.pokemon.shiny && isLegendarySpecies(offer.pokemon.species)
            }
        }
        .sortedWith(
            when (query.sort) {
                OfferSort.NEWEST -> compareByDescending<TradeOffer> { it.publishedAt }.thenBy { it.id.value }
                OfferSort.OLDEST -> compareBy<TradeOffer> { it.publishedAt }.thenBy { it.id.value }
                OfferSort.PRICE_LOW -> compareBy<TradeOffer> { it.payment.amount }.thenByDescending { it.publishedAt }
                OfferSort.PRICE_HIGH -> compareByDescending<TradeOffer> { it.payment.amount }.thenByDescending { it.publishedAt }
                OfferSort.LEVEL_LOW -> compareBy<TradeOffer> { it.pokemon.level }.thenByDescending { it.publishedAt }
                OfferSort.LEVEL_HIGH -> compareByDescending<TradeOffer> { it.pokemon.level }.thenByDescending { it.publishedAt }
            }
        )
        .toList()

    val from = ((query.page - 1) * query.pageSize).coerceAtMost(visible.size)
    val to = (from + query.pageSize).coerceAtMost(visible.size)
    return MarketView(visible.subList(from, to), visible.size, query.page, query.pageSize)
}

/**
 * Domain-only fallback list. Runtime integrations may later replace this with Cobblemon registry tags.
 * Keeping the decision here deterministic makes filters testable without Minecraft classes.
 */
private fun isLegendarySpecies(species: String): Boolean = species.substringAfter(':') in LEGENDARY_SPECIES

private val LEGENDARY_SPECIES = setOf(
    "articuno", "zapdos", "moltres", "mewtwo", "mew",
    "raikou", "entei", "suicune", "lugia", "ho_oh", "celebi",
    "regirock", "regice", "registeel", "latias", "latios", "kyogre", "groudon", "rayquaza", "jirachi", "deoxys",
    "uxie", "mesprit", "azelf", "dialga", "palkia", "heatran", "regigigas", "giratina", "cresselia", "phione", "manaphy", "darkrai", "shaymin", "arceus",
    "cobalion", "terrakion", "virizion", "tornadus", "thundurus", "reshiram", "zekrom", "landorus", "kyurem", "keldeo", "meloetta", "genesect",
    "xerneas", "yveltal", "zygarde", "diancie", "hoopa", "volcanion",
    "type_null", "silvally", "tapu_koko", "tapu_lele", "tapu_bulu", "tapu_fini", "cosmog", "cosmoem", "solgaleo", "lunala", "nihilego", "buzzwole", "pheromosa", "xurkitree", "celesteela", "kartana", "guzzlord", "necrozma", "magearna", "marshadow", "poipole", "naganadel", "stakataka", "blacephalon", "zeraora", "meltan", "melmetal",
    "zacian", "zamazenta", "eternatus", "kubfu", "urshifu", "zarude", "regieleki", "regidrago", "glastrier", "spectrier", "calyrex", "enamorus",
    "wo_chien", "chien_pao", "ting_lu", "chi_yu", "koraidon", "miraidon", "okidogi", "munkidori", "fezandipiti", "ogerpon", "terapagos", "pecharunt"
)
