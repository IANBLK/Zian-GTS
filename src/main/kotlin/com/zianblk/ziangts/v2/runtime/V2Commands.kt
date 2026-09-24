package com.zianblk.ziangts.v2.runtime

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor
import com.zianblk.ziangts.v2.network.OpenMarketScreenPayload

/**
 * Production-facing V2 commands.
 *
 * Player access is intentionally limited to opening the native market UI.
 * Recovery/status remain operator-only because recovery resolution is a destructive
 * administrative action backed by the durable transaction journal.
 */
object V2Commands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("gtsv2")
            .executes { ctx -> open(ctx.source) }
            .then(Commands.literal("open").executes { ctx -> open(ctx.source) })
            .then(Commands.literal("status")
                .requires { it.hasPermission(2) }
                .executes { ctx ->
                    val ready = ZianGtsV2Runtime.isReady()
                    val detail = ZianGtsV2Runtime.blockedReason() ?: "ready"
                    ctx.source.sendSuccess(
                        { Component.literal("Zian GTS V2: ready=$ready, status=$detail") },
                        false
                    )
                    1
                })
            .then(Commands.literal("recovery")
                .requires { it.hasPermission(2) }
                .executes { ctx ->
                    val pending = ZianGtsV2Runtime.unresolvedTransactions()
                    if (pending.isEmpty()) {
                        ctx.source.sendSuccess(
                            { Component.literal("Zian GTS V2: no unresolved transactions") },
                            false
                        )
                    } else {
                        ctx.source.sendFailure(Component.literal(
                            "Zian GTS V2: ${pending.size} unresolved transaction(s); trading remains blocked"
                        ))
                        pending.take(10).forEach { incident ->
                            ctx.source.sendFailure(Component.literal(
                                "id=${incident.ticket.operationId} op=${incident.ticket.operation} " +
                                    "subject=${incident.subjectId} stage=${incident.stage} " +
                                    "quarantined=${incident.quarantined} reason=${incident.reason ?: "-"}"
                            ))
                        }
                    }
                    1
                }
                .then(Commands.literal("resolve")
                    .then(Commands.argument("operation", StringArgumentType.word())
                        .executes { ctx ->
                            val idText = StringArgumentType.getString(ctx, "operation")
                            val id = parseUuid(ctx.source, idText) ?: return@executes 0
                            ctx.source.sendFailure(Component.literal(
                                "Recovery is destructive. Verify AVECOINS, Pokemon storage and GTS state first. " +
                                    "To confirm: /gtsv2 recovery resolve $id confirm"
                            ))
                            1
                        }
                        .then(Commands.literal("confirm").executes { ctx ->
                            val idText = StringArgumentType.getString(ctx, "operation")
                            val id = parseUuid(ctx.source, idText) ?: return@executes 0
                            val actor = ctx.source.entity?.uuid?.toString() ?: "console"
                            val resolved = try {
                                ZianGtsV2Runtime.resolveBlockedTransaction(
                                    id,
                                    "manual reconciliation confirmed by $actor"
                                )
                            } catch (error: Exception) {
                                ctx.source.sendFailure(Component.literal(
                                    "Recovery resolution failed: ${error.message}"
                                ))
                                return@executes 0
                            }
                            if (!resolved) {
                                ctx.source.sendFailure(Component.literal("Unknown unresolved operation: $id"))
                                0
                            } else {
                                ctx.source.sendSuccess({ Component.literal(
                                    "Resolved $id after manual reconciliation. " +
                                        "Restart the server before V2 trading resumes."
                                ) }, true)
                                1
                            }
                        }))))
        dispatcher.register(root)
    }

    private fun open(source: CommandSourceStack): Int {
        val player = try {
            source.playerOrException
        } catch (_: Exception) {
            source.sendFailure(Component.literal("This command must be used by a player."))
            return 0
        }
        if (!ZianGtsV2Runtime.isReady()) {
            source.sendFailure(Component.literal(
                "Zian GTS V2 market unavailable: ${ZianGtsV2Runtime.blockedReason()}"
            ))
            return 0
        }
        PacketDistributor.sendToPlayer(player, OpenMarketScreenPayload)
        return 1
    }

    private fun parseUuid(source: CommandSourceStack, value: String): java.util.UUID? =
        try {
            java.util.UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            source.sendFailure(Component.literal("Invalid operation UUID: $value"))
            null
        }
}
