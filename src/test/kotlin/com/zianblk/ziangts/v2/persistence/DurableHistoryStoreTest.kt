package com.zianblk.ziangts.v2.persistence

import com.zianblk.ziangts.v2.domain.OfferId
import com.zianblk.ziangts.v2.domain.PaymentSpec
import com.zianblk.ziangts.v2.port.TradeHistoryRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class DurableHistoryStoreTest {
    @TempDir lateinit var dir: Path

    @Test fun failedOpenPreservesEvidenceAndReleasesFileForVerifiedRepair() {
        val file = dir.resolve("broken-history.wal")
        val bytes = byteArrayOf(1)
        Files.write(file, bytes)
        assertThrows(IllegalArgumentException::class.java) { DurableHistoryStore(file) }
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, Files.readAllBytes(file))
        Files.write(file, byteArrayOf())
        DurableHistoryStore(file).use { assertEquals(emptyList<TradeHistoryRecord>(), it.all()) }
    }

    @Test fun appendedHistorySurvivesReopen() {
        val file = dir.resolve("history-v2.wal")
        val record = record()
        DurableHistoryStore(file).use { it.append(record) }
        DurableHistoryStore(file).use { assertEquals(listOf(record), it.all()) }
    }

    @Test fun corruptedHistoryFailsClosedOnReplay() {
        val file = dir.resolve("history-v2.wal")
        DurableHistoryStore(file).use { it.append(record()) }
        val bytes = Files.readAllBytes(file)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(file, bytes)
        assertThrows(IllegalArgumentException::class.java) { DurableHistoryStore(file) }
    }

    @Test fun duplicateOperationRejectedBeforeWriteAndAfterReopen() {
        val file=dir.resolve("indexed-history.wal");val record=record()
        DurableHistoryStore(file).use { store ->
            store.append(record);val size=Files.size(file)
            assertThrows(IllegalArgumentException::class.java){store.append(record)}
            assertEquals(size,Files.size(file));assertEquals(listOf(record),store.forPlayer(record.sellerId,20))
        }
        DurableHistoryStore(file).use { store ->
            val size=Files.size(file)
            assertThrows(IllegalArgumentException::class.java){store.append(record)}
            assertEquals(size,Files.size(file));assertEquals(listOf(record),store.forPlayer(record.buyerId,20))
        }
    }
    @Test fun indexedHistoryReplaysLargeSetWithoutDroppingRecords() {
        val file=dir.resolve("large-history.wal");val baseline=record()
        val entries=(0 until 10000).map { baseline.copy(operationId=UUID(0,it.toLong()+1)) }
        DurableHistoryStore(file).use { store -> entries.forEach(store::append) }
        DurableHistoryStore(file).use { store ->
            assertEquals(entries,store.all());assertEquals(entries.takeLast(20).reversed(),store.forPlayer(baseline.sellerId,20))
            assertThrows(IllegalArgumentException::class.java){store.append(entries.first())}
        }
    }
    private fun record() = TradeHistoryRecord(
        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
        OfferId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")),
        UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
        UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
        UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
        "cobblemon:gimmighoul",
        PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8),
        Instant.parse("2026-09-22T17:00:00Z")
    )
}
