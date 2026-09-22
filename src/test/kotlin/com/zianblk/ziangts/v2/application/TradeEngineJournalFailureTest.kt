package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TradeEngineJournalFailureTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-22T16:00:00Z"), ZoneOffset.UTC)
    private val payment = PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8)

    @Test fun purchaseDoesNotMutateWhenJournalBeginFails() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val offers = InMemoryOfferBook()
        val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment, p,
            Instant.now(clock), Instant.now(clock).plusSeconds(3600))
        offers.add(offer)
        val economy = CountingEconomy()
        val pokemon = CountingPokemon()
        val engine = TradeEngine(offers, pokemon, economy, InMemoryProceedsStore(), BeginFailJournal(), clock)

        val result = engine.purchase(buyer, offer.id)

        assertTrue(result is TradeResult.Rejected)
        assertNotNull(offers.find(offer.id))
        assertEquals(0, economy.withdrawCalls)
        assertEquals(0, pokemon.deliverCalls)
    }

    @Test fun publishDoesNotRemovePokemonWhenFirstStageFails() {
        val seller = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val pokemon = CountingPokemon(p)
        val engine = TradeEngine(InMemoryOfferBook(), pokemon, CountingEconomy(), InMemoryProceedsStore(), StageFailJournal(), clock)

        val result = engine.publish(seller, "Seller", p.pokemonId, payment)

        assertTrue(result is TradeResult.Quarantined)
        assertEquals(0, pokemon.removeCalls)
    }

    @Test fun claimDoesNotReserveProceedsWhenJournalBeginFails() {
        val owner = UUID.randomUUID()
        val key = ProceedsKey(payment.adapter, payment.currency)
        val proceeds = InMemoryProceedsStore().apply { credit(owner, key, 8) }
        val economy = CountingEconomy()
        val engine = TradeEngine(InMemoryOfferBook(), CountingPokemon(), economy, proceeds, BeginFailJournal(), clock)

        val result = engine.claim(owner, key)

        assertTrue(result is ClaimResult.Rejected)
        assertEquals(8, proceeds.balance(owner, key))
        assertEquals(0, economy.depositCalls)
    }

    private class BeginFailJournal : TradeJournalPort {
        override fun begin(operation: TradeOperation, subjectId: UUID): JournalTicket = error("disk unavailable")
        override fun stage(ticket: JournalTicket, stage: TradeStage) = Unit
        override fun complete(ticket: JournalTicket) = Unit
        override fun abort(ticket: JournalTicket, reason: String) = Unit
        override fun quarantine(ticket: JournalTicket, reason: String) = Unit
    }

    private class StageFailJournal : TradeJournalPort {
        override fun begin(operation: TradeOperation, subjectId: UUID) = JournalTicket(UUID.randomUUID(), operation)
        override fun stage(ticket: JournalTicket, stage: TradeStage) = error("disk unavailable")
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
