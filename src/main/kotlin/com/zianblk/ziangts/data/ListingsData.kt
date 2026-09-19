package com.zianblk.ziangts.data

import java.util.UUID
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
class ListingsData : SavedData() {
    private val listings = linkedMapOf<UUID, Listing>()

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

    fun removeExpired(nowEpochMillis: Long = System.currentTimeMillis()): List<Listing> {
        val expired = listings.values.filter { it.isExpired(nowEpochMillis) }
        if (expired.isEmpty()) return emptyList()

        expired.forEach { listings.remove(it.id) }
        setDirty()
        return expired
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val entries = ListTag()
        listings.values.forEach { listing ->
            entries.add(listing.toNbt(registries))
        }
        tag.put(KEY_LISTINGS, entries)
        return tag
    }

    companion object {
        private const val DATA_NAME = "ziangts_listings"
        private const val KEY_LISTINGS = "listings"

        private val FACTORY = Factory(
            { ListingsData() },
            { tag, registries -> load(tag, registries) },
            null
        )

        fun get(level: ServerLevel): ListingsData {
            val overworld = level.server.overworld()
            return overworld.dataStorage.computeIfAbsent(FACTORY, DATA_NAME)
        }

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): ListingsData {
            val data = ListingsData()
            val entries = tag.getList(KEY_LISTINGS, Tag.TAG_COMPOUND.toInt())

            for (index in 0 until entries.size) {
                val listingTag = entries.getCompound(index)
                runCatching {
                    Listing.fromNbt(registries, listingTag)
                }.onSuccess { listing ->
                    data.listings[listing.id] = listing
                }
            }

            return data
        }
    }
}
