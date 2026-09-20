package com.zianblk.ziangts.economy

import com.zianblk.ziangts.server.GtsException
import com.zianblk.ziangts.server.ItemPayments
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object Economies {
    fun forPlayer(player: ServerPlayer, key: EconomyKey): EconomyProvider {
        // Wallet menus hold a snapshot and save it on close; never mutate behind an open container.
        if (player.containerMenu !== player.inventoryMenu) throw GtsException("command.ziangts.close_container")
        return when (key.provider) {
            "vanilla_item" -> ItemProvider(player, key.currency)
            "avecoins_wallet" -> try { AvecoinsWalletProvider(key.currency).also { it.balance(player.uuid) } }
                catch (error: Exception) {
                    com.zianblk.ziangts.ZianGts.LOGGER.error("AVECOINS wallet adapter is unavailable", error)
                    throw GtsException("command.ziangts.economy_unavailable")
                }
            else -> throw GtsException("command.ziangts.economy_unavailable")
        }
    }

    private class ItemProvider(private val player: ServerPlayer, override val currencyId: String) : EconomyProvider {
        private val item = ItemPayments.currency(currencyId) ?: throw GtsException("command.ziangts.sell.invalid_currency")
        private fun owner(id: UUID) { require(id == player.uuid) }
        override fun balance(playerId: UUID): Long { owner(playerId); return ItemPayments.balance(player, item) }
        override fun capacity(playerId: UUID): Long { owner(playerId); return ItemPayments.capacity(player, item) }
        override fun withdraw(playerId: UUID, amount: Long): EconomyResult {
            owner(playerId)
            return if (ItemPayments.withdraw(player, item, amount)) EconomyResult.Success else EconomyResult.Failure("insufficient_funds")
        }
        override fun deposit(playerId: UUID, amount: Long): EconomyResult {
            owner(playerId)
            return if (ItemPayments.deposit(player, item, amount)) EconomyResult.Success else EconomyResult.Failure("inventory_full")
        }
    }
}
