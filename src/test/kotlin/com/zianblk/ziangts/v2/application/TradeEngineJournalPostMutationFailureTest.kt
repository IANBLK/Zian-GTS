package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TradeEngineJournalPostMutationFailureTest {
    private val now = Instant.parse("2026-09-22T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val payment = PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8)

    @Test fun journalFailureAfterPaymentPropagatesAndDoesNotContinueDelivery() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val offers = InMemoryOfferBook()
        val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment, p, now, now.plusSeconds(3600))
        offers.add(offer)
        val economy = CountingEconomy()
        val pokemon = CountingPokemon()
        val journal = FailOnStageJournal(TradeStage.PAYMENT_APPLIED)
        val engine = TradeEngine(offers, pokemon, economy, InMemoryProceedsStore(), journal, clock)

        assertThrows(IllegalStateException::class.java) { engine.purchase(buyer, offer.id) }

        assertEquals(1, economy.withdrawCalls)
        assertEquals(0, pokemon.deliverCalls, "must stop immediately when durable evidence cannot advance")
        assertNull(offers.find(offer.id), "reserved offer must remain unavailable")
    }

    @Test fun journalFailureAfterPokemonRemovalPropagatesAndDoesNotCreateOffer() {
        val seller = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val offers = InMemoryOfferBook()
        val pokemon = CountingPokemon(p)
        val journal = FailOnStageJournal(TradeStage.POKEMON_REMOVED)
        val engine = TradeEngine(offers, pokemon, CountingEconomy(), InMemoryProceedsStore(), journal, clock)

        assertThrows(IllegalStateException::class.java) { engine.publish(seller, "Seller", p.pokemonId, payment) }

        assertEquals(1, pokemon.removeCalls)
        assertTrue(offers.all().isEmpty(), "offer must not be created after journal persistence failure")
    }

    @Test fun journalFailureAfterClaimReservationPropagatesAndDoesNotDeposit() {
        val owner = UUID.randomUUID()
        val key = ProceedsKey(payment.adapter, payment.currency)
        val proceeds = InMemoryProceedsStore().apply { credit(owner, key, 8) }
        val economy = CountingEconomy()
        val journal = FailOnStageJournal(TradeStage.PROCEEDS_RESERVED)
        val engine = TradeEngine(InMemoryOfferBook(), CountingPokemon(), economy, proceeds, journal, clock)

        assertThrows(IllegalStateException::class.java) { engine.claim(owner, key) }

        assertEquals(0, proceeds.balance(owner, key))
        assertEquals(0, economy.depositCalls, "must not pay seller when reservation stage was not durably recorded")
    }

    private class FailOnStageJournal(private val failStage: TradeStage) : TradeJournalPort {
        private var ticket: JournalTicket? = null
        override fun begin(operation: TradeOperation, subjectId: UUID): JournalTicket =
            JournalTicket(UUID.randomUUID(), operation).also { ticket = it }
        override fun stage(ticket: JournalTicket, stage: TradeStage) {
            check(ticket == this.ticket)
            if (stage == failStage) error("simulated journal write failure at $stage")
        }
        override fun complete(ticket: JournalTicket) = Unit
        override fun abort(ticket: JournalTicket, reason: String) = Unit
        override fun quarantine(ticket: JournalTicket, reason: String) = Unit
    }

    private class CountingEconomy : EconomyPort {
        var withdrawCalls = 0
        var depositCalls = 0
        override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = true
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
            withdrawCalls++; return EconomyResult.Applied
        }
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
            depositCalls++; return EconomyResult.Applied
        }
    }

    private class CountingPokemon(private val inspected: PokemonEnvelope? = null) : PokemonPort {
        var removeCalls = 0
        var deliverCalls = 0
        override fun inspectOwned(playerId: UUID, pokemonId: UUID) = inspected
        override fun removeOwned(operationId: UUID, playerId: UUID, pokemonId: UUID): PokemonMutation {
            removeCalls++; return PokemonMutation.Applied
        }
        override fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope): PokemonMutation {
            deliverCalls++; return PokemonMutation.Applied
        }
        override fun owns(playerId: UUID, pokemonId: UUID) = false
    }
}
