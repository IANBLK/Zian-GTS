package com.zianblk.ziangts.server

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.zianblk.ziangts.ZianGts
import com.zianblk.ziangts.data.ListingsData
import com.zianblk.ziangts.economy.AvecoinsCatalog
import com.zianblk.ziangts.history.TransactionHistoryData
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import net.minecraft.commands.SharedSuggestionProvider
import java.util.UUID

object GtsCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(literal("gts").requires { GtsPermissions.allowed(it, "ziangts.use") }
            .executes { ctx -> run(ctx.source) { com.zianblk.ziangts.network.GtsNetwork.open(ctx.source.playerOrException) } }
            .then(literal("list").requires { GtsPermissions.allowed(it, "ziangts.list") }.executes { ctx -> run(ctx.source) { show(ctx.source, 1) } }
                .then(argument("page", IntegerArgumentType.integer(1)).executes { ctx ->
                    run(ctx.source) { show(ctx.source, IntegerArgumentType.getInteger(ctx, "page")) }
                }))
            .then(literal("sell").requires { GtsPermissions.allowed(it, "ziangts.sell") }.then(argument("slot", IntegerArgumentType.integer(1, 6))
                .then(argument("price", IntegerArgumentType.integer(1)).executes { ctx -> run(ctx.source) {
                    val listing = GtsService.sell(ctx.source.playerOrException,
                        IntegerArgumentType.getInteger(ctx, "slot"), IntegerArgumentType.getInteger(ctx, "price"))
                    ctx.source.sendSuccess({ Component.translatable("command.ziangts.sell.success").append(" ${listing.id}") }, false)
                } }.then(argument("currency", StringArgumentType.greedyString())
                    .suggests { _, builder ->
                        SharedSuggestionProvider.suggest(AvecoinsCatalog.all(), builder)
                    }
                    .executes { ctx -> run(ctx.source) {
                        val listing = GtsService.sell(ctx.source.playerOrException,
                            IntegerArgumentType.getInteger(ctx, "slot"),
                            IntegerArgumentType.getInteger(ctx, "price"),
                            StringArgumentType.getString(ctx, "currency"))
                        ctx.source.sendSuccess({ Component.translatable("command.ziangts.sell.success").append(" ${listing.id}") }, false)
                    } }))))
            .then(literal("buy").requires { GtsPermissions.allowed(it, "ziangts.buy") }.then(argument("listing", StringArgumentType.word())
                .executes { ctx -> run(ctx.source) {
                    val id = uuid(StringArgumentType.getString(ctx, "listing"))
                    val listing = ListingsData.get(ctx.source.level).get(id) ?: throw GtsException("command.ziangts.not_found")
                    ctx.source.sendSuccess({ Component.translatable("command.ziangts.confirm_purchase", listing.price,
                        "${listing.currency} (${listing.economyProvider})", "/gts buy $id confirm") }, false)
                } }
                .then(literal("confirm").executes { ctx -> run(ctx.source) {
                    GtsService.buy(ctx.source.playerOrException, uuid(StringArgumentType.getString(ctx, "listing")))
                    ctx.source.sendSuccess({ Component.translatable("command.ziangts.purchase.success") }, false)
                } })))
            .then(literal("cancel").requires { GtsPermissions.allowed(it, "ziangts.cancel") }.then(argument("listing", StringArgumentType.word())
                .suggests { ctx, builder ->
                    val player = ctx.source.playerOrException
                    val ids = ListingsData.get(player.serverLevel()).all().asSequence()
                        .filter { it.sellerId == player.uuid }
                        .map { it.id.toString() }
                    SharedSuggestionProvider.suggest(ids, builder)
                }
                .executes { ctx -> run(ctx.source) {
                GtsService.cancel(ctx.source.playerOrException, uuid(StringArgumentType.getString(ctx, "listing")))
                ctx.source.sendSuccess({ Component.translatable("command.ziangts.cancel.success") }, false)
            } }))
            .then(literal("mylistings").requires { GtsPermissions.allowed(it, "ziangts.mine") }.executes { ctx -> run(ctx.source) { showMine(ctx.source) } })
            .then(literal("mine").requires { GtsPermissions.allowed(it, "ziangts.mine") }.executes { ctx -> run(ctx.source) {
                val player = ctx.source.playerOrException
                ListingsData.get(player.serverLevel()).all().filter { it.sellerId == player.uuid }.forEach {
                    player.sendSystemMessage(Component.literal("${it.id} | ${it.pokemon.species.name} | ${it.price} ${it.currency}")
                        .append(Component.translatable(if (it.isExpired()) "command.ziangts.status.expired" else "command.ziangts.status.active")))
                }
            } })
            .then(literal("claim").requires { GtsPermissions.allowed(it, "ziangts.claim") }.executes { ctx -> run(ctx.source) { GtsService.claim(ctx.source.playerOrException) } })
            .then(literal("recovery").requires { GtsPermissions.allowed(it, "ziangts.admin.recovery") }
                .executes { ctx -> run(ctx.source) {
                    val data = ListingsData.get(ctx.source.level)
                    val incidents = data.transferIncidents()
                    ctx.source.sendSuccess({ Component.translatable("command.ziangts.recovery.summary",
                        data.hasUnreadableData(), incidents.size) }, false)
                    incidents.takeLast(10).forEach { incident ->
                        val id = if (incident.hasUUID("incidentId")) incident.getUUID("incidentId").toString() else "sin-id"
                        val actor = if (incident.hasUUID("actor")) incident.getUUID("actor").toString() else "desconocido"
                        ctx.source.sendSuccess({ Component.literal("$id | ${incident.getString("operation")} | $actor | ${incident.getLong("recordedAt")}") }, false)
                    }
                } }
                .then(literal("resolve").requires { GtsPermissions.allowed(it, "ziangts.admin.recovery.resolve") }
                    .then(argument("incident", StringArgumentType.word())
                        .executes { ctx -> run(ctx.source) {
                            val id = uuid(StringArgumentType.getString(ctx, "incident"))
                            ctx.source.sendSuccess({ Component.translatable("command.ziangts.recovery.confirm_resolve",
                                "/gts recovery resolve $id confirm") }, false)
                        } }
                        .then(literal("confirm").executes { ctx -> run(ctx.source) {
                            val id = uuid(StringArgumentType.getString(ctx, "incident"))
                            val data = ListingsData.get(ctx.source.level)
                            if (!data.resolveIncident(id, ctx.source.textName, resolvedById = ctx.source.entity?.uuid))
                                throw GtsException("command.ziangts.not_found")
                            ctx.source.server.overworld().dataStorage.save()
                            ZianGts.LOGGER.warn("GTS incident {} marked as resolved by {}", id, ctx.source.textName)
                            ctx.source.sendSuccess({ Component.translatable("command.ziangts.recovery.resolved", id.toString()) }, true)
                        } }))))
            .then(literal("history").requires { GtsPermissions.allowed(it, "ziangts.admin.history") }
                .executes { ctx -> run(ctx.source) { history(ctx.source, null, 1) } }
                .then(argument("player_uuid", StringArgumentType.word()).executes { ctx -> run(ctx.source) {
                    history(ctx.source, uuid(StringArgumentType.getString(ctx, "player_uuid")), 1)
                } }.then(argument("page", IntegerArgumentType.integer(1)).executes { ctx -> run(ctx.source) {
                    history(ctx.source, uuid(StringArgumentType.getString(ctx, "player_uuid")), IntegerArgumentType.getInteger(ctx, "page"))
                } }))
                .then(literal("delete").requires { GtsPermissions.allowed(it, "ziangts.admin.history.archive") }.then(argument("transaction", StringArgumentType.word())
                    .executes { ctx -> run(ctx.source) {
                        ctx.source.sendSuccess({ Component.translatable("command.ziangts.history.confirm_delete",
                            "/gts history delete ${uuid(StringArgumentType.getString(ctx, "transaction"))} confirm") }, false)
                    } }.then(literal("confirm").executes { ctx -> run(ctx.source) {
                        val id = uuid(StringArgumentType.getString(ctx, "transaction"))
                        val data = TransactionHistoryData.get(ctx.source.level)
                        if (data.archive(id, ctx.source.textName)) {
                            ZianGts.LOGGER.warn("GTS history entry {} archived by {}", id, ctx.source.textName)
                            ctx.source.sendSuccess({ Component.translatable("command.ziangts.history.deleted", id.toString()) }, true)
                        } else throw GtsException("command.ziangts.not_found")
                    } }))))
        )
    }

    private fun uuid(value: String): UUID = try { UUID.fromString(value) }
        catch (_: IllegalArgumentException) { throw GtsException("command.ziangts.invalid_request") }

    private fun show(source: CommandSourceStack, page: Int) {
        val items = ListingsData.get(source.level).all().filter { !it.isExpired() && AvecoinsCatalog.isSupported(it.currency) }
            .sortedByDescending { it.createdAt }
        source.sendSuccess({ Component.translatable("command.ziangts.list.header", page, items.size) }, false)
        val offset = (page.toLong() - 1) * 10
        if (offset >= items.size) return
        items.drop(offset.toInt()).take(10).forEach {
            source.sendSuccess({ Component.literal("${it.id} | ${it.pokemon.species.name} | ${it.sellerName} | ${it.price} ${it.currency}") }, false)
        }
    }

    private fun showMine(source: CommandSourceStack) {
        val player = source.playerOrException
        val now = System.currentTimeMillis()
        val items = ListingsData.get(player.serverLevel()).all()
            .filter { it.sellerId == player.uuid }
            .sortedByDescending { it.createdAt }
        source.sendSuccess({ Component.translatable("command.ziangts.mylistings.header", items.size) }, false)
        if (items.isEmpty()) {
            source.sendSuccess({ Component.translatable("command.ziangts.mylistings.empty") }, false)
            return
        }
        items.forEach {
            val remaining = if (it.isExpired(now)) 0L else maxOf(0L, it.expiresAt - now)
            val minutes = remaining / 60_000L
            val status = if (it.isExpired(now)) "expired" else "active"
            source.sendSuccess({
                Component.literal("${it.pokemon.species.name} | ${it.price} ${it.currency} | ")
                    .append(Component.translatable("command.ziangts.status.$status"))
                    .append(Component.literal(" | ${minutes}m | /gts cancel ${it.id}"))
            }, false)
        }
    }

    private fun history(source: CommandSourceStack, player: UUID?, page: Int) {
        val data = TransactionHistoryData.get(source.level)
        val items = (if (player == null) data.all() else data.findByPlayer(player)).sortedByDescending { it.completedAt }
        source.sendSuccess({ Component.translatable("command.ziangts.history.header", page, items.size) }, false)
        val offset = (page.toLong() - 1) * 10
        if (offset >= items.size) return
        items.drop(offset.toInt()).take(10).forEach {
            source.sendSuccess({ Component.literal("${it.transactionId} | ${it.completedAt} | ${it.buyerName} ← ${it.sellerName} | ${it.amount} ${it.currency} (${it.economyProvider})") }, false)
        }
    }

    private fun run(source: CommandSourceStack, action: () -> Unit): Int = try {
        action()
        1
    } catch (error: GtsException) {
        source.sendFailure(Component.translatable(error.key))
        0
    }
}
