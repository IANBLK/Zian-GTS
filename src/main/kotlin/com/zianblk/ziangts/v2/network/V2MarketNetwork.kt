package com.zianblk.ziangts.v2.network

import com.zianblk.ziangts.ZianGts
import com.zianblk.ziangts.v2.runtime.ZianGtsV2Runtime
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import java.time.Instant

/**
 * NeoForge transport wiring for V2 market reads.
 *
 * Mutating actions (buy/withdraw/publish/claim) intentionally stay out of this first
 * networking slice until the read path is verified end-to-end.
 */
object V2MarketNetwork {
    fun register(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("v2-market")
        registrar.playToServer(
            MarketPageRequestPayload.TYPE,
            MarketPayloadCodecs.REQUEST_PAYLOAD
        ) { payload, context ->
            val player = context.player()
            if (player !is ServerPlayer) return@playToServer
            context.enqueueWork {
                handlePageRequest(player, payload.request)
            }
        }

        registrar.playToClient(
            MarketPageResponsePayload.TYPE,
            MarketPayloadCodecs.RESPONSE_PAYLOAD
        )
    }

    private fun handlePageRequest(player: ServerPlayer, request: MarketPageRequest) {
        if (!ZianGtsV2Runtime.isReady()) {
            ZianGts.LOGGER.debug("Ignoring V2 market request while runtime is unavailable")
            return
        }
        try {
            val screen = ZianGtsV2Runtime.marketScreen(
                request.toScreenRequest(player.uuid, Instant.now())
            ) ?: return
            PacketDistributor.sendToPlayer(player, MarketPageResponsePayload(screen.toPageResponse()))
        } catch (error: IllegalArgumentException) {
            ZianGts.LOGGER.warn("Rejected invalid V2 market request from {}", player.scoreboardName)
        } catch (error: Exception) {
            ZianGts.LOGGER.error("Failed to serve V2 market page for {}", player.scoreboardName, error)
        }
    }
}
