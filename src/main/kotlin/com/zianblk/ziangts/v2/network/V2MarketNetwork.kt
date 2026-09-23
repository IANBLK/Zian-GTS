package com.zianblk.ziangts.v2.network

import com.zianblk.ziangts.ZianGts
import com.zianblk.ziangts.v2.runtime.ZianGtsV2Runtime
import com.zianblk.ziangts.v2.domain.OfferId
import com.zianblk.ziangts.v2.application.TradeResult
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
        ) { payload, context ->
            context.enqueueWork {
                V2MarketClientState.accept(payload.response)
            }
        }

        registrar.playToServer(
            MarketActionRequestPayload.TYPE,
            MarketPayloadCodecs.ACTION_REQUEST_PAYLOAD
        ) { payload, context ->
            val player = context.player()
            if (player !is ServerPlayer) return@playToServer
            context.enqueueWork {
                val engine = ZianGtsV2Runtime.engineOrNull()
                if (engine == null) {
                    PacketDistributor.sendToPlayer(player, MarketActionResponsePayload(payload.offerId, false, "GTS no disponible"))
                    return@enqueueWork
                }
                try {
                    val result = when (payload.action) {
                        MarketAction.BUY -> engine.purchase(player.uuid, OfferId(payload.offerId))
                        MarketAction.WITHDRAW -> engine.withdraw(player.uuid, OfferId(payload.offerId))
                    }
                    val success = result is TradeResult.Success
                    val message = when (result) {
                        is TradeResult.Success -> if (payload.action == MarketAction.BUY) "Compra completada" else "Pokémon retirado"
                        is TradeResult.Rejected -> result.reason
                        is TradeResult.Quarantined -> "Operación bloqueada para recuperación: ${result.operationId}"
                    }
                    PacketDistributor.sendToPlayer(player, MarketActionResponsePayload(payload.offerId, success, message))
                } catch (error: Exception) {
                    ZianGts.LOGGER.error(
                        "Unhandled V2 market action {} for player {} and offer {}",
                        payload.action, player.scoreboardName, payload.offerId, error
                    )
                    PacketDistributor.sendToPlayer(
                        player,
                        MarketActionResponsePayload(payload.offerId, false, "No se pudo completar la operación")
                    )
                }
            }
        }

        registrar.playToClient(
            MarketActionResponsePayload.TYPE,
            MarketPayloadCodecs.ACTION_RESPONSE_PAYLOAD
        ) { payload, context ->
            context.enqueueWork {
                V2MarketClientState.acceptAction(payload)
            }
        }

        registrar.playToClient(
            OpenMarketScreenPayload.TYPE,
            MarketPayloadCodecs.OPEN_SCREEN_PAYLOAD
        ) { _, context ->
            context.enqueueWork {
                V2MarketClientHandlers.openMarketScreen()
            }
        }
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
