package com.zianblk.ziangts.server

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.pokemon.activestate.InactivePokemonState
import com.cobblemon.mod.common.trade.TradeManager
import com.zianblk.ziangts.config.GtsSettings
import com.zianblk.ziangts.ZianGts
import net.minecraft.nbt.CompoundTag
import com.zianblk.ziangts.economy.Economies
import com.zianblk.ziangts.economy.EconomyKey
import com.zianblk.ziangts.economy.EconomyResult
import com.zianblk.ziangts.economy.AvecoinsCatalog
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
        GtsJournal.requireTrading(player.server)
        if (ListingsData.get(player.serverLevel()).hasUnreadableData()) fail("storage_failed")
        mutating = true
        try { return action() } finally { mutating = false }
    }

    fun sell(player: ServerPlayer, slot: Int, price: Int, currency: String = config.currency): Listing =
        mutate(player) { sellInternal(player, slot, price, currency) }
    fun buy(player: ServerPlayer, id: UUID) = mutate(player) { buyInternal(player, id) }
    fun cancel(player: ServerPlayer, id: UUID) = mutate(player) { cancelInternal(player, id) }
    fun claim(player: ServerPlayer): String = mutate(player) { claimInternal(player) }


    private fun checkPlayer(player: ServerPlayer) {
        check(player.server.isSameThread) { "GTS requires the server thread" }
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null ||
            TradeManager.getActiveTrade(player.uuid) != null) fail("busy")
    }

    private fun fail(key: String): Nothing = throw GtsException("command.ziangts.$key")

    private fun sellInternal(player: ServerPlayer, slot: Int, price: Int, currency: String): Listing {
        GtsPermissions.require(player, "ziangts.sell")
        checkPlayer(player)
        if (slot !in 1..6 || price <= 0) fail("invalid_request")
        if (!AvecoinsCatalog.isSupported(currency)) fail("sell.invalid_currency")
        val settings = config
        val economyKey = try { EconomyKey(settings.economyProvider, currency) }
            catch (_: IllegalArgumentException) { fail("sell.invalid_currency") }
        Economies.forPlayer(player, economyKey)
        if (economyKey.provider == "avecoins_wallet" && price > 1728) fail("wallet_price_limit")
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
            currency, now, Math.addExact(now, Math.multiplyExact(settings.expirationTimeHours, 3_600_000L)),
            pokemon, settings.economyProvider)
        // Serialize before changing ownership, so serialization errors cannot consume a Pokémon.
        val snapshot = listing.toNbt(player.registryAccess())
        val operation = GtsJournal.begin(player, "sell", snapshot)
        GtsJournal.stage(operation, "before_party_remove")
        if (!storageMutation(data, "sell", player, snapshot) { party.remove(pokemon) }) {
            GtsJournal.finish(player, operation)
            fail("storage_failed")
        }
        GtsJournal.stage(operation, "party_removed")
        if (!data.add(listing)) {
            party.set(slot - 1, pokemon)
            GtsJournal.finish(player, operation)
            fail("duplicate")
        }
        GtsJournal.finish(player, operation)
        return listing
    }

    private fun buyInternal(player: ServerPlayer, id: UUID) {
        GtsPermissions.require(player, "ziangts.buy")
        checkPlayer(player)
        val data = ListingsData.get(player.serverLevel())
        val listing = data.get(id) ?: fail("not_found")
        if (listing.sellerId == player.uuid) fail("own_listing")
        if (listing.isExpired()) fail("expired")
        if (!AvecoinsCatalog.isSupported(listing.currency)) fail("unsupported_currency")
        val economyKey = EconomyKey(listing.economyProvider, listing.currency)
        val economy = Economies.forPlayer(player, economyKey)
        if (economy.balance(player.uuid) < listing.price) fail("insufficient_funds")
        val party = Cobblemon.storage.getParty(player)
        val pc = Cobblemon.storage.getPC(player)
        if (party.getFirstAvailablePosition() == null && pc.getFirstAvailablePosition() == null) fail("storage_full")
        if (party[listing.pokemon.uuid] != null || pc[listing.pokemon.uuid] != null) fail("duplicate")
        val pending = data.proceeds(listing.sellerId, economyKey.storageKey)
        Math.addExact(pending, listing.price.toLong())
        val record = TransactionRecord(UUID.randomUUID(), id, player.uuid, player.gameProfile.name,
            listing.sellerId, listing.sellerName, listing.price.toLong(), listing.currency,
            listing.pokemon.saveToNBT(player.registryAccess()).toString(), Instant.now(), listing.economyProvider)
        val history = TransactionHistoryData.get(player.serverLevel())
        if (history.hasUnreadableRoot()) fail("storage_failed")
        val snapshot = listing.toNbt(player.registryAccess()).apply { put("transaction", record.toNbt()) }
        val operation = GtsJournal.begin(player, "buy", snapshot)
        // Remove the listing before invoking Cobblemon callbacks, preventing a reentrant purchase.
        check(data.remove(id) != null)
        GtsJournal.stage(operation, "before_payment")
        val payment = storageMutation(data, "buy_payment", player, snapshot) {
            economy.withdraw(player.uuid, listing.price.toLong())
        }
        if (payment is EconomyResult.Failure) {
            data.add(listing)
            GtsJournal.finish(player, operation)
            fail(payment.reason)
        }
        GtsJournal.stage(operation, "payment_complete_before_delivery")
        if (!storageMutation(data, "buy", player, snapshot) { party.add(listing.pokemon) }) {
            GtsJournal.stage(operation, "delivery_rejected_before_refund")
            val refund = storageMutation(data, "buy_refund", player, snapshot) {
                economy.deposit(player.uuid, listing.price.toLong())
            }
            if (refund != EconomyResult.Success) {
                preserveIncident(data, "buy_refund", player, snapshot)
                fail("storage_failed")
            }
            data.add(listing)
            GtsJournal.finish(player, operation)
            fail("storage_full")
        }
        GtsJournal.stage(operation, "delivered_before_credit")
        PurchaseFinalization.complete(
            creditSeller = { data.credit(listing.sellerId, economyKey.storageKey, listing.price.toLong()) },
            appendHistory = { GtsJournal.stage(operation, "credited_before_history"); history.append(record) },
            quarantine = { operation, error ->
                ZianGts.LOGGER.error("GTS {} failed after delivery of listing {}", operation, id, error)
                preserveIncident(data, operation, player, snapshot)
            }
        )
        GtsJournal.finish(player, operation)
    }

    private fun cancelInternal(player: ServerPlayer, id: UUID) {
        GtsPermissions.require(player, "ziangts.cancel")
        checkPlayer(player)
        val data = ListingsData.get(player.serverLevel())
        val listing = data.get(id) ?: fail("not_found")
        if (listing.sellerId != player.uuid) fail("not_owner")
        val party = Cobblemon.storage.getParty(player)
        val pc = Cobblemon.storage.getPC(player)
        if (party.getFirstAvailablePosition() == null && pc.getFirstAvailablePosition() == null) fail("storage_full")
        if (party[listing.pokemon.uuid] != null || pc[listing.pokemon.uuid] != null) fail("duplicate")
        val snapshot = listing.toNbt(player.registryAccess())
        val operation = GtsJournal.begin(player, "cancel", snapshot)
        check(data.remove(id) != null)
        GtsJournal.stage(operation, "before_return")
        if (!storageMutation(data, "cancel", player, snapshot) { party.add(listing.pokemon) }) {
            data.add(listing)
            GtsJournal.finish(player, operation)
            fail("storage_full")
        }
        GtsJournal.finish(player, operation)
    }

    /** Unknown callback outcomes must be quarantined, never retried as a fresh purchase. */
    private fun <T> storageMutation(data: ListingsData, operation: String, player: ServerPlayer,
                                   snapshot: CompoundTag, action: () -> T): T = try {
        action()
    } catch (error: Exception) {
        preserveIncident(data, operation, player, snapshot)
        ZianGts.LOGGER.error("GTS {} callback failed for {}. Trading blocked; inspect failedTransfers before recovery.",
            operation, player.uuid, error)
        fail("storage_failed")
    }

    /** Persist an independent incident copy before waiting for the next world save. */
    private fun preserveIncident(data: ListingsData, operation: String, player: ServerPlayer, snapshot: CompoundTag) {
        data.quarantineTransfer(operation, player.uuid, snapshot)
        try {
            val directory = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("ziangts-recovery")
            java.nio.file.Files.createDirectories(directory)
            val incident = CompoundTag().apply {
                putString("operation", operation)
                putUUID("actor", player.uuid)
                putLong("recordedAt", System.currentTimeMillis())
                put("snapshot", snapshot.copy())
            }
            val bytes = java.nio.ByteBuffer.wrap(incident.toString().toByteArray(Charsets.UTF_8))
            java.nio.channels.FileChannel.open(directory.resolve("${UUID.randomUUID()}.snbt"),
                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE).use { channel ->
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            player.server.overworld().dataStorage.save()
        } catch (error: Exception) {
            ZianGts.LOGGER.error("Could not persist GTS recovery incident; trading remains blocked. Snapshot: {}", snapshot, error)
        }
    }

    /** Claims each payout through its original provider, even after configuration changes. */
    private fun claimInternal(player: ServerPlayer): String {
        GtsPermissions.require(player, "ziangts.claim")
        checkPlayer(player)
        val data = ListingsData.get(player.serverLevel())
        var claimed = false
        var failure: String? = null
        for ((currency, amount) in data.proceeds(player.uuid)) {
            val key = EconomyKey.parse(currency)
            val economy = try { Economies.forPlayer(player, key) }
                catch (error: GtsException) {
                    failure = error.key.removePrefix("command.ziangts.")
                    continue
                }
            val give = minOf(amount, economy.capacity(player.uuid))
            if (give <= 0) continue
            val snapshot = CompoundTag().apply {
                putString("economyProvider", key.provider)
                putString("currency", key.currency)
                putLong("amount", give)
                putLong("pendingBefore", amount)
            }
            val operation = GtsJournal.begin(player, "claim", snapshot)
            GtsJournal.stage(operation, "before_proceeds_debit")
            data.debit(player.uuid, currency, give)
            GtsJournal.stage(operation, "before_payout")
            val result = storageMutation(data, "claim", player, snapshot) { economy.deposit(player.uuid, give) }
            if (result is EconomyResult.Failure) {
                data.credit(player.uuid, currency, give)
                GtsJournal.finish(player, operation)
                failure = result.reason
                continue
            }
            GtsJournal.finish(player, operation)
            claimed = true
        }
        val key = when {
            !claimed && failure != null -> failure
            !claimed && data.proceeds(player.uuid).isNotEmpty() -> "payment_destination_full"
            !claimed -> "claim.none"
            data.proceeds(player.uuid).isNotEmpty() -> "claim.partial"
            else -> "claim.success"
        }
        player.sendSystemMessage(Component.translatable("command.ziangts.$key"))
        return "command.ziangts.$key"
    }
}
