package com.zianblk.ziangts.server

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.pokemon.activestate.InactivePokemonState
import com.cobblemon.mod.common.trade.TradeManager
import com.zianblk.ziangts.config.GtsSettings
import com.zianblk.ziangts.data.Listing
import com.zianblk.ziangts.data.ListingsData
import com.zianblk.ziangts.data.TransactionRecord
import com.zianblk.ziangts.history.TransactionHistoryData
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.time.Instant
import java.util.UUID

class GtsException(val key: String) : RuntimeException(key)

/** All entry points (including network requests) run on Minecraft's server thread. */
object GtsService {
    val config get() = GtsSettings.snapshot()
    private var mutating = false

    private fun <T> mutate(player: ServerPlayer, action: () -> T): T {
        check(player.server.isSameThread) { "GTS requires the server thread" }
        if (!GtsSettings.enabled.get()) fail("disabled")
        if (mutating) fail("busy")
        if (ListingsData.get(player.serverLevel()).hasUnreadableData()) fail("storage_failed")
        mutating = true
        try { return action() } finally { mutating = false }
    }

    fun sell(player: ServerPlayer, slot: Int, price: Int): Listing = mutate(player) { sellInternal(player, slot, price) }
    fun buy(player: ServerPlayer, id: UUID) = mutate(player) { buyInternal(player, id) }
    fun cancel(player: ServerPlayer, id: UUID) = mutate(player) { cancelInternal(player, id) }
    fun claim(player: ServerPlayer): Boolean = mutate(player) { claimInternal(player) }


    private fun checkPlayer(player: ServerPlayer) {
        check(player.server.isSameThread) { "GTS requires the server thread" }
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null ||
            TradeManager.getActiveTrade(player.uuid) != null) fail("busy")
    }

    private fun fail(key: String): Nothing = throw GtsException("command.ziangts.$key")

    private fun sellInternal(player: ServerPlayer, slot: Int, price: Int): Listing {
        checkPlayer(player)
        if (slot !in 1..6 || price <= 0) fail("invalid_request")
        if (config.economyProvider != "vanilla_item" || ItemPayments.currency(config.currency) == null)
            fail("sell.invalid_currency")
        val data = ListingsData.get(player.serverLevel())
        if (data.countBySeller(player.uuid) >= config.maxListings) fail("sell.max_listings")
        val party = Cobblemon.storage.getParty(player)
        val pokemon = party.get(slot - 1) ?: fail("sell.party_empty")
        if (party.count() <= 1) fail("sell.party_last")
        if (!pokemon.tradeable) fail("sell.not_tradeable")
        if (pokemon.state !is InactivePokemonState) fail("recall_first")
        if (data.all().any { it.pokemon.uuid == pokemon.uuid }) fail("duplicate")
        val now = System.currentTimeMillis()
        val listing = Listing(UUID.randomUUID(), player.uuid, player.gameProfile.name, price,
            config.currency, now, Math.addExact(now, Math.multiplyExact(config.expirationTimeHours, 3_600_000L)), pokemon)
        // Serialize before changing ownership, so serialization errors cannot consume a Pokémon.
        listing.toNbt(player.registryAccess())
        if (!party.remove(pokemon)) fail("storage_failed")
        if (!data.add(listing)) {
            party.set(slot - 1, pokemon)
            fail("duplicate")
        }
        return listing
    }

    private fun buyInternal(player: ServerPlayer, id: UUID) {
        checkPlayer(player)
        val data = ListingsData.get(player.serverLevel())
        val listing = data.get(id) ?: fail("not_found")
        if (listing.sellerId == player.uuid) fail("own_listing")
        if (listing.isExpired()) fail("expired")
        val item = ItemPayments.currency(listing.currency) ?: fail("sell.invalid_currency")
        if (ItemPayments.balance(player, item) < listing.price) fail("insufficient_funds")
        val party = Cobblemon.storage.getParty(player)
        val pc = Cobblemon.storage.getPC(player)
        if (party.getFirstAvailablePosition() == null && pc.getFirstAvailablePosition() == null) fail("storage_full")
        if (party[listing.pokemon.uuid] != null || pc[listing.pokemon.uuid] != null) fail("duplicate")
        val pending = data.proceeds(listing.sellerId, listing.currency)
        Math.addExact(pending, listing.price.toLong())
        val record = TransactionRecord(UUID.randomUUID(), id, player.uuid, player.gameProfile.name,
            listing.sellerId, listing.sellerName, listing.price.toLong(), listing.currency,
            listing.pokemon.saveToNBT(player.registryAccess()).toString(), Instant.now())
        val history = TransactionHistoryData.get(player.serverLevel())
        // Remove the listing before invoking Cobblemon callbacks, preventing a reentrant purchase.
        check(data.remove(id) != null)
        if (!ItemPayments.withdraw(player, item, listing.price.toLong())) {
            data.add(listing)
            fail("insufficient_funds")
        }
        if (!party.add(listing.pokemon)) {
            check(ItemPayments.deposit(player, item, listing.price.toLong()))
            data.add(listing)
            fail("storage_full")
        }
        data.credit(listing.sellerId, listing.currency, listing.price.toLong())
        history.append(record)
    }

    private fun cancelInternal(player: ServerPlayer, id: UUID) {
        checkPlayer(player)
        val data = ListingsData.get(player.serverLevel())
        val listing = data.get(id) ?: fail("not_found")
        if (listing.sellerId != player.uuid) fail("not_owner")
        val party = Cobblemon.storage.getParty(player)
        val pc = Cobblemon.storage.getPC(player)
        if (party.getFirstAvailablePosition() == null && pc.getFirstAvailablePosition() == null) fail("storage_full")
        if (party[listing.pokemon.uuid] != null || pc[listing.pokemon.uuid] != null) fail("duplicate")
        check(data.remove(id) != null)
        if (!party.add(listing.pokemon)) {
            data.add(listing)
            fail("storage_full")
        }
    }

    /** Claims item proceeds; expired Pokémon remain safely escrowed until explicitly returned. */
    private fun claimInternal(player: ServerPlayer): Boolean {
        checkPlayer(player)
        val data = ListingsData.get(player.serverLevel())
        var claimed = false
        for ((currency, amount) in data.proceeds(player.uuid)) {
            val item = ItemPayments.currency(currency) ?: continue
            val give = minOf(amount, ItemPayments.capacity(player, item))
            if (give <= 0) continue
            data.debit(player.uuid, currency, give)
            if (!ItemPayments.deposit(player, item, give)) {
                data.credit(player.uuid, currency, give)
                continue
            }
            claimed = true
        }
        val key = when {
            !claimed && data.proceeds(player.uuid).isNotEmpty() -> "inventory_full"
            !claimed -> "claim.none"
            data.proceeds(player.uuid).isNotEmpty() -> "claim.partial"
            else -> "claim.success"
        }
        player.sendSystemMessage(Component.translatable("command.ziangts.$key"))
        return claimed
    }
}
