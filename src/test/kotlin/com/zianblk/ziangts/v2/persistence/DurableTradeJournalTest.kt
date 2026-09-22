package com.zianblk.ziangts.v2.persistence

import com.zianblk.ziangts.v2.port.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class DurableTradeJournalTest {
    @TempDir lateinit var dir: Path

    @Test fun completedOperationDoesNotBlockAfterReopen() {
        val path = dir.resolve("transactions-v2.wal")
        DurableTradeJournal(path).use { j ->
            val t = j.begin(TradeOperation.PURCHASE, UUID.randomUUID())
            j.stage(t, TradeStage.BEFORE_PAYMENT)
            j.stage(t, TradeStage.PAYMENT_APPLIED)
            j.complete(t)
            assertFalse(j.blocksTrading())
        }
        DurableTradeJournal(path).use { assertFalse(it.blocksTrading()) }
    }

    @Test fun incompleteOperationSurvivesAndBlocks() {
        val path = dir.resolve("transactions-v2.wal")
        val id: UUID
        DurableTradeJournal(path).use { j ->
            val t = j.begin(TradeOperation.PURCHASE, UUID.randomUUID())
            id = t.operationId
            j.stage(t, TradeStage.PAYMENT_APPLIED)
        }
        DurableTradeJournal(path).use { j ->
            assertTrue(j.blocksTrading())
            assertEquals(id, j.unresolved().single().ticket.operationId)
            assertEquals(TradeStage.PAYMENT_APPLIED, j.unresolved().single().stage)
        }
    }

    @Test fun quarantineSurvivesReopenUntilExplicitResolve() {
        val path = dir.resolve("transactions-v2.wal")
        val id: UUID
        DurableTradeJournal(path).use { j ->
            val t = j.begin(TradeOperation.CLAIM, UUID.randomUUID())
            id = t.operationId
            j.quarantine(t, "timeout")
        }
        DurableTradeJournal(path).use { j ->
            assertTrue(j.unresolved().single().quarantined)
            j.resolve(id, "admin verified external state")
            assertFalse(j.blocksTrading())
        }
        DurableTradeJournal(path).use { assertFalse(it.blocksTrading()) }
    }
}
