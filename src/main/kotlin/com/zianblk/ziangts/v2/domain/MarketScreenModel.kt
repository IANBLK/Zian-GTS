package com.zianblk.ziangts.v2.domain

/**
 * Server-owned projection for the final GTS screen.
 *
 * The client renders this model but does not decide which offers are visible,
 * whether an expired offer is public, or whether an offer belongs to the viewer.
 */
enum class MarketTab { MARKET, MY_OFFERS }

data class MarketScreenRequest(
    val viewer: java.util.UUID,
    val tab: MarketTab = MarketTab.MARKET,
    val page: Int = 1,
    val pageSize: Int = 6,
    val filter: OfferFilter = OfferFilter.ALL,
    val sort: OfferSort = OfferSort.NEWEST,
    val now: java.time.Instant = java.time.Instant.now()
) {
    init {
        require(page >= 1)
        require(pageSize in 1..50)
        require(tab != MarketTab.MARKET || filter != OfferFilter.OWN) {
            "OWN is represented by the MY_OFFERS tab"
        }
    }
}

data class MarketScreenEntry(
    val offer: TradeOffer,
    val ownedByViewer: Boolean,
    val canBuy: Boolean,
    val canWithdraw: Boolean
)

data class MarketScreenModel(
    val tab: MarketTab,
    val entries: List<MarketScreenEntry>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val filter: OfferFilter,
    val sort: OfferSort
)

fun OfferBook.screen(request: MarketScreenRequest): MarketScreenModel {
    val query = when (request.tab) {
        MarketTab.MARKET -> MarketQuery(
            viewer = request.viewer,
            page = request.page,
            pageSize = request.pageSize,
            filter = request.filter,
            sort = request.sort,
            ownership = OfferOwnership.OTHERS,
            now = request.now
        )
        MarketTab.MY_OFFERS -> MarketQuery(
            viewer = request.viewer,
            page = request.page,
            pageSize = request.pageSize,
            filter = OfferFilter.OWN,
            sort = request.sort,
            ownership = OfferOwnership.MINE,
            now = request.now
        )
    }

    val view = query(query)
    val totalPages = if (view.total == 0) 1 else (view.total + view.pageSize - 1) / view.pageSize
    return MarketScreenModel(
        tab = request.tab,
        entries = view.offers.map { offer ->
            val owned = offer.owner.playerId == request.viewer
            MarketScreenEntry(
                offer = offer,
                ownedByViewer = owned,
                canBuy = !owned && offer.purchasableBy(request.viewer, request.now),
                canWithdraw = owned
            )
        },
        total = view.total,
        page = view.page,
        pageSize = view.pageSize,
        totalPages = totalPages,
        hasPrevious = view.page > 1,
        hasNext = view.page < totalPages,
        filter = request.filter,
        sort = request.sort
    )
}
