package com.zianblk.ziangts.v2.network

/**
 * Client-side snapshot of the last market page delivered by the authoritative server.
 *
 * The GUI will observe this state. It never mutates offers or decides permissions.
 */
object V2MarketClientState {
    @Volatile
    private var current: MarketPageResponse? = null

    @Volatile
    private var revision: Long = 0

    @Volatile
    private var latestMarketRequestId: Long = 0

    @Volatile
    private var action: MarketActionResponsePayload? = null

    @Volatile
    private var actionRevision: Long = 0

    @Volatile private var publishOptions: PublishOptionsResponse? = null
    @Volatile private var publishOptionsRevision: Long = 0
    @Volatile private var publishResult: PublishOfferResponsePayload? = null
    @Volatile private var publishResultRevision: Long = 0
    @Volatile private var proceeds: ProceedsResponsePayload? = null
    @Volatile private var proceedsRevision: Long = 0
    @Volatile private var claimResult: ClaimProceedsResponsePayload? = null
    @Volatile private var claimRevision: Long = 0
    @Volatile private var history: HistoryResponsePayload? = null
    @Volatile private var historyRevision: Long = 0

    fun markMarketRequest(requestId: Long) {
        if (requestId > latestMarketRequestId) latestMarketRequestId = requestId
    }

    fun accept(requestId: Long, response: MarketPageResponse) {
        if (requestId < latestMarketRequestId) return
        require(response.protocolVersion == MARKET_PROTOCOL_VERSION) {
            "unsupported market protocol version"
        }
        current = response
        revision += 1
    }

    fun snapshot(): MarketPageResponse? = current

    fun acceptAction(response: MarketActionResponsePayload) {
        action = response
        actionRevision += 1
    }

    fun actionSnapshot(): MarketActionResponsePayload? = action
    fun actionRevision(): Long = actionRevision

    fun acceptPublishOptions(response: PublishOptionsResponse) {
        publishOptions = response
        publishOptionsRevision += 1
    }
    fun publishOptionsSnapshot(): PublishOptionsResponse? = publishOptions
    fun publishOptionsRevision(): Long = publishOptionsRevision

    fun acceptPublishResult(response: PublishOfferResponsePayload) {
        publishResult = response
        publishResultRevision += 1
    }
    fun publishResultSnapshot(): PublishOfferResponsePayload? = publishResult
    fun publishResultRevision(): Long = publishResultRevision

    fun acceptProceeds(response: ProceedsResponsePayload) { proceeds = response; proceedsRevision += 1 }
    fun proceedsSnapshot(): ProceedsResponsePayload? = proceeds
    fun proceedsRevision(): Long = proceedsRevision

    fun acceptClaimResult(response: ClaimProceedsResponsePayload) { claimResult = response; claimRevision += 1 }
    fun claimResultSnapshot(): ClaimProceedsResponsePayload? = claimResult
    fun claimRevision(): Long = claimRevision

    fun acceptHistory(response: HistoryResponsePayload) { history = response; historyRevision += 1 }
    fun historySnapshot(): HistoryResponsePayload? = history
    fun historyRevision(): Long = historyRevision

    fun revision(): Long = revision

    fun clear() {
        current = null
        revision += 1
    }
}
