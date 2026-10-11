package com.zianblk.ziangts.v2.integration

import com.zianblk.ziangts.v2.domain.ProceedsKey
import com.zianblk.ziangts.v2.persistence.DurableMarketStore
import com.zianblk.ziangts.v2.persistence.DurableHistoryStore
import com.zianblk.ziangts.v2.persistence.DurableTradeJournal
import com.zianblk.ziangts.v2.port.TradeStage
import com.zianblk.ziangts.v2.testadapter.DurableFileEconomyPort
import com.zianblk.ziangts.v2.testadapter.DurableFilePokemonPort
import com.zianblk.ziangts.v2.testadapter.launchCrashProbe
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class FullPurchaseCrashIntegrationTest {
    @TempDir lateinit var dir: Path

    private fun crashAt(stage: TradeStage) {
        val p = launchCrashProbe(dir, FullPurchaseCrashProbe::class.java, dir.toString(), stage.name)
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

    @Test fun crashAfterProceedsCreditPreservesAllAppliedEffectsAndBlocks() {
        crashAt(TradeStage.PROCEEDS_CREDITED)
        val market = DurableMarketStore(dir.resolve("market-v2.state"))
        assertNull(market.find(FullPurchaseCrashProbe.offerId))
        assertEquals(8, market.balance(FullPurchaseCrashProbe.seller, FullPurchaseCrashProbe.key))
        assertEquals(12, DurableFileEconomyPort(dir.resolve("economy.state"))
            .balance(FullPurchaseCrashProbe.buyer, FullPurchaseCrashProbe.key.currency))
        assertTrue(DurableFilePokemonPort(dir.resolve("pokemon.state"))
            .owns(FullPurchaseCrashProbe.buyer, FullPurchaseCrashProbe.pokemonId))
        DurableTradeJournal(dir.resolve("transactions-v2.wal")).use { journal ->
            assertTrue(journal.blocksTrading())
            assertEquals(TradeStage.PROCEEDS_CREDITED, journal.unresolved().single().stage)
        }
    }

    @Test fun crashAfterHistoryAppendPreservesHistoryAndBlocks() {
        crashAt(TradeStage.HISTORY_APPENDED)
        val market = DurableMarketStore(dir.resolve("market-v2.state"))
        assertNull(market.find(FullPurchaseCrashProbe.offerId))
        assertEquals(8, market.balance(FullPurchaseCrashProbe.seller, FullPurchaseCrashProbe.key))
        assertEquals(12, DurableFileEconomyPort(dir.resolve("economy.state"))
            .balance(FullPurchaseCrashProbe.buyer, FullPurchaseCrashProbe.key.currency))
        assertTrue(DurableFilePokemonPort(dir.resolve("pokemon.state"))
            .owns(FullPurchaseCrashProbe.buyer, FullPurchaseCrashProbe.pokemonId))
        DurableHistoryStore(dir.resolve("history-v2.wal")).use { history ->
            val record = history.all().single()
            assertEquals(FullPurchaseCrashProbe.offerId, record.offerId)
            assertEquals(FullPurchaseCrashProbe.seller, record.sellerId)
            assertEquals(FullPurchaseCrashProbe.buyer, record.buyerId)
            assertEquals(FullPurchaseCrashProbe.pokemonId, record.pokemonId)
        }
        DurableTradeJournal(dir.resolve("transactions-v2.wal")).use { journal ->
            assertTrue(journal.blocksTrading())
            assertEquals(TradeStage.HISTORY_APPENDED, journal.unresolved().single().stage)
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
