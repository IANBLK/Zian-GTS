package com.zianblk.ziangts.v2.domain

import java.time.Instant
import java.util.UUID

enum class OfferFilter { ALL, SHINY, ALPHA, LEGENDARY, LEGENDARY_SHINY, OWN }
enum class OfferSort { NEWEST, OLDEST, PRICE_LOW, PRICE_HIGH, LEVEL_LOW, LEVEL_HIGH }

data class MarketQuery(
    val viewer: UUID,
    val page: Int = 1,
    val pageSize: Int = 6,
    val filter: OfferFilter = OfferFilter.ALL,
    val sort: OfferSort = OfferSort.NEWEST,
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
