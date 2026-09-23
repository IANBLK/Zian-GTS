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


/** Client requests a server-authoritative mutation for one offer. */
enum class MarketAction { BUY, WITHDRAW }

data class MarketActionRequestPayload(
    val offerId: java.util.UUID,
    val action: MarketAction
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<MarketActionRequestPayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_market_action_request")
        )
    }
}

data class MarketActionResponsePayload(
    val offerId: java.util.UUID,
    val success: Boolean,
    val message: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<MarketActionResponsePayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_market_action_response")
        )
    }
}


data object PublishOptionsRequestPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    val TYPE = CustomPacketPayload.Type<PublishOptionsRequestPayload>(
        ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_publish_options_request")
    )
}

data class PublishOptionsResponsePayload(val response: PublishOptionsResponse) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<PublishOptionsResponsePayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_publish_options_response")
        )
    }
}

data class PublishOfferRequestPayload(
    val pokemonId: java.util.UUID,
    val amount: Long,
    val currency: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<PublishOfferRequestPayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_publish_offer_request")
        )
    }
}

data class PublishOfferResponsePayload(
    val success: Boolean,
    val message: String
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<PublishOfferResponsePayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_publish_offer_response")
        )
    }
}


data object ProceedsRequestPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    val TYPE = CustomPacketPayload.Type<ProceedsRequestPayload>(
        ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_proceeds_request")
    )
}

data class ProceedsEntryDto(val adapter: String, val currency: String, val amount: Long)

data class ProceedsResponsePayload(val entries: List<ProceedsEntryDto>) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<ProceedsResponsePayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_proceeds_response")
        )
    }
}

data class ClaimProceedsRequestPayload(val adapter: String, val currency: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<ClaimProceedsRequestPayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_claim_proceeds_request")
        )
    }
}

data class ClaimProceedsResponsePayload(val success: Boolean, val message: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<ClaimProceedsResponsePayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_claim_proceeds_response")
        )
    }
}


data class HistoryRequestPayload(val page: Int, val pageSize: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HistoryRequestPayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_history_request")
        )
    }
}

data class HistoryEntryDto(
    val operationId: java.util.UUID,
    val sellerId: java.util.UUID,
    val buyerId: java.util.UUID,
    val species: String,
    val amount: Long,
    val currency: String,
    val completedAtEpochMilli: Long
)

data class HistoryResponsePayload(
    val entries: List<HistoryEntryDto>,
    val page: Int,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE
    companion object {
        val TYPE = CustomPacketPayload.Type<HistoryResponsePayload>(
            ResourceLocation.fromNamespaceAndPath(ZianGts.MOD_ID, "v2_history_response")
        )
    }
}
