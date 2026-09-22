package com.zianblk.ziangts.v2.integration

import com.zianblk.ziangts.v2.domain.ProceedsKey
import com.zianblk.ziangts.v2.persistence.DurableMarketStore
import com.zianblk.ziangts.v2.persistence.DurableTradeJournal
import com.zianblk.ziangts.v2.port.TradeStage
import com.zianblk.ziangts.v2.testadapter.DurableFileEconomyPort
import com.zianblk.ziangts.v2.testadapter.DurableFilePokemonPort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class FullPurchaseCrashIntegrationTest {
    @TempDir lateinit var dir: Path

    private fun crashAt(stage: TradeStage) {
        val java = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val p = ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
            FullPurchaseCrashProbe::class.java.name, dir.toString(), stage.name)
            .redirectErrorStream(true).start()
        assertTrue(p.waitFor(20, TimeUnit.SECONDS), "full purchase probe timed out")
        assertEquals(29, p.exitValue())
    }

    @Test fun crashAfterPaymentLeavesDurableEvidenceAndBlocks() {
        crashAt(TradeStage.PAYMENT_APPLIED)
        val market = DurableMarketStore(dir.resolve("market-v2.state"))
        assertNull(market.find(FullPurchaseCrashProbe.offerId), "offer must remain reserved")
        assertEquals(0, market.balance(FullPurchaseCrashProbe.seller, FullPurchaseCrashProbe.key))
        assertEquals(12, DurableFileEconomyPort(dir.resolve("economy.state"))
            .balance(FullPurchaseCrashProbe.buyer, FullPurchaseCrashProbe.key.currency))
        assertFalse(DurableFilePokemonPort(dir.resolve("pokemon.state"))
            .owns(FullPurchaseCrashProbe.buyer, FullPurchaseCrashProbe.pokemonId))
        DurableTradeJournal(dir.resolve("transactions-v2.wal")).use { journal ->
            assertTrue(journal.blocksTrading())
            assertEquals(TradeStage.PAYMENT_APPLIED, journal.unresolved().single().stage)
        }
    }

    @Test fun crashAfterDeliveryPreservesPokemonAndStillBlocks() {
        crashAt(TradeStage.POKEMON_DELIVERED)
        val market = DurableMarketStore(dir.resolve("market-v2.state"))
        assertNull(market.find(FullPurchaseCrashProbe.offerId))
        assertEquals(0, market.balance(FullPurchaseCrashProbe.seller, FullPurchaseCrashProbe.key))
        assertEquals(12, DurableFileEconomyPort(dir.resolve("economy.state"))
            .balance(FullPurchaseCrashProbe.buyer, FullPurchaseCrashProbe.key.currency))
        assertTrue(DurableFilePokemonPort(dir.resolve("pokemon.state"))
            .owns(FullPurchaseCrashProbe.buyer, FullPurchaseCrashProbe.pokemonId))
        DurableTradeJournal(dir.resolve("transactions-v2.wal")).use { journal ->
            assertTrue(journal.blocksTrading())
            assertEquals(TradeStage.POKEMON_DELIVERED, journal.unresolved().single().stage)
        }
    }
}
