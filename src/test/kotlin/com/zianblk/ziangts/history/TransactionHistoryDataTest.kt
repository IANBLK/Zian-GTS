package com.zianblk.ziangts.history

import com.zianblk.ziangts.data.TransactionRecord
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TransactionHistoryDataTest {
    private val registries = RegistryAccess.EMPTY
    private fun record() = TransactionRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Buyer",
        UUID.randomUUID(), "Seller", 42, "avecoins:goldcoin", "{snapshot:1}",
        Instant.ofEpochMilli(1700000000000), "avecoins_wallet")
    private fun reload(data: TransactionHistoryData) = TransactionHistoryData.load(data.save(CompoundTag(), registries), registries)

    @Test fun `sale and archive retain all audit fields across reload`() {
        val original = record()
        val data = TransactionHistoryData()
        data.append(original)
        val restored = reload(data)
        assertEquals(original, restored.findByTransactionId(original.transactionId))
        assertEquals(listOf(original), restored.findByPlayer(original.sellerId))
        assertTrue(restored.archive(original.transactionId, "Administrator"))
        val archived = reload(restored)
        assertNull(archived.findByTransactionId(original.transactionId))
        val snapshot = archived.save(CompoundTag(), registries).getList("archivedTransactions", 10).getCompound(0)
        assertEquals("Administrator", snapshot.getString("archivedBy"))
        assertEquals(original.pokemonSnapshot, snapshot.getString("pokemonSnapshot"))
        assertEquals(original.economyProvider, snapshot.getString("economyProvider"))
        assertTrue(snapshot.getLong("archivedAt") > 0)
    }

    @Test fun `archiving cannot permit reuse of a transaction identifier`() {
        val original = record()
        val data = TransactionHistoryData()
        data.append(original)
        assertThrows(IllegalArgumentException::class.java) { data.append(original) }
        data.archive(original.transactionId, "Administrator")
        val restored = reload(data)
        assertThrows(IllegalArgumentException::class.java) { restored.append(original) }
        assertTrue(restored.all().isEmpty())
    }

    @Test fun `unreadable audit records survive subsequent saves`() {
        val damaged = CompoundTag().apply { putString("future-format", "preserve evidence") }
        val root = CompoundTag().apply { put("transactions", ListTag().apply { add(damaged) }) }
        val data = TransactionHistoryData.load(root, registries)
        data.append(record())
        val saved = data.save(CompoundTag(), registries).getList("transactions", 10)
        assertEquals(2, saved.size)
        assertEquals(damaged, saved.getCompound(1))
    }
}
