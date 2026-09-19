package com.zianblk.ziangts.server

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Item currency never drops overflow on the ground. Called on the server thread only. */
object ItemPayments {
    fun currency(id: String): Item? {
        val key = ResourceLocation.tryParse(id) ?: return null
        if (!BuiltInRegistries.ITEM.containsKey(key)) return null
        return BuiltInRegistries.ITEM.get(key).takeUnless { it == Items.AIR }
    }

    private fun matches(stack: ItemStack, item: Item): Boolean =
        !stack.isEmpty && ItemStack.isSameItemSameComponents(stack, ItemStack(item))

    fun balance(player: ServerPlayer, item: Item): Long =
        player.inventory.items.filter { matches(it, item) }.sumOf { it.count.toLong() }

    fun capacity(player: ServerPlayer, item: Item): Long = player.inventory.items.sumOf {
        when {
            it.isEmpty -> item.defaultMaxStackSize.toLong()
            matches(it, item) -> (it.maxStackSize - it.count).toLong()
            else -> 0L
        }
    }

    fun withdraw(player: ServerPlayer, item: Item, amount: Long): Boolean {
        require(amount > 0)
        if (balance(player, item) < amount) return false
        var remaining = amount
        for (stack in player.inventory.items) {
            if (matches(stack, item)) {
                val take = minOf(remaining, stack.count.toLong()).toInt()
                stack.shrink(take)
                remaining -= take
                if (remaining == 0L) break
            }
        }
        player.inventory.setChanged()
        player.containerMenu.broadcastChanges()
        return true
    }

    fun deposit(player: ServerPlayer, item: Item, amount: Long): Boolean {
        require(amount > 0)
        if (capacity(player, item) < amount) return false
        var remaining = amount
        for (stack in player.inventory.items) {
            if (matches(stack, item)) {
                val give = minOf(remaining, (stack.maxStackSize - stack.count).toLong()).toInt()
                stack.grow(give)
                remaining -= give
                if (remaining == 0L) break
            }
        }
        for (index in player.inventory.items.indices) {
            if (remaining == 0L) break
            if (player.inventory.items[index].isEmpty) {
                val give = minOf(remaining, item.defaultMaxStackSize.toLong()).toInt()
                player.inventory.items[index] = ItemStack(item, give)
                remaining -= give
            }
        }
        player.inventory.setChanged()
        player.containerMenu.broadcastChanges()
        return true
    }
}
