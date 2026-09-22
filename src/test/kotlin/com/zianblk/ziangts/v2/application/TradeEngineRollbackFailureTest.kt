package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TradeEngineRollbackFailureTest {
    private val now = Instant.parse("2026-09-22T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val payment = PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8)

    @Test fun purchaseQuarantinesWhenOfferRestorationFails() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val offer = offer(seller)
        val offers = FailingRestoreOfferBook(offer)
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, NoopPokemon(), RejectingWithdrawEconomy(),
            InMemoryProceedsStore(), journal, clock)

        val result = engine.purchase(buyer, offer.id)

        assertTrue(result is TradeResult.Quarantined)
        assertTrue(journal.quarantined.isNotEmpty())
        assertFalse(journal.events.any { it.value.startsWith("abort:") })
    }

    @Test fun withdrawQuarantinesWhenOfferRestorationFails() {
        val seller = UUID.randomUUID()
        val offer = offer(seller)
        val offers = FailingRestoreOfferBook(offer)
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, RejectingDeliveryPokemon(), AppliedEconomy(),
            InMemoryProceedsStore(), journal, clock)

        val result = engine.withdraw(seller, offer.id)

        assertTrue(result is TradeResult.Quarantined)
        assertTrue(journal.quarantined.isNotEmpty())
        assertFalse(journal.events.any { it.value.startsWith("abort:") })
    }

    @Test fun claimQuarantinesWhenProceedsRestorationFails() {
        val owner = UUID.randomUUID()
        val key = ProceedsKey(payment.adapter, payment.currency)
        val proceeds = FailingRestoreProceeds(owner, key, 8)
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(InMemoryOfferBook(), NoopPokemon(), RejectingDepositEconomy(),
            proceeds, journal, clock)

        val result = engine.claim(owner, key)

        assertTrue(result is ClaimResult.Quarantined)
        assertEquals(0, proceeds.balance(owner, key))
        assertTrue(journal.quarantined.isNotEmpty())
        assertFalse(journal.events.any { it.value.startsWith("abort:") })
    }

    private fun offer(seller: UUID) = TradeOffer(
        OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment,
        PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}"),
        now, now.plusSeconds(3600)
    )

    private class FailingRestoreOfferBook(initial: TradeOffer) : OfferBook {
        private var value: TradeOffer? = initial
        private var removed = false
        override fun find(id: OfferId) = value?.takeIf { it.id == id }
        override fun containsPokemon(pokemonId: UUID) = value?.pokemon?.pokemonId == pokemonId
        override fun countOwnedBy(owner: UUID) = if (value?.owner?.playerId == owner) 1 else 0
        override fun all() = listOfNotNull(value)
        override fun remove(id: OfferId): TradeOffer? {
            val old = find(id) ?: return null
            value = null
            removed = true
            return old
        }
        override fun add(offer: TradeOffer) {
            if (removed) error("simulated offer restoration failure")
            value = offer
        }
    }

    private class FailingRestoreProceeds(
        private val owner: UUID,
        private val key: ProceedsKey,
        amount: Long
    ) : ProceedsStore {
        private var value = amount
        private var debited = false
        override fun balance(owner: UUID, key: ProceedsKey) =
            if (owner == this.owner && key == this.key) value else 0L
        override fun debit(owner: UUID, key: ProceedsKey, amount: Long) {
            require(balance(owner, key) >= amount)
            value -= amount
            debited = true
        }
        override fun credit(owner: UUID, key: ProceedsKey, amount: Long) {
            if (debited) error("simulated proceeds restoration failure")
            value += amount
        }
        override fun balances(owner: UUID): Map<ProceedsKey, Long> =
            if (owner == this.owner && value > 0) mapOf(key to value) else emptyMap()
    }

    private open class NoopPokemon : PokemonPort {
        override fun inspectOwned(playerId: UUID, pokemonId: UUID): PokemonEnvelope? = null
        override fun removeOwned(operationId: UUID, playerId: UUID, pokemonId: UUID) = PokemonMutation.Applied
        override fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope) = PokemonMutation.Applied
        override fun owns(playerId: UUID, pokemonId: UUID) = false
    }

    private class RejectingDeliveryPokemon : NoopPokemon() {
        override fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope): PokemonMutation =
            PokemonMutation.Rejected("storage full")
    }

    private open class AppliedEconomy : EconomyPort {
        override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = true
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long) = EconomyResult.Applied
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long) = EconomyResult.Applied
    }

    private class RejectingWithdrawEconomy : AppliedEconomy() {
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult =
            EconomyResult.Rejected("wallet rejected")
    }

    private class RejectingDepositEconomy : AppliedEconomy() {
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult =
            EconomyResult.Rejected("wallet rejected")
    }
}
