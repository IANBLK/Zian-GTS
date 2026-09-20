package com.zianblk.ziangts.data

import java.util.UUID
import net.minecraft.core.RegistryAccess
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData

/**
 * Persistent server-side storage for active GTS listings.
 *
 * Data is stored in the overworld data storage so every dimension shares the
 * same market. Every mutating operation marks the SavedData dirty.
 */
class ListingsData(private val registryAccess: RegistryAccess) : SavedData() {
    private val listings = linkedMapOf<UUID, Listing>()
    private val balances = linkedMapOf<UUID, MutableMap<String, Long>>()
    private val unreadableListings = ListTag()
    private val unreadableProceeds = ListTag()
    private val failedTransfers = ListTag()
    private var unreadableRoot: CompoundTag? = null

    fun quarantineTransfer(operation: String, actor: UUID, snapshot: CompoundTag) {
        failedTransfers.add(CompoundTag().apply {
            putString("operation", operation)
            putUUID("actor", actor)
            putLong("recordedAt", System.currentTimeMillis())
            put("listing", snapshot.copy())
        })
        setDirty()
    }
    fun hasUnreadableData(): Boolean = unreadableRoot != null || unreadableListings.isNotEmpty() || unreadableProceeds.isNotEmpty() || failedTransfers.isNotEmpty()

    fun proceeds(seller: UUID): Map<String, Long> = balances[seller]?.toMap() ?: emptyMap()
    fun proceeds(seller: UUID, currency: String): Long = balances[seller]?.get(currency) ?: 0L

    fun credit(seller: UUID, currency: String, amount: Long) {
        require(amount > 0 && currency.isNotBlank())
        val total = Math.addExact(proceeds(seller, currency), amount)
        balances.getOrPut(seller) { linkedMapOf() }[currency] = total
        setDirty()
    }

    fun debit(seller: UUID, currency: String, amount: Long) {
        require(amount > 0 && amount <= proceeds(seller, currency))
        val remaining = proceeds(seller, currency) - amount
        if (remaining == 0L) {
            balances[seller]?.remove(currency)
            if (balances[seller]?.isEmpty() == true) balances.remove(seller)
        } else balances.getValue(seller)[currency] = remaining
        setDirty()
    }


    fun all(): List<Listing> = listings.values.toList()

    fun get(id: UUID): Listing? = listings[id]

    fun countBySeller(sellerId: UUID): Int =
        listings.values.count { it.sellerId == sellerId }

    fun add(listing: Listing): Boolean {
        if (listings.containsKey(listing.id)) return false
        listings[listing.id] = listing
        setDirty()
        return true
    }

    fun remove(id: UUID): Listing? {
        val removed = listings.remove(id) ?: return null
        setDirty()
        return removed
    }

    // Expiry hides an offer from buyers; it must not destroy the seller's Pokémon.
    fun expiredBySeller(seller: UUID, now: Long = System.currentTimeMillis()): List<Listing> =
        listings.values.filter { it.sellerId == seller && it.isExpired(now) }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        unreadableRoot?.let { return it.copy() }
        tag.put("failedTransfers", failedTransfers.copy())
        val entries = ListTag()
        listings.values.forEach { listing ->
            entries.add(listing.toNbt(registryAccess))
        }
        entries.addAll(unreadableListings.map { it.copy() })
        tag.put(KEY_LISTINGS, entries)
        val payouts = ListTag()
        balances.forEach { (seller, currencies) -> currencies.forEach { (currency, amount) ->
            payouts.add(CompoundTag().apply {
                putUUID("seller", seller)
                putString("currency", currency)
                putLong("amount", amount)
            })
        } }
        payouts.addAll(unreadableProceeds.map { it.copy() })
        tag.put("proceeds", payouts)
        return tag
    }

    companion object {
        private const val DATA_NAME = "ziangts_listings"
        private const val KEY_LISTINGS = "listings"

        fun get(level: ServerLevel): ListingsData {
            val overworld = level.server.overworld()
            val access = overworld.registryAccess()
            val factory = Factory(
                { ListingsData(access) },
                { tag, _ -> load(tag, access) },
                null
            )
            return overworld.dataStorage.computeIfAbsent(factory, DATA_NAME)
        }

        internal fun load(tag: CompoundTag, registries: RegistryAccess): ListingsData {
            val data = ListingsData(registries)
            for (key in listOf(KEY_LISTINGS, "proceeds", "failedTransfers")) {
                val raw = tag.get(key) ?: continue
                if (raw !is ListTag || (raw.isNotEmpty() && raw.elementType != Tag.TAG_COMPOUND)) {
                    data.unreadableRoot = tag.copy()
                    return data
                }
            }
            data.failedTransfers.addAll(tag.getList("failedTransfers", Tag.TAG_COMPOUND.toInt()).map { it.copy() })
            val entries = tag.getList(KEY_LISTINGS, Tag.TAG_COMPOUND.toInt())

            for (index in 0 until entries.size) {
                val listingTag = entries.getCompound(index)
                runCatching {
                    Listing.fromNbt(registries, listingTag)
                }.onSuccess { listing ->
                    if (data.listings.containsKey(listing.id)) {
                        data.unreadableListings.add(listingTag.copy())
                    } else data.listings[listing.id] = listing
                }.onFailure { error ->
                    data.unreadableListings.add(listingTag.copy())
                    com.zianblk.ziangts.ZianGts.LOGGER.error("Preserved unreadable GTS listing at index {}", index, error)
                }
            }

            val payouts = tag.getList("proceeds", Tag.TAG_COMPOUND.toInt())
            for (index in 0 until payouts.size) {
                val entry = payouts.getCompound(index)
                runCatching {
                    val seller = entry.getUUID("seller")
                    val currency = entry.getString("currency")
                    val amount = entry.getLong("amount")
                    require(currency.isNotBlank() && amount > 0) { "Invalid GTS proceeds record" }
                    require(data.balances[seller]?.containsKey(currency) != true) { "Duplicate GTS proceeds record" }
                    data.balances.getOrPut(seller) { linkedMapOf() }[currency] = amount
                }.onFailure { error ->
                    data.unreadableProceeds.add(entry.copy())
                    com.zianblk.ziangts.ZianGts.LOGGER.error("Preserved unreadable GTS payout at index {}", index, error)
                }
            }
            return data
        }
    }
}

