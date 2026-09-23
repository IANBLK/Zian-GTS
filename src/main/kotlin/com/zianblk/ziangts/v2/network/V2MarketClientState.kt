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
    private var action: MarketActionResponsePayload? = null

    @Volatile
    private var actionRevision: Long = 0

    fun accept(response: MarketPageResponse) {
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

    fun revision(): Long = revision

    fun clear() {
        current = null
        revision += 1
    }
}
