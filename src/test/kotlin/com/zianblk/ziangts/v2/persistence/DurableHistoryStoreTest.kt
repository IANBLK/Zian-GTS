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
