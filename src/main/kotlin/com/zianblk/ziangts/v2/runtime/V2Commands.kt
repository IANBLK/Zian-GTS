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
 * Recovery/status use server-side administrative permissions; without LuckPerms
 * the default remains operator level 2.
 */
object V2Commands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("ZianGTS").requires { GtsPermissions.allows(it,"use") }
            .executes { ctx -> open(ctx.source) }
            .then(Commands.literal("open").executes { ctx -> open(ctx.source) })
            .then(Commands.literal("status")
                .requires { GtsPermissions.allows(it, "admin.recovery", defaultAdmin = true) }
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
                .requires { GtsPermissions.allows(it, "admin.recovery", defaultAdmin = true) }
                .executes { ctx ->
                    val pending = ZianGtsV2Runtime.unresolvedTransactions()
                    if (pending.isEmpty()) {
                        ctx.source.sendSuccess(
                            { Component.literal("Zian GTS: no hay operaciones pendientes de revisión.") },
                            false
                        )
                    } else {
                        ctx.source.sendFailure(Component.literal(
                            "Zian GTS: ${pending.size} operaciones requieren revisión; el mercado permanece bloqueado."
                        ))
                        pending.take(10).forEach { incident ->
                            ctx.source.sendFailure(Component.literal(
                                "${ZianGtsV2Runtime.reference(incident.ticket.operationId)} · jugador=${ZianGtsV2Runtime.referenceOwner(incident.ticket.operationId)} · operación=${incident.ticket.operation} " +
                                    "fase=${incident.stage} " +
                                    "enRevisión=${incident.quarantined} motivo=${incident.reason ?: "-"}"
                            ))
                        }
                    }
                    1
                }
                .then(Commands.literal("resolve")
                    .requires { GtsPermissions.allows(it, "admin.recovery.resolve", defaultAdmin = true) }
                    .then(Commands.argument("operation", StringArgumentType.word()).suggests { _,b -> net.minecraft.commands.SharedSuggestionProvider.suggest(ZianGtsV2Runtime.unresolvedTransactions().map{ZianGtsV2Runtime.reference(it.ticket.operationId)},b) }
                        .executes { ctx ->
                            val idText = StringArgumentType.getString(ctx, "operation")
                            val id = parseReference(ctx.source, idText) ?: return@executes 0
                            ctx.source.sendFailure(Component.literal(
                                "Comprueba el cobro, el almacenamiento Pokémon y el GTS. No se compensa nada automáticamente. " +
                                    "Para confirmar: /ZianGTS recovery resolve $idText confirm"
                            ))
                            1
                        }
                        .then(Commands.literal("confirm").executes { ctx ->
                            val idText = StringArgumentType.getString(ctx, "operation")
                            val id = parseReference(ctx.source, idText) ?: return@executes 0
                            val actor = ctx.source.entity?.uuid?.toString() ?: "console"
                            val resolved = try {
                                ZianGtsV2Runtime.resolveBlockedTransaction(
                                    id,
                                    "manual reconciliation confirmed by $actor"
                                )
                            } catch (error: Exception) {
                                ctx.source.sendFailure(Component.literal(
                                    "No se pudo registrar la revisión: ${error.message}"
                                ))
                                return@executes 0
                            }
                            if (!resolved) {
                                ctx.source.sendFailure(Component.literal("No existe una operación pendiente con esa referencia."))
                                0
                            } else {
                                ctx.source.sendSuccess({ Component.literal(
                                    "Revisión de $idText registrada tras la conciliación manual. " +
                                        "Reinicia el servidor antes de reabrir el mercado."
                                ) }, true)
                                1
                            }
                        }))))
        val registered=dispatcher.register(root)
        if(dispatcher.root.getChild("gts")==null)dispatcher.register(Commands.literal("gts").requires { GtsPermissions.allows(it,"use") }.executes { open(it.source) }.redirect(registered))
    }

    private fun parseReference(source:CommandSourceStack,value:String):java.util.UUID?=try{ZianGtsV2Runtime.resolveReference(value)}catch(error:Exception){source.sendFailure(Component.literal("Referencia inexistente. Usa el autocompletado o un UUID válido."));null}
    private fun open(source: CommandSourceStack): Int {
        val player = try {
            source.playerOrException
        } catch (_: Exception) {
            source.sendFailure(Component.literal("This command must be used by a player."))
            return 0
        }
        if (!GtsPermissions.allows(player, "use")) {
            source.sendFailure(Component.literal(GtsPermissions.denialMessage()))
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
