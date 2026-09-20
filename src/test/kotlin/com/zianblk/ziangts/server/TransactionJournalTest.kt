package com.zianblk.ziangts.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
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

    @Test fun `process termination at each purchase boundary preserves intent`() {
        val classpath = listOf(JournalCrashProbe::class.java, TransactionJournal::class.java,
            com.google.gson.Gson::class.java, kotlin.Unit::class.java).map {
            Path.of(it.protectionDomain.codeSource.location.toURI()).toString()
        }.distinct().joinToString(java.io.File.pathSeparator)
        for (stage in listOf("prepared", "before_payment", "payment_complete_before_delivery", "delivered_before_credit", "credited_before_history", "runtime_complete")) {
            val path = directory.resolve("$stage.wal")
            val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, JournalCrashProbe::class.java.name, path.toString(), stage)
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
            }
        }
    }
}

object JournalCrashProbe {
    @JvmStatic fun main(args: Array<String>) {
        val journal = TransactionJournal(Path.of(args[0]))
        val id = journal.begin("buy", UUID.randomUUID(), "{pokemon:retained,price:5}")
        when (args[1]) {
            "prepared" -> Unit
            "runtime_complete" -> journal.finish(id, "checkpoint")
            else -> journal.stage(id, args[1])
        }
        Runtime.getRuntime().halt(23) // No finally, close, shutdown hook or graceful server event.
    }
}
