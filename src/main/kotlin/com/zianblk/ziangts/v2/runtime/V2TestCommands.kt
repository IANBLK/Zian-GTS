package com.zianblk.ziangts.v2.runtime

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.zianblk.ziangts.v2.application.ClaimResult
import com.zianblk.ziangts.v2.application.TradeResult
import com.zianblk.ziangts.v2.domain.OfferId
import com.zianblk.ziangts.v2.domain.PaymentSpec
import com.zianblk.ziangts.v2.domain.ProceedsKey
import com.zianblk.ziangts.v2.domain.MarketQuery
import com.zianblk.ziangts.v2.domain.OfferFilter
import com.zianblk.ziangts.v2.domain.OfferSort
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/** Temporary operator-facing commands for real Youer validation before V2 GUI/network wiring. */
object V2TestCommands {
    private const val ADAPTER = "avecoins_wallet"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("gtsv2").requires { it.hasPermission(2) }
            .then(Commands.literal("status").executes { ctx ->
                val ready = ZianGtsV2Runtime.isReady()
                val detail = ZianGtsV2Runtime.blockedReason() ?: "ready"
                ctx.source.sendSuccess({ Component.literal("Zian GTS V2: ready=$ready, status=$detail") }, false)
                1
            })
            .then(Commands.literal("recovery")
                .executes { ctx ->
                    val pending = ZianGtsV2Runtime.unresolvedTransactions()
                    if (pending.isEmpty()) {
                        ctx.source.sendSuccess({ Component.literal("Zian GTS V2: no unresolved transactions") }, false)
                    } else {
                        ctx.source.sendFailure(Component.literal("Zian GTS V2: ${pending.size} unresolved transaction(s); trading remains blocked"))
                        pending.take(10).forEach { incident ->
                            ctx.source.sendFailure(Component.literal(
                                "id=${incident.ticket.operationId} op=${incident.ticket.operation} subject=${incident.subjectId} stage=${incident.stage} quarantined=${incident.quarantined} reason=${incident.reason ?: "-"}"
                            ))
                        }
                    }
                    1
                })
                .then(Commands.literal("resolve")
                    .then(Commands.argument("operation", StringArgumentType.word())
                        .executes { ctx ->
                            val idText = StringArgumentType.getString(ctx, "operation")
                            val id = try { java.util.UUID.fromString(idText) } catch (_: IllegalArgumentException) {
                                ctx.source.sendFailure(Component.literal("Invalid operation UUID: $idText"))
                                return@executes 0
                            }
                            ctx.source.sendFailure(Component.literal(
                                "Recovery is destructive. Verify AVECOINS, Pokemon storage and GTS state first. " +
                                    "To confirm: /gtsv2 recovery resolve $id confirm"
                            ))
                            1
                        }
                        .then(Commands.literal("confirm").executes { ctx ->
                            val idText = StringArgumentType.getString(ctx, "operation")
                            val id = try { java.util.UUID.fromString(idText) } catch (_: IllegalArgumentException) {
                                ctx.source.sendFailure(Component.literal("Invalid operation UUID: $idText"))
                                return@executes 0
                            }
                            val actor = ctx.source.entity?.uuid?.toString() ?: "console"
                            val note = "manual reconciliation confirmed by $actor"
                            val resolved = try {
                                ZianGtsV2Runtime.resolveBlockedTransaction(id, note)
                            } catch (error: Exception) {
                                ctx.source.sendFailure(Component.literal("Recovery resolution failed: ${error.message}"))
                                return@executes 0
                            }
                            if (!resolved) {
                                ctx.source.sendFailure(Component.literal("Unknown unresolved operation: $id"))
                                0
                            } else {
                                ctx.source.sendSuccess({ Component.literal(
                                    "Resolved $id after manual reconciliation. Restart the server before V2 trading resumes."
                                ) }, true)
                                1
                            }
                        })))
            .then(Commands.literal("market").executes { ctx ->
                val player = ctx.source.playerOrException
                val view = ZianGtsV2Runtime.marketView(
                    MarketQuery(player.uuid, page = 1, pageSize = 10, filter = OfferFilter.ALL, sort = OfferSort.NEWEST)
                )
                if (view == null) {
                    ctx.source.sendFailure(Component.literal("Zian GTS V2 market unavailable: ${ZianGtsV2Runtime.blockedReason()}"))
                    0
                } else {
                    ctx.source.sendSuccess({ Component.literal("Zian GTS V2 market: ${view.total} active offer(s)") }, false)
                    view.offers.forEach { offer ->
                        ctx.source.sendSuccess({ Component.literal(
                            "id=${offer.id.value} pokemon=${offer.pokemon.species} lvl=${offer.pokemon.level} price=${offer.payment.amount} ${offer.payment.currency} seller=${offer.owner.displayName}"
                        ) }, false)
                    }
                    1
                }
            })
            .then(Commands.literal("mine").executes { ctx ->
                val player = ctx.source.playerOrException
                val view = ZianGtsV2Runtime.marketView(
                    MarketQuery(player.uuid, page = 1, pageSize = 10, filter = OfferFilter.OWN, sort = OfferSort.NEWEST)
                )
                if (view == null) {
                    ctx.source.sendFailure(Component.literal("Zian GTS V2 listings unavailable: ${ZianGtsV2Runtime.blockedReason()}"))
                    0
                } else {
                    ctx.source.sendSuccess({ Component.literal("My Zian GTS V2 offers: ${view.total}") }, false)
                    view.offers.forEach { offer ->
                        ctx.source.sendSuccess({ Component.literal(
                            "id=${offer.id.value} pokemon=${offer.pokemon.species} state=${offer.stateAt(java.time.Instant.now())} price=${offer.payment.amount} ${offer.payment.currency}"
                        ) }, false)
                    }
                    1
                }
            })
            .then(Commands.literal("history").executes { ctx ->
                val player = ctx.source.playerOrException
                val records = ZianGtsV2Runtime.historyFor(player.uuid, 20)
                if (records == null) {
                    ctx.source.sendFailure(Component.literal("Zian GTS V2 history unavailable: ${ZianGtsV2Runtime.blockedReason()}"))
                    0
                } else {
                    ctx.source.sendSuccess({ Component.literal("Zian GTS V2 history: ${records.size} recent trade(s)") }, false)
                    records.forEach { record ->
                        val role = if (record.sellerId == player.uuid) "sold" else "bought"
                        ctx.source.sendSuccess({ Component.literal(
                            "$role ${record.species} for ${record.payment.amount} ${record.payment.currency} at ${record.completedAt}"
                        ) }, false)
                    }
                    1
                }
            })
            .then(Commands.literal("proceeds").executes { ctx ->
                val player = ctx.source.playerOrException
                val balances = ZianGtsV2Runtime.proceedsFor(player.uuid)
                if (balances == null) {
                    ctx.source.sendFailure(Component.literal("Zian GTS V2 proceeds unavailable: ${ZianGtsV2Runtime.blockedReason()}"))
                    0
                } else {
                    if (balances.isEmpty()) {
                        ctx.source.sendSuccess({ Component.literal("Zian GTS V2: no pending proceeds") }, false)
                    } else {
                        balances.forEach { (key, amount) ->
                            ctx.source.sendSuccess({ Component.literal("pending=$amount ${key.currency}") }, false)
                        }
                    }
                    1
                }
            })
            .then(Commands.literal("publish")
                .then(Commands.argument("pokemon", StringArgumentType.word())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .then(Commands.argument("currency", StringArgumentType.word())
                            .executes { ctx ->
                                val player = ctx.source.playerOrException
                                val engine = engine(ctx.source) ?: return@executes 0
                                val result = engine.publish(
                                    player.uuid,
                                    player.gameProfile.name,
                                    java.util.UUID.fromString(StringArgumentType.getString(ctx, "pokemon")),
                                    PaymentSpec(ADAPTER, StringArgumentType.getString(ctx, "currency"),
                                        IntegerArgumentType.getInteger(ctx, "amount").toLong())
                                )
                                report(ctx.source, result)
                            }))))
            .then(Commands.literal("buy")
                .then(Commands.argument("offer", StringArgumentType.word())
                    .suggests { ctx, builder ->
                        val player = ctx.source.player
                        val view = player?.let {
                            ZianGtsV2Runtime.marketView(
                                MarketQuery(it.uuid, page = 1, pageSize = 50, filter = OfferFilter.ALL, sort = OfferSort.NEWEST)
                            )
                        }
                        view?.offers?.forEach { builder.suggest(it.id.value.toString()) }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                    val player = ctx.source.playerOrException
                    val engine = engine(ctx.source) ?: return@executes 0
                    report(ctx.source, engine.purchase(player.uuid, OfferId(java.util.UUID.fromString(StringArgumentType.getString(ctx, "offer")))))
                }))
            .then(Commands.literal("withdraw")
                .then(Commands.argument("offer", StringArgumentType.word())
                    .suggests { ctx, builder ->
                        val player = ctx.source.player
                        val view = player?.let {
                            ZianGtsV2Runtime.marketView(
                                MarketQuery(it.uuid, page = 1, pageSize = 50, filter = OfferFilter.OWN, sort = OfferSort.NEWEST)
                            )
                        }
                        view?.offers?.forEach { builder.suggest(it.id.value.toString()) }
                        builder.buildFuture()
                    }
                    .executes { ctx ->
                    val player = ctx.source.playerOrException
                    val engine = engine(ctx.source) ?: return@executes 0
                    report(ctx.source, engine.withdraw(player.uuid, OfferId(java.util.UUID.fromString(StringArgumentType.getString(ctx, "offer")))))
                }))
            .then(Commands.literal("claim")
                .then(Commands.argument("currency", StringArgumentType.word()).executes { ctx ->
                    val player = ctx.source.playerOrException
                    val engine = engine(ctx.source) ?: return@executes 0
                    val result = engine.claim(player.uuid,
                        ProceedsKey(ADAPTER, StringArgumentType.getString(ctx, "currency")))
                    val text = when (result) {
                        is ClaimResult.Success -> "claim ok: ${result.amount} ${result.key.currency}"
                        is ClaimResult.Rejected -> "claim rejected: ${result.reason}"
                        is ClaimResult.Quarantined -> "claim quarantined: ${result.operationId} (${result.reason})"
                    }
                    ctx.source.sendSuccess({ Component.literal(text) }, false)
                    if (result is ClaimResult.Success) 1 else 0
                }))

        dispatcher.register(root)
    }

    private fun engine(source: CommandSourceStack) =
        ZianGtsV2Runtime.engineOrNull().also {
            if (it == null) source.sendFailure(Component.literal(
                "Zian GTS V2 unavailable: ${ZianGtsV2Runtime.blockedReason()}"))
        }

    private fun report(source: CommandSourceStack, result: TradeResult): Int {
        val text = when (result) {
            is TradeResult.Success -> "ok: offer=${result.offer.id.value}"
            is TradeResult.Rejected -> "rejected: ${result.reason}"
            is TradeResult.Quarantined -> "quarantined: ${result.operationId} (${result.reason})"
        }
        if (result is TradeResult.Success) source.sendSuccess({ Component.literal(text) }, false)
        else source.sendFailure(Component.literal(text))
        return if (result is TradeResult.Success) 1 else 0
    }
}
