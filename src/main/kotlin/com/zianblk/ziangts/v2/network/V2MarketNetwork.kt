package com.zianblk.ziangts.v2.network

import com.zianblk.ziangts.ZianGts
import com.zianblk.ziangts.v2.runtime.ZianGtsV2Runtime
import com.zianblk.ziangts.v2.domain.OfferId
import com.zianblk.ziangts.v2.domain.PaymentSpec
import com.zianblk.ziangts.economy.AvecoinsWalletProvider
import com.zianblk.ziangts.v2.application.TradeResult
import com.zianblk.ziangts.v2.application.ClaimResult
import com.zianblk.ziangts.v2.domain.ProceedsKey
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
                handlePageRequest(player, payload.requestId, payload.request)
            }
        }

        registrar.playToClient(
            MarketPageResponsePayload.TYPE,
            MarketPayloadCodecs.RESPONSE_PAYLOAD
        ) { payload, context ->
            context.enqueueWork {
                V2MarketClientState.accept(payload.requestId, payload.response)
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

        registrar.playToServer(
            PublishOptionsRequestPayload.TYPE,
            MarketPayloadCodecs.PUBLISH_OPTIONS_REQUEST_PAYLOAD
        ) { _, context ->
            val player = context.player()
            if (player !is ServerPlayer) return@playToServer
            context.enqueueWork {
                try {
                    val party = ZianGtsV2Runtime.partyEntries(player.uuid) ?: return@enqueueWork
                    val currencies = AvecoinsWalletProvider.supportedCurrencies()
                    val dto = party.map { PartyEntryDto(it.slot, it.pokemonId, it.species, it.level, it.shiny, it.alpha, it.legendary) }
                    PacketDistributor.sendToPlayer(player, PublishOptionsResponsePayload(PublishOptionsResponse(dto, currencies)))
                } catch (error: Exception) {
                    ZianGts.LOGGER.error("Failed to prepare V2 publish options for {}", player.scoreboardName, error)
                    PacketDistributor.sendToPlayer(player, PublishOfferResponsePayload(false, "No se pudieron cargar las opciones de publicación"))
                }
            }
        }

        registrar.playToClient(
            PublishOptionsResponsePayload.TYPE,
            MarketPayloadCodecs.PUBLISH_OPTIONS_RESPONSE_PAYLOAD
        ) { payload, context ->
            context.enqueueWork { V2MarketClientState.acceptPublishOptions(payload.response) }
        }

        registrar.playToServer(
            PublishOfferRequestPayload.TYPE,
            MarketPayloadCodecs.PUBLISH_OFFER_REQUEST_PAYLOAD
        ) { payload, context ->
            val player = context.player()
            if (player !is ServerPlayer) return@playToServer
            context.enqueueWork {
                try {
                    require(payload.amount > 0) { "price must be positive" }
                    val supported = AvecoinsWalletProvider.supportedCurrencies()
                    require(payload.currency in supported) { "unsupported currency" }
                    val engine = ZianGtsV2Runtime.engineOrNull()
                    if (engine == null) {
                        PacketDistributor.sendToPlayer(player, PublishOfferResponsePayload(false, "GTS no disponible"))
                        return@enqueueWork
                    }
                    val result = engine.publish(
                        player.uuid,
                        player.gameProfile.name,
                        payload.pokemonId,
                        PaymentSpec("avecoins_wallet", payload.currency, payload.amount)
                    )
                    val success = result is TradeResult.Success
                    val message = when (result) {
                        is TradeResult.Success -> "Pokémon publicado"
                        is TradeResult.Rejected -> result.reason
                        is TradeResult.Quarantined -> "Operación bloqueada para recuperación: ${result.operationId}"
                    }
                    PacketDistributor.sendToPlayer(player, PublishOfferResponsePayload(success, message))
                } catch (error: Exception) {
                    ZianGts.LOGGER.error("Unhandled V2 publish for player {}", player.scoreboardName, error)
                    PacketDistributor.sendToPlayer(player, PublishOfferResponsePayload(false, "No se pudo publicar el Pokémon"))
                }
            }
        }

        registrar.playToClient(
            PublishOfferResponsePayload.TYPE,
            MarketPayloadCodecs.PUBLISH_OFFER_RESPONSE_PAYLOAD
        ) { payload, context ->
            context.enqueueWork { V2MarketClientState.acceptPublishResult(payload) }
        }

        registrar.playToServer(
            ProceedsRequestPayload.TYPE,
            MarketPayloadCodecs.PROCEEDS_REQUEST_PAYLOAD
        ) { _, context ->
            val player = context.player()
            if (player !is ServerPlayer) return@playToServer
            context.enqueueWork {
                try {
                    val balances = ZianGtsV2Runtime.proceedsFor(player.uuid)
                    if (balances == null) {
                        PacketDistributor.sendToPlayer(player, ProceedsResponsePayload(emptyList()))
                        return@enqueueWork
                    }
                    val entries = balances.filterValues { it > 0 }.map { (key, amount) ->
                        ProceedsEntryDto(key.adapter, key.currency, amount)
                    }
                    PacketDistributor.sendToPlayer(player, ProceedsResponsePayload(entries))
                } catch (error: Exception) {
                    ZianGts.LOGGER.error("Failed to load V2 proceeds for {}", player.scoreboardName, error)
                    PacketDistributor.sendToPlayer(player, ProceedsResponsePayload(emptyList()))
                }
            }
        }

        registrar.playToClient(
            ProceedsResponsePayload.TYPE,
            MarketPayloadCodecs.PROCEEDS_RESPONSE_PAYLOAD
        ) { payload, context ->
            context.enqueueWork { V2MarketClientState.acceptProceeds(payload) }
        }

        registrar.playToServer(
            ClaimProceedsRequestPayload.TYPE,
            MarketPayloadCodecs.CLAIM_PROCEEDS_REQUEST_PAYLOAD
        ) { payload, context ->
            val player = context.player()
            if (player !is ServerPlayer) return@playToServer
            context.enqueueWork {
                try {
                    require(payload.adapter == "avecoins_wallet") { "unsupported proceeds adapter" }
                    require(payload.currency in AvecoinsWalletProvider.supportedCurrencies()) { "unsupported currency" }
                    val engine = ZianGtsV2Runtime.engineOrNull()
                    if (engine == null) {
                        PacketDistributor.sendToPlayer(player, ClaimProceedsResponsePayload(false, "GTS no disponible"))
                        return@enqueueWork
                    }
                    val result = engine.claim(player.uuid, ProceedsKey(payload.adapter, payload.currency))
                    val success = result is ClaimResult.Success
                    val message = when (result) {
                        is ClaimResult.Success -> "Se agregaron ${result.amount} ${currencyDisplayName(result.key.currency, result.amount)} a tu wallet exitosamente"
                        is ClaimResult.Rejected -> result.reason
                        is ClaimResult.Quarantined -> "Cobro bloqueado para recuperación: ${result.operationId}"
                    }
                    PacketDistributor.sendToPlayer(player, ClaimProceedsResponsePayload(success, message))
                } catch (error: Exception) {
                    ZianGts.LOGGER.error("Unhandled V2 proceeds claim for {}", player.scoreboardName, error)
                    PacketDistributor.sendToPlayer(player, ClaimProceedsResponsePayload(false, "No se pudieron cobrar las ganancias"))
                }
            }
        }

        registrar.playToClient(
            ClaimProceedsResponsePayload.TYPE,
            MarketPayloadCodecs.CLAIM_PROCEEDS_RESPONSE_PAYLOAD
        ) { payload, context ->
            context.enqueueWork { V2MarketClientState.acceptClaimResult(payload) }
        }

        registrar.playToServer(
            HistoryRequestPayload.TYPE,
            MarketPayloadCodecs.HISTORY_REQUEST_PAYLOAD
        ) { payload, context ->
            val player = context.player()
            if (player !is ServerPlayer) return@playToServer
            context.enqueueWork {
                try {
                    require(payload.page >= 1) { "invalid history page" }
                    require(payload.pageSize in 1..20) { "invalid history page size" }
                    val records = ZianGtsV2Runtime.historyFor(player.uuid, 500)
                    if (records == null) {
                        PacketDistributor.sendToPlayer(player, HistoryResponsePayload(emptyList(), 1, 1, false, false))
                        return@enqueueWork
                    }
                    val totalPages = maxOf(1, (records.size + payload.pageSize - 1) / payload.pageSize)
                    val page = payload.page.coerceAtMost(totalPages)
                    val start = (page - 1) * payload.pageSize
                    val entries = records.drop(start).take(payload.pageSize).map {
                        HistoryEntryDto(it.operationId, it.sellerId, it.buyerId, it.species, it.payment.amount, it.payment.currency, it.completedAt.toEpochMilli())
                    }
                    PacketDistributor.sendToPlayer(player, HistoryResponsePayload(entries, page, totalPages, page > 1, page < totalPages))
                } catch (error: Exception) {
                    ZianGts.LOGGER.error("Failed to serve V2 history for {}", player.scoreboardName, error)
                    PacketDistributor.sendToPlayer(player, HistoryResponsePayload(emptyList(), 1, 1, false, false))
                }
            }
        }

        registrar.playToClient(
            HistoryResponsePayload.TYPE,
            MarketPayloadCodecs.HISTORY_RESPONSE_PAYLOAD
        ) { payload, context ->
            context.enqueueWork { V2MarketClientState.acceptHistory(payload) }
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

    private fun currencyDisplayName(currency: String, amount: Long): String {
        val singular = amount == 1L
        return when (currency.substringAfter(':')) {
            "coppercoin" -> if (singular) "Moneda de Cobre" else "Monedas de Cobre"
            "ironcoin" -> if (singular) "Moneda de Hierro" else "Monedas de Hierro"
            "goldcoin" -> if (singular) "Moneda de Oro" else "Monedas de Oro"
            "diamondcoin" -> if (singular) "Moneda de Diamante" else "Monedas de Diamante"
            "netheritecoin" -> if (singular) "Moneda de Netherita" else "Monedas de Netherita"
            "goldticket" -> if (singular) "Ticket de Oro" else "Tickets de Oro"
            "diamondticket" -> if (singular) "Ticket de Diamante" else "Tickets de Diamante"
            "netheriteticket" -> if (singular) "Ticket de Netherita" else "Tickets de Netherita"
            else -> currency.substringAfter(':').replace('_', ' ')
        }
    }

    private fun handlePageRequest(player: ServerPlayer, requestId: Long, request: MarketPageRequest) {
        if (!ZianGtsV2Runtime.isReady()) {
            ZianGts.LOGGER.debug("Ignoring V2 market request while runtime is unavailable")
            return
        }
        try {
            val screen = ZianGtsV2Runtime.marketScreen(
                request.toScreenRequest(player.uuid, Instant.now())
            ) ?: return
            PacketDistributor.sendToPlayer(player, MarketPageResponsePayload(requestId, screen.toPageResponse()))
        } catch (error: IllegalArgumentException) {
            ZianGts.LOGGER.warn("Rejected invalid V2 market request from {}", player.scoreboardName)
        } catch (error: Exception) {
            ZianGts.LOGGER.error("Failed to serve V2 market page for {}", player.scoreboardName, error)
        }
    }
}
