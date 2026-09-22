package com.zianblk.ziangts.v2.network

import com.zianblk.ziangts.ZianGts
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

/**
 * Wire payload wrappers for the V2 market protocol.
 *
 * The request intentionally carries no player UUID. The server handler must derive
 * the viewer from the authenticated packet context.
 */
data class MarketPageRequestPayload(val request: MarketPageRequest) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MarketPageRequestPayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_market_page_request")
        )
    }
}

data class MarketPageResponsePayload(val response: MarketPageResponse) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MarketPageResponsePayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_market_page_response")
        )
    }
}


/**
 * Server-authoritative instruction to open the V2 market screen.
 * It carries no state: the client must request the current page after opening.
 */
data object OpenMarketScreenPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    val TYPE = CustomPacketPayload.Type<OpenMarketScreenPayload>(
        ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_open_market_screen")
    )
}
