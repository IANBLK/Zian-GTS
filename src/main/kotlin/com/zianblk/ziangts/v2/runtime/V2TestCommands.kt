package com.zianblk.ziangts.v2.runtime

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.UuidArgument
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.zianblk.ziangts.v2.application.ClaimResult
import com.zianblk.ziangts.v2.application.TradeResult
import com.zianblk.ziangts.v2.domain.OfferId
import com.zianblk.ziangts.v2.domain.PaymentSpec
import com.zianblk.ziangts.v2.domain.ProceedsKey
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
            .then(Commands.literal("publish")
                .then(Commands.argument("pokemon", UuidArgument.uuid())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .then(Commands.argument("currency", StringArgumentType.word())
                            .executes { ctx ->
                                val player = ctx.source.playerOrException
                                val engine = engine(ctx.source) ?: return@executes 0
                                val result = engine.publish(
                                    player.uuid,
                                    player.gameProfile.name,
                                    UuidArgument.getUuid(ctx, "pokemon"),
                                    PaymentSpec(ADAPTER, StringArgumentType.getString(ctx, "currency"),
                                        IntegerArgumentType.getInteger(ctx, "amount").toLong())
                                )
                                report(ctx.source, result)
                            }))))
            .then(Commands.literal("buy")
                .then(Commands.argument("offer", UuidArgument.uuid()).executes { ctx ->
                    val player = ctx.source.playerOrException
                    val engine = engine(ctx.source) ?: return@executes 0
                    report(ctx.source, engine.purchase(player.uuid, OfferId(UuidArgument.getUuid(ctx, "offer"))))
                }))
            .then(Commands.literal("withdraw")
                .then(Commands.argument("offer", UuidArgument.uuid()).executes { ctx ->
                    val player = ctx.source.playerOrException
                    val engine = engine(ctx.source) ?: return@executes 0
                    report(ctx.source, engine.withdraw(player.uuid, OfferId(UuidArgument.getUuid(ctx, "offer"))))
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
