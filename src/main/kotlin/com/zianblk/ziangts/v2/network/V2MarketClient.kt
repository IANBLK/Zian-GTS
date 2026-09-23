package com.zianblk.ziangts.v2.network

import net.neoforged.neoforge.network.PacketDistributor

/**
 * Client entry point for requesting authoritative V2 market projections.
 *
 * The request deliberately carries no player UUID. Identity is derived from the
 * authenticated server network context.
 */
object V2MarketClient {
    fun requestHistory(page: Int = 1, pageSize: Int = 8) = PacketDistributor.sendToServer(HistoryRequestPayload(page, pageSize))

    fun requestProceeds() = PacketDistributor.sendToServer(ProceedsRequestPayload)
    fun claimProceeds(adapter: String, currency: String) = PacketDistributor.sendToServer(ClaimProceedsRequestPayload(adapter, currency))

    fun requestPublishOptions() = PacketDistributor.sendToServer(PublishOptionsRequestPayload)

    fun publish(pokemonId: java.util.UUID, amount: Long, currency: String) =
        PacketDistributor.sendToServer(PublishOfferRequestPayload(pokemonId, amount, currency))

    fun requestAction(offerId: java.util.UUID, action: MarketAction) {
        PacketDistributor.sendToServer(MarketActionRequestPayload(offerId, action))
    }

    fun requestPage(request: MarketPageRequest) {
        PacketDistributor.sendToServer(MarketPageRequestPayload(request))
    }

    fun requestMarket(
        page: Int = 1,
        pageSize: Int = 6,
        filter: com.zianblk.ziangts.v2.domain.OfferFilter = com.zianblk.ziangts.v2.domain.OfferFilter.ALL,
        sort: com.zianblk.ziangts.v2.domain.OfferSort = com.zianblk.ziangts.v2.domain.OfferSort.NEWEST
    ) {
        requestPage(
            MarketPageRequest(
                tab = com.zianblk.ziangts.v2.domain.MarketTab.MARKET,
                page = page,
                pageSize = pageSize,
                filter = filter,
                sort = sort
            )
        )
    }

    fun requestMyOffers(
        page: Int = 1,
        pageSize: Int = 6,
        sort: com.zianblk.ziangts.v2.domain.OfferSort = com.zianblk.ziangts.v2.domain.OfferSort.NEWEST
    ) {
        requestPage(
            MarketPageRequest(
                tab = com.zianblk.ziangts.v2.domain.MarketTab.MY_OFFERS,
                page = page,
                pageSize = pageSize,
                filter = com.zianblk.ziangts.v2.domain.OfferFilter.OWN,
                sort = sort
            )
        )
    }
}
