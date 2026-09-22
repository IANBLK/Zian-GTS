package com.zianblk.ziangts.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class TransactionJournalTest {
    @TempDir lateinit var directory: Path
    private val actor = UUID.randomUUID()

    @Test fun `runtime completion followed by crash still requires reconciliation`() {
        val path = directory.resolve("transactions.wal")
        val id = TransactionJournal(path).use { journal ->
            val id = journal.begin("buy", actor, "{pokemon:retained,price:5}")
            journal.stage(id, "payment_complete_before_delivery")
            journal.finish(id, "{listings:[],proceeds:5}")
            assertFalse(journal.blocksTrading())
            id
        }
        TransactionJournal(path).use { journal ->
            assertTrue(journal.blocksTrading())
            assertEquals(id, journal.entries().single().id)
            assertEquals("{pokemon:retained,price:5}", journal.entries().single().snapshot)
            assertEquals("{listings:[],proceeds:5}", journal.latestCheckpoint)
            assertThrows(IllegalStateException::class.java) { journal.begin("claim", actor, "{}") }
            assertThrows(IllegalStateException::class.java) { journal.orderlyShutdown("{}") }
        }
    }

    @Test fun `successive orderly restarts retain checkpoint without recovery block`() {
        val path = directory.resolve("transactions.wal")
        repeat(3) {
            TransactionJournal(path).use { journal ->
                assertFalse(journal.blocksTrading())
                val id = journal.begin("sell", actor, "{pokemon:retained}")
                journal.finish(id, "{saved:$it}")
                journal.orderlyShutdown("{saved:$it}")
            }
            TransactionJournal(path).use { journal ->
                assertFalse(journal.blocksTrading())
                assertTrue(journal.entries().isEmpty())
                assertEquals("{saved:$it}", journal.orderlyCheckpoint)
            }
        }
    }

    @Test fun `resolution is durable and never clears another operation`() {
        val path = directory.resolve("transactions.wal")
        val ids = TransactionJournal(path).use { journal ->
            (1..2).map { journal.begin("buy", actor, "snapshot-$it").also { journal.finish(it, "checkpoint") } }
        }
        TransactionJournal(path).use { journal ->
            assertTrue(journal.resolve(ids[0], "admin", actor))
            assertTrue(journal.blocksTrading())
        }
        TransactionJournal(path).use { journal ->
            assertEquals(ids[1], journal.entries().single().id)
            assertFalse(journal.resolve(ids[0], "admin", actor))
            assertTrue(journal.resolve(ids[1], "admin", actor))
            assertFalse(journal.blocksTrading())
        }
        TransactionJournal(path).use { assertFalse(it.blocksTrading()) }
    }

    @Test fun `truncated and corrupted tails block without modifying evidence`() {
        val path = directory.resolve("transactions.wal")
        TransactionJournal(path).use { it.begin("claim", actor, "{amount:5}") }
        val valid = Files.readAllBytes(path)
        for (broken in listOf(valid + byteArrayOf(0, 1), valid.copyOf(valid.size - 1), valid.copyOf().apply { this[10] = (this[10].toInt() xor 1).toByte() })) {
            Files.write(path, broken)
            TransactionJournal(path).use { journal ->
                assertTrue(journal.blocksTrading())
                assertNotNull(journal.fault)
                assertThrows(IllegalStateException::class.java) { journal.begin("buy", actor, "{}") }
            }
            assertArrayEquals(broken, Files.readAllBytes(path))
        }
    }

    @Test fun `second writer and invalid journal path fail closed`() {
        val path = directory.resolve("transactions.wal")
        TransactionJournal(path).use {
            TransactionJournal(path).use { other -> assertTrue(other.blocksTrading()); assertNotNull(other.fault) }
        }
        TransactionJournal(directory).use { assertTrue(it.blocksTrading()); assertNotNull(it.fault) }
    }

    @Test fun `process termination at market mutation boundaries preserves intent`() {
        val classpath = checkNotNull(System.getProperty("ziangts.crashTestClasspath"))
        val boundaries = listOf("prepared", "before_payment", "payment_complete_before_delivery", "delivered_before_credit", "credited_before_history", "runtime_complete").map { "buy" to it } +
            listOf("sell" to "before_party_remove", "sell" to "party_removed", "cancel" to "before_return",
                "claim" to "before_proceeds_debit", "claim" to "before_payout")
        for ((operation, stage) in boundaries) {
            val path = directory.resolve("$operation-$stage.wal")
            val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, JournalCrashProbe::class.java.name, path.toString(), stage, operation)
                .redirectErrorStream(true).redirectOutput(directory.resolve("$stage.log").toFile()).start()
            val exited = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            if (!exited) process.destroyForcibly()
            assertTrue(exited, "Child timed out: $stage")
            assertEquals(23, process.exitValue(), Files.readString(directory.resolve("$stage.log")))
            TransactionJournal(path).use { journal ->
                assertNull(journal.fault)
                assertTrue(journal.blocksTrading())
                assertEquals("{pokemon:retained,price:5}", journal.entries().single().snapshot)
                assertEquals(stage, journal.entries().single().stage)
                assertEquals(operation, journal.entries().single().operation)
            }
        }
    }
    @Test fun `crash integration preserves persisted buy state without duplication`() {
        val classpath = checkNotNull(System.getProperty("ziangts.crashTestClasspath"))
        for (stage in listOf("after_begin", "after_payment", "after_delivery")) {
            val root = directory.resolve("integration-$stage")
            Files.createDirectories(root)
            val journalPath = root.resolve("transactions.wal")
            val statePath = root.resolve("market.state")
            val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, GtsBuyCrashProbe::class.java.name,
                journalPath.toString(), statePath.toString(), stage)
                .redirectErrorStream(true).redirectOutput(root.resolve("probe.log").toFile()).start()
            val exited = process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
            if (!exited) process.destroyForcibly()
            assertTrue(exited, "Child timed out: $stage")
            assertEquals(29, process.exitValue(), Files.readString(root.resolve("probe.log")))

            val state = GtsBuyCrashProbe.readState(statePath)
            when (stage) {
                "after_begin" -> {
                    assertTrue(state.listingPresent)
                    assertEquals(10L, state.buyerBalance)
                    assertEquals(0, state.buyerPokemon)
                    assertEquals(0L, state.sellerProceeds)
                }
                "after_payment" -> {
                    assertFalse(state.listingPresent)
                    assertEquals(5L, state.buyerBalance)
                    assertEquals(0, state.buyerPokemon)
                    assertEquals(0L, state.sellerProceeds)
                }
                "after_delivery" -> {
                    assertFalse(state.listingPresent)
                    assertEquals(5L, state.buyerBalance)
                    assertEquals(1, state.buyerPokemon)
                    assertEquals(0L, state.sellerProceeds)
                }
            }
            assertTrue(state.buyerPokemon in 0..1)
            assertTrue(state.buyerBalance in setOf(10L, 5L))

            TransactionJournal(journalPath).use { journal ->
                assertNull(journal.fault)
                assertTrue(journal.blocksTrading())
                val incident = journal.entries().single()
                assertEquals("buy", incident.operation)
                assertEquals(stage, incident.stage)
                assertTrue(journal.resolve(incident.id, "test-admin", actor))
                assertFalse(journal.blocksTrading())
            }
            assertEquals(state, GtsBuyCrashProbe.readState(statePath),
                "resolve() must not mutate persisted market/economy/Pokémon state")
        }
    }

}

object JournalCrashProbe {
    @JvmStatic fun main(args: Array<String>) {
        val journal = TransactionJournal(Path.of(args[0]))
        val id = journal.begin(args[2], UUID.randomUUID(), "{pokemon:retained,price:5}")
        when (args[1]) {
            "prepared" -> Unit
            "runtime_complete" -> journal.finish(id, "checkpoint")
            else -> journal.stage(id, args[1])
        }
        Runtime.getRuntime().halt(23) // No finally, close, shutdown hook or graceful server event.
    }
}


object GtsBuyCrashProbe {
    data class State(val listingPresent: Boolean, val buyerBalance: Long, val buyerPokemon: Int, val sellerProceeds: Long)

    @JvmStatic fun main(args: Array<String>) {
        val journalPath = Path.of(args[0])
        val statePath = Path.of(args[1])
        val stopAt = args[2]
        var state = State(true, 10L, 0, 0L)
        persist(statePath, state)

        val journal = TransactionJournal(journalPath)
        val id = journal.begin("buy", UUID.randomUUID(), "{listing:true,buyerBalance:10,buyerPokemon:0,sellerProceeds:0}")
        journal.stage(id, "after_begin")
        if (stopAt == "after_begin") Runtime.getRuntime().halt(29)

        state = state.copy(listingPresent = false, buyerBalance = 5L)
        persist(statePath, state)
        journal.stage(id, "after_payment")
        if (stopAt == "after_payment") Runtime.getRuntime().halt(29)

        state = state.copy(buyerPokemon = 1)
        persist(statePath, state)
        journal.stage(id, "after_delivery")
        if (stopAt == "after_delivery") Runtime.getRuntime().halt(29)

        error("Unknown crash boundary: $stopAt")
    }

    fun readState(path: Path): State {
        val parts = Files.readString(path).trim().split('|')
        return State(parts[0].toBooleanStrict(), parts[1].toLong(), parts[2].toInt(), parts[3].toLong())
    }

    private fun persist(path: Path, state: State) {
        val bytes = java.nio.ByteBuffer.wrap(
            "${state.listingPresent}|${state.buyerBalance}|${state.buyerPokemon}|${state.sellerProceeds}\n"
                .toByteArray(Charsets.UTF_8))
        java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING, java.nio.file.StandardOpenOption.WRITE).use { file ->
            while (bytes.hasRemaining()) file.write(bytes)
            file.force(true)
        }
    }
}
