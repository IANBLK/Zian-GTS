package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TradeEngineReservationRaceTest {
    private val now = Instant.parse("2026-09-22T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val payment = PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8)

    @Test fun purchaseAbortsWhenOfferDisappearsAfterJournalBeginWithoutExternalMutation() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val offer = offer(seller)
        val offers = DisappearingOfferBook(offer)
        val economy = CountingEconomy()
        val pokemon = CountingPokemon()
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, pokemon, economy, InMemoryProceedsStore(), journal, clock)

        val result = engine.purchase(buyer, offer.id)

        assertTrue(result is TradeResult.Rejected)
        assertEquals(0, economy.withdrawCalls)
        assertEquals(0, pokemon.deliverCalls)
        assertTrue(journal.events.any { it.value.startsWith("abort:offer disappeared before reservation") })
        assertTrue(journal.quarantined.isEmpty())
    }

    @Test fun withdrawAbortsWhenOfferDisappearsAfterJournalBeginWithoutDelivery() {
        val seller = UUID.randomUUID()
        val offer = offer(seller)
        val offers = DisappearingOfferBook(offer)
        val pokemon = CountingPokemon()
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, pokemon, CountingEconomy(), InMemoryProceedsStore(), journal, clock)

        val result = engine.withdraw(seller, offer.id)

        assertTrue(result is TradeResult.Rejected)
        assertEquals(0, pokemon.deliverCalls)
        assertTrue(journal.events.any { it.value.startsWith("abort:offer disappeared before withdrawal reservation") })
        assertTrue(journal.quarantined.isEmpty())
    }

    private fun offer(seller: UUID) = TradeOffer(
        OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment,
        PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}"),
        now, now.plusSeconds(3600)
    )

    private class DisappearingOfferBook(private val offer: TradeOffer) : OfferBook {
        override fun find(id: OfferId): TradeOffer? = if (id == offer.id) offer else null
        override fun containsPokemon(pokemonId: UUID) = pokemonId == offer.pokemon.pokemonId
        override fun countOwnedBy(owner: UUID) = if (owner == offer.owner.playerId) 1 else 0
        override fun add(offer: TradeOffer) = error("unexpected restoration")
        override fun remove(id: OfferId): TradeOffer? = null
        override fun all(): List<TradeOffer> = listOf(offer)
    }

    private class CountingEconomy : EconomyPort {
        var withdrawCalls = 0
        override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = true
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
            withdrawCalls++
            return EconomyResult.Applied
        }
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult =
            EconomyResult.Applied
    }

    private class CountingPokemon : PokemonPort {
        var deliverCalls = 0
        override fun inspectOwned(playerId: UUID, pokemonId: UUID): PokemonEnvelope? = null
        override fun removeOwned(operationId: UUID, playerId: UUID, pokemonId: UUID): PokemonMutation =
            PokemonMutation.Rejected("not used")
        override fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope): PokemonMutation {
            deliverCalls++
            return PokemonMutation.Applied
        }
        override fun owns(playerId: UUID, pokemonId: UUID) = false
    }
}
