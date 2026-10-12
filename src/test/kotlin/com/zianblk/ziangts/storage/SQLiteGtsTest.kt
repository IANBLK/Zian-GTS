package com.zianblk.ziangts.storage

import com.zianblk.ziangts.v2.persistence.*
import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import java.nio.file.*
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*

class SQLiteGtsTest {
    @TempDir lateinit var world: Path
    @AfterEach fun close() { StorageFiles.closeAll() }
    private fun record(seller:UUID,buyer:UUID,days:Long)=TradeHistoryRecord(
        UUID.randomUUID(),OfferId(UUID.randomUUID()),seller,buyer,UUID.randomUUID(),"ditto",
        PaymentSpec("avecoins_wallet","avecoins:coppercoin",5),Instant.now().minusSeconds(days*86400))
    private fun migrate(rows:List<TradeHistoryRecord>) {
        val root=world.resolve("ziangts-v2")
        DurableHistoryStore(root.resolve("history-v2.wal")).use { history -> rows.forEach(history::append) }
        DurableTradeJournal(root.resolve("transactions-v2.wal")).close()
        StorageMaintenance.migrateOffline(world)
        Files.move(root,world.resolve("originals"))
    }
    private fun ownersInactive(vararg ids:UUID) {
        ids.forEach { StorageFiles.touchOwner(world,it) }
        StorageFiles.connect(world.resolve("zian-storage/gts.db")).use { connection ->
            connection.prepareStatement("update owners set last_seen=? where id=?").use { query ->
                ids.forEach { query.setLong(1,System.currentTimeMillis()-200L*86400000);query.setString(2,it.toString());query.addBatch() }
                query.executeBatch()
            }
        }
        StorageFiles.closeAll()
    }
    @Test fun `old inactive details can be removed but receipt blocks duplicate after restart`() {
        val seller=UUID.randomUUID();val buyer=UUID.randomUUID();val active=UUID.randomUUID()
        val old=record(seller,buyer,200);val recent=record(seller,buyer,1);val protected=record(seller,active,200)
        migrate(listOf(old,recent,protected));ownersInactive(seller,buyer)
        StorageFiles.touchOwner(world,active);StorageFiles.closeAll()
        assertEquals(1,HistoryRetention.pruneOffline(world,false).eligible)
        assertEquals(1,HistoryRetention.pruneOffline(world,true).eligible)
        val path=world.resolve("ziangts-v2/history-v2.wal")
        DurableHistoryStore(path).use {
            assertEquals(listOf(recent,protected),it.all())
            assertThrows(IllegalArgumentException::class.java){it.append(old)}
        }
        StorageFiles.closeAll()
        // A second cleanup must be able to rebuild retained rows that already have a receipt.
        ownersInactive(active)
        assertEquals(1,HistoryRetention.pruneOffline(world,true).eligible)
        DurableHistoryStore(path).use { assertEquals(listOf(recent),it.all());assertThrows(IllegalArgumentException::class.java){it.append(protected)} }
        assertFalse(Files.exists(path))
        StorageFiles.closeAll()
        StorageFiles.connect(world.resolve("zian-storage/gts.db")).use { db ->
            db.createStatement().use { it.executeUpdate("update documents set modified=0 where name like 'ziangts-v2/history-retention.archive-%.wal.gz'") }
        }
        val cleaned=StorageMaintenance.cleanupOffline(world,true)
        assertEquals(2,cleaned.retiredHistoryArchives())
        DurableHistoryStore(path).use { assertThrows(IllegalArgumentException::class.java){it.append(old)} }

    }
    @Test fun `recent Minecraft save protects a session with stale login tracking`() {
        val seller=UUID.randomUUID();val buyer=UUID.randomUUID();val old=record(seller,buyer,200)
        migrate(listOf(old));ownersInactive(seller,buyer)
        Files.createDirectories(world.resolve("playerdata"));Files.writeString(world.resolve("playerdata/$buyer.dat"),"recent test autosave")
        assertEquals(0,HistoryRetention.pruneOffline(world,true).eligible)
    }
    @Test fun `pending transaction and unclaimed proceeds protect historical evidence`() {
        val seller=UUID.randomUUID();val buyer=UUID.randomUUID();val old=record(seller,buyer,200)
        migrate(listOf(old));ownersInactive(seller,buyer)
        val market=DurableMarketStore(world.resolve("ziangts-v2/market-v2.state"))
        market.credit(seller,ProceedsKey("avecoins_wallet","avecoins:coppercoin"),10)
        StorageFiles.closeAll()
        assertEquals(0,HistoryRetention.pruneOffline(world,true).eligible)
        DurableTradeJournal(world.resolve("ziangts-v2/transactions-v2.wal")).use { val ticket=it.begin(TradeOperation.PURCHASE,UUID.randomUUID());it.quarantine(ticket,"uncertain wallet result") }
        StorageFiles.closeAll()
        assertThrows(IllegalStateException::class.java){HistoryRetention.pruneOffline(world,true)}
        DurableHistoryStore(world.resolve("ziangts-v2/history-v2.wal")).use { assertEquals(listOf(old),it.all()) }
        assertEquals(10,DurableMarketStore(world.resolve("ziangts-v2/market-v2.state")).balance(seller,ProceedsKey("avecoins_wallet","avecoins:coppercoin")))
    }
}
