package com.zianblk.ziangts.v2.network

import com.zianblk.ziangts.v2.domain.*
import java.time.Instant
import java.util.UUID

/**
 * Transport-neutral V2 protocol contract.
 *
 * These DTOs contain no NeoForge/Cobblemon classes so codec and packet wiring can evolve
 * without leaking runtime objects into the domain layer.
 */
const val MARKET_PROTOCOL_VERSION = 1

data class MarketPageRequest(
    val protocolVersion: Int = MARKET_PROTOCOL_VERSION,
    val tab: MarketTab = MarketTab.MARKET,
    val page: Int = 1,
    val pageSize: Int = 6,
    val filter: OfferFilter = OfferFilter.ALL,
    val sort: OfferSort = OfferSort.NEWEST
) {
    init {
        require(protocolVersion == MARKET_PROTOCOL_VERSION) { "unsupported market protocol version" }
        require(page >= 1)
        require(pageSize in 1..50)
        require(tab != MarketTab.MARKET || filter != OfferFilter.OWN)
    }

    fun toScreenRequest(viewer: UUID, now: Instant): MarketScreenRequest =
        MarketScreenRequest(viewer, tab, page, pageSize, filter, sort, now)
}

data class MarketEntryDto(
    val offerId: UUID,
    val sellerId: UUID,
    val sellerName: String,
    val pokemonId: UUID,
    val species: String,
    val level: Int,
    val shiny: Boolean,
    val alpha: Boolean,
    val price: Long,
    val currency: String,
    val publishedAtEpochMilli: Long,
    val expiresAtEpochMilli: Long,
    val ownedByViewer: Boolean,
    val canBuy: Boolean,
    val canWithdraw: Boolean
)

data class MarketPageResponse(
    val protocolVersion: Int = MARKET_PROTOCOL_VERSION,
    val tab: MarketTab,
    val entries: List<MarketEntryDto>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val filter: OfferFilter,
    val sort: OfferSort
)

fun MarketScreenModel.toPageResponse(): MarketPageResponse =
    MarketPageResponse(
        tab = tab,
        entries = entries.map { entry ->
            val offer = entry.offer
            MarketEntryDto(
                offerId = offer.id.value,
                sellerId = offer.owner.playerId,
                sellerName = offer.owner.displayName,
                pokemonId = offer.pokemon.pokemonId,
                species = offer.pokemon.species,
                level = offer.pokemon.level,
                shiny = offer.pokemon.shiny,
                alpha = offer.pokemon.alpha,
                price = offer.payment.amount,
                currency = offer.payment.currency,
                publishedAtEpochMilli = offer.publishedAt.toEpochMilli(),
                expiresAtEpochMilli = offer.expiresAt.toEpochMilli(),
                ownedByViewer = entry.ownedByViewer,
                canBuy = entry.canBuy,
                canWithdraw = entry.canWithdraw
            )
        },
        total = total,
        page = page,
        pageSize = pageSize,
        totalPages = totalPages,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
        filter = filter,
        sort = sort
    )
