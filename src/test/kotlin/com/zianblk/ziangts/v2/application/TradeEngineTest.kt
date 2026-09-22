package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import com.zianblk.ziangts.v2.testadapter.DurableFilePokemonPort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TradeEngineTest {
    @TempDir lateinit var dir: Path
    private val now = Instant.parse("2026-09-22T16:00:00Z")
    private val payment = PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8)

    @Test fun publishRemovesPokemonAndCreatesOffer() {
        val seller = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val pokemon = DurableFilePokemonPort(dir.resolve("pokemon.state")).apply { seed(seller, p) }
        val offers = InMemoryOfferBook()
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, pokemon, TestEconomy(), InMemoryProceedsStore(), journal, Clock.fixed(now, ZoneOffset.UTC))

        val result = engine.publish(seller, "Seller", p.pokemonId, payment)
        assertTrue(result is TradeResult.Success)
        assertFalse(pokemon.owns(seller, p.pokemonId))
        assertEquals(1, offers.all().size)
        assertTrue(journal.events.any { it.value == "stage:POKEMON_REMOVED" })
        assertTrue(journal.events.any { it.value == "complete" })
    }

    @Test fun rejectedPublishRemovalAbortsWithoutQuarantine() {
        val seller = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val journal = RecordingTradeJournal()
        val pokemon = RejectingRemovalPokemonPort(seller, p)
        val offers = InMemoryOfferBook()
        val engine = TradeEngine(offers, pokemon, TestEconomy(), InMemoryProceedsStore(), journal, Clock.fixed(now, ZoneOffset.UTC))

        val result = engine.publish(seller, "Seller", p.pokemonId, payment)

        assertTrue(result is TradeResult.Rejected)
        assertTrue(offers.all().isEmpty())
        assertTrue(pokemon.owns(seller, p.pokemonId))
        assertTrue(journal.events.any { it.value.startsWith("abort:") })
        assertTrue(journal.quarantined.isEmpty())
    }

    @Test fun withdrawReturnsPokemonAndRemovesOffer() {
        val seller = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val pokemon = DurableFilePokemonPort(dir.resolve("pokemon.state"))
        val offers = InMemoryOfferBook()
        val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment, p, now, now.plusSeconds(3600))
        offers.add(offer)
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, pokemon, TestEconomy(), InMemoryProceedsStore(), journal, Clock.fixed(now, ZoneOffset.UTC))

        val result = engine.withdraw(seller, offer.id)
        assertTrue(result is TradeResult.Success)
        assertTrue(pokemon.owns(seller, p.pokemonId))
        assertNull(offers.find(offer.id))
    }

    @Test fun rejectedWithdrawRestoresOfferAndAbortsWithoutQuarantine() {
        val seller = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val offers = InMemoryOfferBook()
        val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment, p, now, now.plusSeconds(3600))
        offers.add(offer)
        val journal = RecordingTradeJournal()
        val pokemon = RejectingDeliveryPokemonPort()
        val engine = TradeEngine(offers, pokemon, TestEconomy(), InMemoryProceedsStore(), journal, Clock.fixed(now, ZoneOffset.UTC))

        val result = engine.withdraw(seller, offer.id)

        assertTrue(result is TradeResult.Rejected)
        assertNotNull(offers.find(offer.id))
        assertTrue(journal.events.any { it.value.startsWith("abort:") })
        assertTrue(journal.quarantined.isEmpty())
    }

    @Test fun otherPlayerCannotWithdrawOffer() {
        val seller = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val offers = InMemoryOfferBook()
        val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment, p, now, now.plusSeconds(3600))
        offers.add(offer)
        val engine = TradeEngine(offers, DurableFilePokemonPort(dir.resolve("pokemon.state")), TestEconomy(), InMemoryProceedsStore(), RecordingTradeJournal(), Clock.fixed(now, ZoneOffset.UTC))

        assertTrue(engine.withdraw(UUID.randomUUID(), offer.id) is TradeResult.Rejected)
        assertNotNull(offers.find(offer.id))
    }
    @Test fun purchaseChargesDeliversAndCreditsSeller() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val pokemon = DurableFilePokemonPort(dir.resolve("purchase-pokemon.state"))
        val offers = InMemoryOfferBook()
        val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment, p, now, now.plusSeconds(3600))
        offers.add(offer)
        val economy = TestEconomy().apply { balances[buyer] = 20 }
        val proceeds = InMemoryProceedsStore()
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, pokemon, economy, proceeds, journal, Clock.fixed(now, ZoneOffset.UTC))

        val result = engine.purchase(buyer, offer.id)

        assertTrue(result is TradeResult.Success)
        assertNull(offers.find(offer.id))
        assertEquals(12, economy.balances[buyer])
        assertTrue(pokemon.owns(buyer, p.pokemonId))
        assertEquals(8, proceeds.balance(seller, ProceedsKey("avecoins_wallet", "avecoins:coppercoin")))
        assertTrue(journal.events.any { it.value == "stage:PAYMENT_APPLIED" })
        assertTrue(journal.events.any { it.value == "stage:POKEMON_DELIVERED" })
        assertTrue(journal.events.any { it.value == "stage:PROCEEDS_CREDITED" })
    }

    @Test fun rejectedPurchaseRestoresOfferAndAbortsWithoutQuarantine() {
        val seller = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val offers = InMemoryOfferBook()
        val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment, p, now, now.plusSeconds(3600))
        offers.add(offer)
        val economy = RejectingWithdrawEconomy()
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, DurableFilePokemonPort(dir.resolve("rejected-purchase.state")),
            economy, InMemoryProceedsStore(), journal, Clock.fixed(now, ZoneOffset.UTC))

        val result = engine.purchase(buyer, offer.id)

        assertTrue(result is TradeResult.Rejected)
        assertNotNull(offers.find(offer.id))
        assertTrue(journal.events.any { it.value.startsWith("abort:") })
        assertTrue(journal.quarantined.isEmpty())
    }

    @Test fun purchaseRejectsOwnOfferWithoutMutation() {
        val seller = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val offers = InMemoryOfferBook()
        val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment, p, now, now.plusSeconds(3600))
        offers.add(offer)
        val economy = TestEconomy().apply { balances[seller] = 20 }
        val engine = TradeEngine(offers, DurableFilePokemonPort(dir.resolve("own.state")), economy, InMemoryProceedsStore(), RecordingTradeJournal(), Clock.fixed(now, ZoneOffset.UTC))

        assertTrue(engine.purchase(seller, offer.id) is TradeResult.Rejected)
        assertEquals(20, economy.balances[seller])
        assertNotNull(offers.find(offer.id))
    }

    private class RejectingRemovalPokemonPort(
        private val owner: UUID,
        private val envelope: PokemonEnvelope
    ) : PokemonPort {
        override fun inspectOwned(playerId: UUID, pokemonId: UUID): PokemonEnvelope? =
            if (playerId == owner && pokemonId == envelope.pokemonId) envelope else null
        override fun removeOwned(operationId: UUID, playerId: UUID, pokemonId: UUID): PokemonMutation =
            PokemonMutation.Rejected("removal rejected")
        override fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope): PokemonMutation =
            PokemonMutation.Rejected("not used")
        override fun owns(playerId: UUID, pokemonId: UUID): Boolean =
            playerId == owner && pokemonId == envelope.pokemonId
    }

    private class RejectingDeliveryPokemonPort : PokemonPort {
        override fun inspectOwned(ownerId: UUID, pokemonId: UUID): PokemonEnvelope? = null
        override fun removeOwned(operationId: UUID, ownerId: UUID, pokemonId: UUID): PokemonMutation =
            PokemonMutation.Rejected("not used")
        override fun deliver(operationId: UUID, ownerId: UUID, pokemon: PokemonEnvelope): PokemonMutation =
            PokemonMutation.Rejected("storage full")
        override fun owns(playerId: UUID, pokemonId: UUID): Boolean = false
    }

    private class RejectingWithdrawEconomy : EconomyPort {
        override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = true
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult =
            EconomyResult.Rejected("wallet rejected")
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult =
            EconomyResult.Applied
    }

    private class TestEconomy : EconomyPort {
        val balances = mutableMapOf<UUID, Long>()
        override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = (balances[playerId] ?: 0) >= amount
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
            if (!canWithdraw(playerId, currency, amount)) return EconomyResult.Rejected("insufficient funds")
            balances[playerId] = (balances[playerId] ?: 0) - amount
            return EconomyResult.Applied
        }
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
            balances[playerId] = (balances[playerId] ?: 0) + amount
            return EconomyResult.Applied
        }
    }
}
