package com.zianblk.ziangts.v2.persistence

import com.zianblk.ziangts.v2.port.*
import java.nio.file.*
import java.util.UUID
import java.util.zip.GZIPInputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Assertions.*

class TradeStorageMaintenanceTest {
    @TempDir lateinit var directory:Path
    @Test fun `closed WAL archived losslessly while history file is untouched`() {
        val path=directory.resolve("transactions-v2.wal")
        val history=directory.resolve("history-v2.wal");Files.writeString(history,"untouched history fixture")
        DurableTradeJournal(path).use { journal ->
            repeat(100){val ticket=journal.begin(TradeOperation.PUBLISH,UUID.randomUUID());journal.stage(ticket,TradeStage.RUNTIME_COMPLETE);journal.complete(ticket)}
        }
        val before=Files.readAllBytes(path)
        val result=TradeStorageMaintenance.compactClosedOffline(path)
        assertEquals(0,Files.size(path))
        GZIPInputStream(Files.newInputStream(result.archive)).use { assertArrayEquals(before,it.readAllBytes()) }
        assertEquals("untouched history fixture",Files.readString(history))
        DurableTradeJournal(path).use { assertFalse(it.blocksTrading());val next=it.begin(TradeOperation.PUBLISH,UUID.randomUUID());it.abort(next,"safe test abort") }
    }
    @Test fun `pending transactions are never removed or compacted`() {
        val path=directory.resolve("transactions-v2.wal")
        DurableTradeJournal(path).use { val ticket=it.begin(TradeOperation.PURCHASE,UUID.randomUUID());it.quarantine(ticket,"uncertain payment") }
        val before=Files.readAllBytes(path)
        assertThrows(IllegalStateException::class.java){TradeStorageMaintenance.compactClosedOffline(path)}
        assertArrayEquals(before,Files.readAllBytes(path))
        DurableTradeJournal(path).use { assertTrue(it.blocksTrading());assertEquals(1,it.unresolved().size) }
    }
}
