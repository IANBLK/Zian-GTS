package com.zianblk.ziangts.history

import com.zianblk.ziangts.data.TransactionRecord
import java.time.Instant
import java.util.UUID
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData

/**
 * Persistent immutable audit history for completed sales.
 *
 * Records live independently from active listings so a completed/expired
 * listing cannot erase evidence needed for moderation.
 */
class TransactionHistoryData : SavedData(), TransactionHistory {
    private val records = linkedMapOf<UUID, TransactionRecord>()
    private val archived = ListTag()
    private val unreadable = ListTag()
    private var unreadableRoot: CompoundTag? = null
    fun hasUnreadableRoot(): Boolean = unreadableRoot != null

    override fun append(record: TransactionRecord) {
        check(unreadableRoot == null) { "History root is unreadable" }
        require(!records.containsKey(record.transactionId)) {
            "transactionId already exists: ${record.transactionId}"
        }
        records[record.transactionId] = record
        setDirty()
    }

    override fun findByTransactionId(id: UUID): TransactionRecord? = records[id]

    override fun findByPlayer(playerId: UUID): List<TransactionRecord> =
        records.values.filter { it.buyerId == playerId || it.sellerId == playerId }

    override fun all(): List<TransactionRecord> =
        records.values.sortedByDescending { it.completedAt }

    override fun delete(id: UUID): Boolean = archive(id, "API")

    fun archive(id: UUID, actor: String): Boolean {
        if (unreadableRoot != null) return false
        val record = records.remove(id) ?: return false
        archived.add(record.toNbt().apply {
            putString("archivedBy", actor)
            putLong("archivedAt", System.currentTimeMillis())
        })
        setDirty()
        return true
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        unreadableRoot?.let { return it.copy() }
        val entries = ListTag()
        records.values.forEach { record ->
            entries.add(record.toNbt())
        }
        entries.addAll(unreadable.map { it.copy() })
        tag.put(KEY_TRANSACTIONS, entries)
        tag.put("archivedTransactions", archived.copy())
        return tag
    }

    private fun TransactionRecord.toNbt(): CompoundTag = CompoundTag().apply {
        putUUID("transactionId", transactionId)
        putUUID("listingId", listingId)
        putUUID("buyerId", buyerId)
        putString("buyerName", buyerName)
        putUUID("sellerId", sellerId)
        putString("sellerName", sellerName)
        putLong("amount", amount)
        putString("currency", currency)
        putString("economyProvider", economyProvider)
        putString("pokemonSnapshot", pokemonSnapshot)
        putLong("completedAt", completedAt.toEpochMilli())
    }

    companion object {
        private const val DATA_NAME = "ziangts_transactions"
        private const val KEY_TRANSACTIONS = "transactions"

        private val FACTORY = Factory(
            { TransactionHistoryData() },
            { tag, registries -> load(tag, registries) },
            null
        )

        fun get(level: ServerLevel): TransactionHistoryData {
            val overworld = level.server.overworld()
            return overworld.dataStorage.computeIfAbsent(FACTORY, DATA_NAME)
        }

        private fun load(
            tag: CompoundTag,
            @Suppress("UNUSED_PARAMETER") registries: HolderLookup.Provider
        ): TransactionHistoryData {
            val data = TransactionHistoryData()
            for (key in listOf(KEY_TRANSACTIONS, "archivedTransactions")) {
                val raw = tag.get(key) ?: continue
                if (raw !is ListTag || (raw.isNotEmpty() && raw.elementType != Tag.TAG_COMPOUND)) {
                    data.unreadableRoot = tag.copy()
                    return data
                }
            }
            val entries = tag.getList(KEY_TRANSACTIONS, Tag.TAG_COMPOUND.toInt())

            for (index in 0 until entries.size) {
                val entry = entries.getCompound(index)
                runCatching {
                    TransactionRecord(
                        transactionId = entry.getUUID("transactionId"),
                        listingId = entry.getUUID("listingId"),
                        buyerId = entry.getUUID("buyerId"),
                        buyerName = entry.getString("buyerName"),
                        sellerId = entry.getUUID("sellerId"),
                        sellerName = entry.getString("sellerName"),
                        amount = entry.getLong("amount"),
                        currency = entry.getString("currency"),
                        pokemonSnapshot = entry.getString("pokemonSnapshot"),
                        completedAt = Instant.ofEpochMilli(entry.getLong("completedAt")),
                        economyProvider = if (entry.contains("economyProvider")) entry.getString("economyProvider") else "vanilla_item"
                    )
                }.onSuccess { record ->
                    if (data.records.containsKey(record.transactionId)) data.unreadable.add(entry.copy())
                    else data.records[record.transactionId] = record
                }.onFailure { error ->
                    data.unreadable.add(entry.copy())
                    com.zianblk.ziangts.ZianGts.LOGGER.error("Preserved unreadable GTS transaction at index {}", index, error)
                }
            }

            data.archived.addAll(tag.getList("archivedTransactions", Tag.TAG_COMPOUND.toInt()).map { it.copy() })
            return data
        }
    }
}

