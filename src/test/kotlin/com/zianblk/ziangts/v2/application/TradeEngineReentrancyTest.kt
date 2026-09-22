package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TradeEngineReentrancyTest {
    @Test fun pokemonDeliveryCannotReenterPurchase() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val pokemonId = UUID.randomUUID()
        val offerId = OfferId(UUID.randomUUID())
        val key = ProceedsKey("avecoins_wallet", "avecoins:coppercoin")
        val offers = InMemoryOfferBook()
        offers.add(TradeOffer(offerId, OfferOwner(seller, "seller"),
            PaymentSpec(key.adapter, key.currency, 8),
            PokemonEnvelope(pokemonId, "cobblemon:test", 10, false, false, "{}"),
            Instant.parse("2026-09-22T00:00:00Z"), Instant.parse("2026-09-24T00:00:00Z")))
        val economy = object : EconomyPort {
            override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = true
            override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long) = EconomyResult.Applied
            override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long) = EconomyResult.Applied
        }
        lateinit var engine: TradeEngine
        var nested: TradeResult? = null
        val pokemon = object : PokemonPort {
            override fun inspectOwned(ownerId: UUID, pokemonId: UUID) = null
            override fun removeOwned(operationId: UUID, ownerId: UUID, pokemonId: UUID) = PokemonMutation.Rejected("unused")
            override fun deliver(operationId: UUID, ownerId: UUID, pokemon: PokemonEnvelope): PokemonMutation {
                nested = engine.purchase(ownerId, offerId)
                return PokemonMutation.Applied
            }
            override fun owns(ownerId: UUID, pokemonId: UUID) = false
        }
        engine = TradeEngine(offers, pokemon, economy, InMemoryProceedsStore(), RecordingTradeJournal(),
            Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC))

        val outer = engine.purchase(buyer, offerId)

        assertTrue(outer is TradeResult.Success)
        assertEquals(TradeResult.Rejected("market mutation already in progress"), nested)
    }
}
