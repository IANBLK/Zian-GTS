package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TradeEngineJournalTerminalFailureTest {
    private val now = Instant.parse("2026-09-22T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val payment = PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8)

    @Test fun purchaseCompleteFailurePropagatesAfterAllMutations() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val offer = offer(seller)
        val offers = InMemoryOfferBook().apply { add(offer) }
        val journal = TerminalFailJournal(failComplete = true)
        val proceeds = InMemoryProceedsStore()
        val engine = TradeEngine(offers, AppliedPokemon(), AppliedEconomy(), proceeds, journal, clock)

        assertThrows(IllegalStateException::class.java) { engine.purchase(buyer, offer.id) }

        assertNull(offers.find(offer.id))
        assertEquals(8, proceeds.balance(seller, ProceedsKey(payment.adapter, payment.currency)))
        assertTrue(journal.stages.contains(TradeStage.RUNTIME_COMPLETE))
    }

    @Test fun purchaseAbortFailurePropagatesAndDoesNotCharge() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val offer = offer(seller)
        val offers = InMemoryOfferBook().apply { add(offer) }
        val journal = TerminalFailJournal(failAbort = true)
        val economy = RejectingWithdrawEconomy()
        val engine = TradeEngine(offers, AppliedPokemon(), economy, InMemoryProceedsStore(), journal, clock)

        assertThrows(IllegalStateException::class.java) { engine.purchase(buyer, offer.id) }

        assertNotNull(offers.find(offer.id), "offer was safely restored before abort persistence failed")
        assertEquals(1, economy.withdrawCalls)
    }

    @Test fun purchaseQuarantineFailurePropagatesAndKeepsOfferReserved() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val offer = offer(seller)
        val offers = InMemoryOfferBook().apply { add(offer) }
        val journal = TerminalFailJournal(failQuarantine = true)
        val engine = TradeEngine(offers, AppliedPokemon(), ThrowingWithdrawEconomy(),
            InMemoryProceedsStore(), journal, clock)

        assertThrows(IllegalStateException::class.java) { engine.purchase(buyer, offer.id) }

        assertNull(offers.find(offer.id), "uncertain payment must keep offer unavailable")
    }

    private fun offer(seller: UUID) = TradeOffer(
        OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment,
        PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}"),
        now, now.plusSeconds(3600)
    )

    private class TerminalFailJournal(
        private val failComplete: Boolean = false,
        private val failAbort: Boolean = false,
        private val failQuarantine: Boolean = false
    ) : TradeJournalPort {
        val stages = mutableListOf<TradeStage>()
        override fun begin(operation: TradeOperation, subjectId: UUID) = JournalTicket(UUID.randomUUID(), operation)
        override fun stage(ticket: JournalTicket, stage: TradeStage) { stages += stage }
        override fun complete(ticket: JournalTicket) {
            if (failComplete) error("simulated complete persistence failure")
        }
        override fun abort(ticket: JournalTicket, reason: String) {
            if (failAbort) error("simulated abort persistence failure")
        }
        override fun quarantine(ticket: JournalTicket, reason: String) {
            if (failQuarantine) error("simulated quarantine persistence failure")
        }
    }

    private class AppliedPokemon : PokemonPort {
        override fun inspectOwned(playerId: UUID, pokemonId: UUID): PokemonEnvelope? = null
        override fun removeOwned(operationId: UUID, playerId: UUID, pokemonId: UUID): PokemonMutation = PokemonMutation.Applied
        override fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope): PokemonMutation = PokemonMutation.Applied
        override fun owns(playerId: UUID, pokemonId: UUID) = false
    }

    private open class AppliedEconomy : EconomyPort {
        override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = true
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult = EconomyResult.Applied
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult = EconomyResult.Applied
    }

    private class RejectingWithdrawEconomy : AppliedEconomy() {
        var withdrawCalls = 0
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
            withdrawCalls++
            return EconomyResult.Rejected("wallet rejected")
        }
    }

    private class ThrowingWithdrawEconomy : AppliedEconomy() {
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult =
            error("simulated uncertain wallet failure")
    }
}
