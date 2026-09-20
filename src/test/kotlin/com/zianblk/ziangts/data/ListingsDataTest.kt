package com.zianblk.ziangts.data

import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class ListingsDataTest {
    private val seller = UUID.randomUUID()
    private val registries = RegistryAccess.EMPTY

    @Test fun `pending earnings survive save and reload including partial claims`() {
        val data = ListingsData(registries)
        data.credit(seller, "minecraft:diamond", 100)
        data.credit(seller, "minecraft:emerald", 5)
        data.credit(seller, "avecoins_wallet|avecoins:goldcoin", 7)
        data.credit(seller, "avecoins:goldcoin", 3)
        data.debit(seller, "minecraft:diamond", 64)
        val restored = ListingsData.load(data.save(CompoundTag(), registries), registries)
        assertEquals(36L, restored.proceeds(seller, "minecraft:diamond"))
        assertEquals(5L, restored.proceeds(seller, "minecraft:emerald"))
        assertEquals(7L, restored.proceeds(seller, "avecoins_wallet|avecoins:goldcoin"))
        assertEquals(3L, restored.proceeds(seller, "avecoins:goldcoin"))
        assertFalse(restored.hasUnreadableData())
        restored.debit(seller, "minecraft:diamond", 36)
        assertFalse(restored.proceeds(seller).containsKey("minecraft:diamond"))
    }

    @Test fun `overflow and overdraft cannot change a balance`() {
        val data = ListingsData(registries)
        data.credit(seller, "minecraft:diamond", Long.MAX_VALUE)
        assertThrows(ArithmeticException::class.java) { data.credit(seller, "minecraft:diamond", 1) }
        assertEquals(Long.MAX_VALUE, data.proceeds(seller, "minecraft:diamond"))
        assertThrows(IllegalArgumentException::class.java) { data.debit(seller, "minecraft:emerald", 1) }
        assertThrows(IllegalArgumentException::class.java) { data.credit(seller, "minecraft:diamond", -1) }
        assertEquals(Long.MAX_VALUE, data.proceeds(seller, "minecraft:diamond"))
    }

    @Test fun `unreadable records are preserved instead of erased by a later save`() {
        val invalid = CompoundTag().apply { putString("unknown-format", "do not lose this Pokemon") }
        val root = CompoundTag().apply { put("listings", ListTag().apply { add(invalid) }) }
        val data = ListingsData.load(root, registries)
        assertTrue(data.hasUnreadableData())
        val output = data.save(CompoundTag(), registries)
        assertEquals(invalid, output.getList("listings", 10).getCompound(0))
    }

    @Test fun `duplicate payout records are quarantined without doubling the spendable balance`() {
        val entry = CompoundTag().apply {
            putUUID("seller", seller); putString("currency", "minecraft:diamond"); putLong("amount", 10)
        }
        val root = CompoundTag().apply { put("proceeds", ListTag().apply { add(entry); add(entry.copy()) }) }
        val data = ListingsData.load(root, registries)
        assertTrue(data.hasUnreadableData())
        assertEquals(10L, data.proceeds(seller, "minecraft:diamond"))
        assertEquals(2, data.save(CompoundTag(), registries).getList("proceeds", 10).size)
    }

    @Test fun `unexpected root types are retained verbatim and block trading`() {
        val root = CompoundTag().apply { putString("listings", "unexpected external format") }
        val data = ListingsData.load(root, registries)
        assertTrue(data.hasUnreadableData())
        assertEquals(root, data.save(CompoundTag(), registries))
    }

    @Test fun `uncertain transfer snapshots survive restart and block trading`() {
        val snapshot = CompoundTag().apply { putString("pokemon", "retained snapshot") }
        val data = ListingsData(registries)
        data.quarantineTransfer("buy", seller, snapshot)
        val saved = data.save(CompoundTag(), registries)
        val reloaded = ListingsData.load(saved, registries)
        assertTrue(reloaded.hasUnreadableData())
        assertEquals(snapshot, reloaded.save(CompoundTag(), registries)
            .getList("failedTransfers", 10).getCompound(0).getCompound("listing"))
    }
}
