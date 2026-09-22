package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class PublishPersistenceFailureTest {
    private val now = Instant.parse("2026-09-22T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val payment = PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8)

    @Test fun offerPersistenceFailureIsQuarantinedAsMarketFailure() {
        val fixture = Fixture()
        val offers = FailingAddOfferBook()
        val journal = ControlledJournal()
        val result = fixture.engine(offers, journal).publish(fixture.seller, "Seller", fixture.pokemon.pokemonId, payment)

        assertTrue(result is TradeResult.Quarantined)
        assertTrue(journal.quarantineReason!!.startsWith("offer persistence failed after pokemon removal"))
        assertFalse(journal.quarantineReason!!.contains("runtime-complete"))
    }

    @Test fun runtimeCompleteStageFailureKeepsDurableOfferAndUsesJournalReason() {
        val fixture = Fixture()
        val offers = InMemoryOfferBook()
        val journal = ControlledJournal(failRuntimeComplete = true)
        val result = fixture.engine(offers, journal).publish(fixture.seller, "Seller", fixture.pokemon.pokemonId, payment)

        assertTrue(result is TradeResult.Quarantined)
        assertEquals(1, offers.all().size, "offer was already persisted and must remain available to recovery")
        assertTrue(journal.quarantineReason!!.startsWith("journal runtime-complete stage failed after offer persistence"))
    }

    @Test fun completeFailurePropagatesWithoutRollingBackDurableOffer() {
        val fixture = Fixture()
        val offers = InMemoryOfferBook()
        val journal = ControlledJournal(failComplete = true)
        assertThrows(IllegalStateException::class.java) {
            fixture.engine(offers, journal).publish(fixture.seller, "Seller", fixture.pokemon.pokemonId, payment)
        }
        assertEquals(1, offers.all().size)
        assertNull(journal.quarantineReason, "complete failure must not be mislabeled as OfferBook persistence failure")
    }

    private inner class Fixture {
        val seller = UUID.randomUUID()
        val pokemon = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        fun engine(offers: OfferBook, journal: TradeJournalPort) =
            TradeEngine(offers, OwnedPokemon(seller, pokemon), NoopEconomy(), InMemoryProceedsStore(), journal, clock)
    }

    private class FailingAddOfferBook : OfferBook {
        override fun find(id: OfferId): TradeOffer? = null
        override fun containsPokemon(pokemonId: UUID) = false
        override fun countOwnedBy(owner: UUID) = 0
        override fun add(offer: TradeOffer) { error("simulated market persistence failure") }
        override fun remove(id: OfferId): TradeOffer? = null
        override fun all(): List<TradeOffer> = emptyList()
    }

    private class ControlledJournal(
        private val failRuntimeComplete: Boolean = false,
        private val failComplete: Boolean = false
    ) : TradeJournalPort {
        var quarantineReason: String? = null
        override fun begin(operation: TradeOperation, subjectId: UUID) = JournalTicket(UUID.randomUUID(), operation)
        override fun stage(ticket: JournalTicket, stage: TradeStage) {
            if (failRuntimeComplete && stage == TradeStage.RUNTIME_COMPLETE) error("simulated runtime-complete failure")
        }
        override fun complete(ticket: JournalTicket) {
            if (failComplete) error("simulated complete failure")
        }
        override fun abort(ticket: JournalTicket, reason: String) = Unit
        override fun quarantine(ticket: JournalTicket, reason: String) { quarantineReason = reason }
    }

    private class OwnedPokemon(private val owner: UUID, private val envelope: PokemonEnvelope) : PokemonPort {
        override fun inspectOwned(playerId: UUID, pokemonId: UUID) =
            if (playerId == owner && pokemonId == envelope.pokemonId) envelope else null
        override fun removeOwned(operationId: UUID, playerId: UUID, pokemonId: UUID) = PokemonMutation.Applied
        override fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope) = PokemonMutation.Applied
        override fun owns(playerId: UUID, pokemonId: UUID) = playerId == owner && pokemonId == envelope.pokemonId
    }

    private class NoopEconomy : EconomyPort {
        override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = true
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult = EconomyResult.Applied
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult = EconomyResult.Applied
    }
}
