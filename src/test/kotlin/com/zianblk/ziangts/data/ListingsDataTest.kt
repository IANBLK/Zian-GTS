package com.zianblk.ziangts.data

import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class ListingsDataTest {
    @Test fun `incident diagnostics cannot modify stored snapshots`() {
        val data = ListingsData(RegistryAccess.EMPTY)
        val actor = UUID.randomUUID()
        val snapshot = CompoundTag().apply { putLong("amount", 5) }
        data.quarantineTransfer("claim", actor, snapshot)
        snapshot.putLong("amount", 10)
        val exposed = data.transferIncidents().single()
        assertTrue(exposed.hasUUID("incidentId"))
        exposed.getCompound("listing").putLong("amount", 99)
        assertEquals(5L, data.transferIncidents().single().getCompound("listing").getLong("amount"))
        assertTrue(data.hasUnreadableData())
    }
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

    @Test fun `resolving an incident unblocks trading and keeps an audit copy across reload`() {
        val snapshot = CompoundTag().apply { putString("pokemon", "retained snapshot") }
        val data = ListingsData(registries)
        data.quarantineTransfer("buy", seller, snapshot)
        val incidentId = data.transferIncidents().single().getUUID("incidentId")
        assertTrue(data.hasUnreadableData())
        assertFalse(data.resolveIncident(UUID.randomUUID(), "admin"))
        assertTrue(data.hasUnreadableData())
        val adminId = UUID.randomUUID()
        assertTrue(data.resolveIncident(incidentId, "admin", now = 1234L, resolvedById = adminId))
        assertFalse(data.hasUnreadableData())
        assertTrue(data.transferIncidents().isEmpty())
        val reloaded = ListingsData.load(data.save(CompoundTag(), registries), registries)
        assertFalse(reloaded.hasUnreadableData())
        val archived = reloaded.resolvedIncidents().single()
        assertEquals(incidentId, archived.getUUID("incidentId"))
        assertEquals("admin", archived.getString("resolvedBy"))
        assertEquals(adminId, archived.getUUID("resolvedById"))
        assertEquals(1234L, archived.getLong("resolvedAt"))
        assertFalse(reloaded.resolveIncident(incidentId, "admin"))
        assertEquals(snapshot, archived.getCompound("listing"))
    }

    @Test fun `resolving an incident does not clear other blocking data`() {
        val invalid = CompoundTag().apply { putString("unknown-format", "do not lose this Pokemon") }
        val root = CompoundTag().apply { put("listings", ListTag().apply { add(invalid) }) }
        val data = ListingsData.load(root, registries)
        data.quarantineTransfer("buy", seller, CompoundTag())
        val incidentId = data.transferIncidents().single().getUUID("incidentId")
        assertTrue(data.resolveIncident(incidentId, "admin"))
        assertTrue(data.hasUnreadableData())
        assertEquals(invalid, data.save(CompoundTag(), registries).getList("listings", 10).getCompound(0))
    }

    @Test fun `resolving one incident retains the block from another`() {
        val data = ListingsData(registries)
        data.quarantineTransfer("buy_credit", seller, CompoundTag())
        data.quarantineTransfer("buy_history", seller, CompoundTag())
        assertTrue(data.resolveIncident(data.transferIncidents().first().getUUID("incidentId"), "admin"))
        val reloaded = ListingsData.load(data.save(CompoundTag(), registries), registries)
        assertTrue(reloaded.hasUnreadableData())
        assertEquals(1, reloaded.transferIncidents().size)
        assertEquals(1, reloaded.resolvedIncidents().size)
    }

    @Test fun `post delivery incidents preserve complete transaction for reconciliation`() {
        val record = TransactionRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "buyer",
            seller, "seller", 5L, "avecoins:coppercoin", "{pokemon:snapshot}", java.time.Instant.ofEpochMilli(1234))
        for (operation in listOf("buy_credit", "buy_history")) {
            val data = ListingsData(registries)
            data.quarantineTransfer(operation, record.buyerId, CompoundTag().apply { put("transaction", record.toNbt()) })
            val reloaded = ListingsData.load(data.save(CompoundTag(), registries), registries)
            val incident = reloaded.transferIncidents().single()
            assertTrue(reloaded.hasUnreadableData())
            assertEquals(record.toNbt(), incident.getCompound("listing").getCompound("transaction"))
            assertEquals(record.transactionId, incident.getCompound("listing").getCompound("transaction").getUUID("transactionId"))
            assertEquals(record.buyerId, incident.getUUID("actor"))
        }
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
