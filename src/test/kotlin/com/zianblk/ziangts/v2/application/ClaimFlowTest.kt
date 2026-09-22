package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import com.zianblk.ziangts.v2.testadapter.DurableFilePokemonPort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.util.UUID

class ClaimFlowTest {
    @TempDir lateinit var dir: Path
    private val key = ProceedsKey("avecoins_wallet", "avecoins:coppercoin")

    @Test fun successfulClaimMovesEntireBalanceToEconomy() {
        val owner = UUID.randomUUID()
        val proceeds = InMemoryProceedsStore().apply { credit(owner, key, 13) }
        val economy = ClaimEconomy()
        val journal = RecordingTradeJournal()
        val engine = engine(economy, proceeds, journal)

        val result = engine.claim(owner, key)

        assertEquals(ClaimResult.Success(13, key), result)
        assertEquals(0, proceeds.balance(owner, key))
        assertEquals(13, economy.balances[owner])
        assertTrue(journal.events.any { it.value == "complete" })
    }

    @Test fun rejectedDepositRestoresReservedProceeds() {
        val owner = UUID.randomUUID()
        val proceeds = InMemoryProceedsStore().apply { credit(owner, key, 8) }
        val economy = ClaimEconomy(result = EconomyResult.Rejected("wallet rejected"))
        val journal = RecordingTradeJournal()
        val result = engine(economy, proceeds, journal).claim(owner, key)

        assertTrue(result is ClaimResult.Rejected)
        assertEquals(8, proceeds.balance(owner, key))
        assertEquals(0, economy.balances[owner] ?: 0)
        assertTrue(journal.events.any { it.value.startsWith("abort:") })
        assertTrue(journal.quarantined.isEmpty())
    }

    @Test fun uncertainDepositDoesNotRestoreProceeds() {
        val owner = UUID.randomUUID()
        val proceeds = InMemoryProceedsStore().apply { credit(owner, key, 8) }
        val economy = ClaimEconomy(result = EconomyResult.Uncertain("timeout"))
        val journal = RecordingTradeJournal()
        val result = engine(economy, proceeds, journal).claim(owner, key)

        assertTrue(result is ClaimResult.Quarantined)
        assertEquals(0, proceeds.balance(owner, key))
        assertTrue(journal.quarantined.isNotEmpty())
    }

    private fun engine(economy: EconomyPort, proceeds: ProceedsStore, journal: TradeJournalPort) =
        TradeEngine(InMemoryOfferBook(), DurableFilePokemonPort(dir.resolve("pokemon.state")),
            economy, proceeds, journal, Clock.systemUTC())

    private class ClaimEconomy(private val result: EconomyResult = EconomyResult.Applied) : EconomyPort {
        val balances = mutableMapOf<UUID, Long>()
        override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = true
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long) = EconomyResult.Applied
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
            if (result == EconomyResult.Applied)
                balances[playerId] = (balances[playerId] ?: 0) + amount
            return result
        }
    }
}
