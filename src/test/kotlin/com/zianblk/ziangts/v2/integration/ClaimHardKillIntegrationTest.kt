package com.zianblk.ziangts.v2.integration

import com.zianblk.ziangts.v2.persistence.DurableMarketStore
import com.zianblk.ziangts.v2.persistence.DurableTradeJournal
import com.zianblk.ziangts.v2.port.TradeStage
import com.zianblk.ziangts.v2.testadapter.DurableFileEconomyPort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class ClaimHardKillIntegrationTest {
    @TempDir lateinit var dir: Path

    private fun crashAt(stage: TradeStage) {
        val java = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val p = ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
            ClaimCrashProbe::class.java.name, dir.toString(), stage.name)
            .redirectErrorStream(true).start()
        assertTrue(p.waitFor(20, TimeUnit.SECONDS), "claim probe timed out")
        assertEquals(29, p.exitValue())
    }

    @Test fun crashAfterReservationKeepsWalletUnpaidAndBlocks() {
        crashAt(TradeStage.PROCEEDS_RESERVED)
        val market = DurableMarketStore(dir.resolve("market-v2.state"))
        assertEquals(0, market.balance(ClaimCrashProbe.owner, ClaimCrashProbe.key))
        assertEquals(0, DurableFileEconomyPort(dir.resolve("economy.state"))
            .balance(ClaimCrashProbe.owner, ClaimCrashProbe.key.currency))
        DurableTradeJournal(dir.resolve("transactions-v2.wal")).use { journal ->
            assertTrue(journal.blocksTrading())
            assertEquals(TradeStage.PROCEEDS_RESERVED, journal.unresolved().single().stage)
        }
    }

    @Test fun crashAfterDepositKeepsPaidWalletAndBlocksAgainstDuplicateClaim() {
        crashAt(TradeStage.PAYMENT_APPLIED)
        val market = DurableMarketStore(dir.resolve("market-v2.state"))
        assertEquals(0, market.balance(ClaimCrashProbe.owner, ClaimCrashProbe.key))
        assertEquals(13, DurableFileEconomyPort(dir.resolve("economy.state"))
            .balance(ClaimCrashProbe.owner, ClaimCrashProbe.key.currency))
        DurableTradeJournal(dir.resolve("transactions-v2.wal")).use { journal ->
            assertTrue(journal.blocksTrading())
            assertEquals(TradeStage.PAYMENT_APPLIED, journal.unresolved().single().stage)
        }
    }
}
