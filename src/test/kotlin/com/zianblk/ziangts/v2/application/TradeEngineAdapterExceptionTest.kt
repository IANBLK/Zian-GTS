package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TradeEngineAdapterExceptionTest {
    private val now = Instant.parse("2026-09-22T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val payment = PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8)

    @Test fun purchaseQuarantinesWhenEconomyThrowsDuringWithdraw() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val pokemon = UUID.randomUUID()
        val offers = InMemoryOfferBook()
        val offer = offer(seller, pokemon)
        offers.add(offer)
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, NoopPokemon(), ThrowingEconomy(withdraw = true), InMemoryProceedsStore(), journal, clock)

        val result = engine.purchase(buyer, offer.id)

        assertTrue(result is TradeResult.Quarantined)
        assertNull(offers.find(offer.id), "reserved offer must stay unavailable until recovery")
        assertTrue(journal.events.any { it.value.startsWith("quarantine:") })
    }

    @Test fun purchaseQuarantinesWhenPokemonThrowsDuringDelivery() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val pokemon = UUID.randomUUID()
        val offers = InMemoryOfferBook()
        val offer = offer(seller, pokemon)
        offers.add(offer)
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, ThrowingPokemon(deliver = true), AppliedEconomy(), InMemoryProceedsStore(), journal, clock)

        val result = engine.purchase(buyer, offer.id)

        assertTrue(result is TradeResult.Quarantined)
        assertNull(offers.find(offer.id))
        assertTrue(journal.events.any { it.value == "stage:PAYMENT_APPLIED" })
        assertTrue(journal.events.any { it.value.startsWith("quarantine:") })
    }

    @Test fun publishQuarantinesWhenPokemonThrowsDuringRemoval() {
        val seller = UUID.randomUUID()
        val pokemonId = UUID.randomUUID()
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(InMemoryOfferBook(), ThrowingPokemon(remove = true), AppliedEconomy(), InMemoryProceedsStore(), journal, clock)

        val result = engine.publish(seller, "Seller", pokemonId, payment)

        assertTrue(result is TradeResult.Quarantined)
        assertTrue(journal.events.any { it.value.startsWith("quarantine:") })
    }

    @Test fun withdrawQuarantinesWhenPokemonThrowsDuringReturn() {
        val seller = UUID.randomUUID()
        val pokemonId = UUID.randomUUID()
        val offers = InMemoryOfferBook()
        val offer = offer(seller, pokemonId)
        offers.add(offer)
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, ThrowingPokemon(deliver = true), AppliedEconomy(), InMemoryProceedsStore(), journal, clock)

        val result = engine.withdraw(seller, offer.id)

        assertTrue(result is TradeResult.Quarantined)
        assertNull(offers.find(offer.id))
        assertTrue(journal.events.any { it.value.startsWith("quarantine:") })
    }

    @Test fun claimQuarantinesWhenEconomyThrowsAfterReservation() {
        val owner = UUID.randomUUID()
        val key = ProceedsKey(payment.adapter, payment.currency)
        val proceeds = InMemoryProceedsStore().apply { credit(owner, key, 8) }
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(InMemoryOfferBook(), NoopPokemon(), ThrowingEconomy(deposit = true), proceeds, journal, clock)

        val result = engine.claim(owner, key)

        assertTrue(result is ClaimResult.Quarantined)
        assertEquals(0, proceeds.balance(owner, key), "reserved proceeds must not be restored on uncertain payout")
        assertTrue(journal.events.any { it.value == "stage:PROCEEDS_RESERVED" })
        assertTrue(journal.events.any { it.value.startsWith("quarantine:") })
    }

    private fun offer(seller: UUID, pokemonId: UUID) = TradeOffer(
        OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment,
        PokemonEnvelope(pokemonId, "cobblemon:gimmighoul", 17, false, false, "{test:true}"),
        now, now.plusSeconds(3600)
    )

    private open class NoopPokemon : PokemonPort {
        override fun inspectOwned(playerId: UUID, pokemonId: UUID) =
            PokemonEnvelope(pokemonId, "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        override fun removeOwned(operationId: UUID, playerId: UUID, pokemonId: UUID): PokemonMutation = PokemonMutation.Applied
        override fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope): PokemonMutation = PokemonMutation.Applied
        override fun owns(playerId: UUID, pokemonId: UUID) = false
    }

    private class ThrowingPokemon(
        private val remove: Boolean = false,
        private val deliver: Boolean = false
    ) : NoopPokemon() {
        override fun removeOwned(operationId: UUID, playerId: UUID, pokemonId: UUID): PokemonMutation {
            if (remove) error("simulated remove failure")
            return PokemonMutation.Applied
        }
        override fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope): PokemonMutation {
            if (deliver) error("simulated delivery failure")
            return PokemonMutation.Applied
        }
    }

    private open class AppliedEconomy : EconomyPort {
        override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = true
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult = EconomyResult.Applied
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult = EconomyResult.Applied
    }

    private class ThrowingEconomy(
        private val withdraw: Boolean = false,
        private val deposit: Boolean = false
    ) : AppliedEconomy() {
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
            if (withdraw) error("simulated withdraw failure")
            return EconomyResult.Applied
        }
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
            if (deposit) error("simulated deposit failure")
            return EconomyResult.Applied
        }
    }
}
