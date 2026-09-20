package com.zianblk.ziangts.server

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.zianblk.ziangts.ZianGts
import com.zianblk.ziangts.data.ListingsData
import com.zianblk.ziangts.history.TransactionHistoryData
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import java.util.UUID

object GtsCommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(literal("gts")
            .executes { ctx -> run(ctx.source) { com.zianblk.ziangts.network.GtsNetwork.open(ctx.source.playerOrException) } }
            .then(literal("list").executes { ctx -> run(ctx.source) { show(ctx.source, 1) } }
                .then(argument("page", IntegerArgumentType.integer(1)).executes { ctx ->
                    run(ctx.source) { show(ctx.source, IntegerArgumentType.getInteger(ctx, "page")) }
                }))
            .then(literal("sell").then(argument("slot", IntegerArgumentType.integer(1, 6))
                .then(argument("price", IntegerArgumentType.integer(1)).executes { ctx -> run(ctx.source) {
                    val listing = GtsService.sell(ctx.source.playerOrException,
                        IntegerArgumentType.getInteger(ctx, "slot"), IntegerArgumentType.getInteger(ctx, "price"))
                    ctx.source.sendSuccess({ Component.translatable("command.ziangts.sell.success").append(" ${listing.id}") }, false)
                } })))
            .then(literal("buy").then(argument("listing", StringArgumentType.word())
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
            .then(literal("cancel").then(argument("listing", StringArgumentType.word()).executes { ctx -> run(ctx.source) {
                GtsService.cancel(ctx.source.playerOrException, uuid(StringArgumentType.getString(ctx, "listing")))
                ctx.source.sendSuccess({ Component.translatable("command.ziangts.cancel.success") }, false)
            } }))
            .then(literal("mine").executes { ctx -> run(ctx.source) {
                val player = ctx.source.playerOrException
                ListingsData.get(player.serverLevel()).all().filter { it.sellerId == player.uuid }.forEach {
                    player.sendSystemMessage(Component.literal("${it.id} | ${it.pokemon.species.name} | ${it.price} ${it.currency}")
                        .append(Component.translatable(if (it.isExpired()) "command.ziangts.status.expired" else "command.ziangts.status.active")))
                }
            } })
            .then(literal("claim").executes { ctx -> run(ctx.source) { GtsService.claim(ctx.source.playerOrException) } })
            .then(literal("history").requires { it.hasPermission(2) }
                .executes { ctx -> run(ctx.source) { history(ctx.source, null, 1) } }
                .then(argument("player_uuid", StringArgumentType.word()).executes { ctx -> run(ctx.source) {
                    history(ctx.source, uuid(StringArgumentType.getString(ctx, "player_uuid")), 1)
                } }.then(argument("page", IntegerArgumentType.integer(1)).executes { ctx -> run(ctx.source) {
                    history(ctx.source, uuid(StringArgumentType.getString(ctx, "player_uuid")), IntegerArgumentType.getInteger(ctx, "page"))
                } }))
                .then(literal("delete").then(argument("transaction", StringArgumentType.word())
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
        val items = ListingsData.get(source.level).all().filter { !it.isExpired() }
            .sortedByDescending { it.createdAt }
        source.sendSuccess({ Component.translatable("command.ziangts.list.header", page, items.size) }, false)
        val offset = (page.toLong() - 1) * 10
        if (offset >= items.size) return
        items.drop(offset.toInt()).take(10).forEach {
            source.sendSuccess({ Component.literal("${it.id} | ${it.pokemon.species.name} | ${it.sellerName} | ${it.price} ${it.currency}") }, false)
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
