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
        val engine = TradeEngine(offers, pokemon, journal, Clock.fixed(now, ZoneOffset.UTC))

        val result = engine.publish(seller, "Seller", p.pokemonId, payment)
        assertTrue(result is TradeResult.Success)
        assertFalse(pokemon.owns(seller, p.pokemonId))
        assertEquals(1, offers.all().size)
        assertTrue(journal.events.any { it.value == "stage:POKEMON_REMOVED" })
        assertTrue(journal.events.any { it.value == "complete" })
    }

    @Test fun withdrawReturnsPokemonAndRemovesOffer() {
        val seller = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val pokemon = DurableFilePokemonPort(dir.resolve("pokemon.state"))
        val offers = InMemoryOfferBook()
        val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment, p, now, now.plusSeconds(3600))
        offers.add(offer)
        val journal = RecordingTradeJournal()
        val engine = TradeEngine(offers, pokemon, journal, Clock.fixed(now, ZoneOffset.UTC))

        val result = engine.withdraw(seller, offer.id)
        assertTrue(result is TradeResult.Success)
        assertTrue(pokemon.owns(seller, p.pokemonId))
        assertNull(offers.find(offer.id))
    }

    @Test fun otherPlayerCannotWithdrawOffer() {
        val seller = UUID.randomUUID()
        val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val offers = InMemoryOfferBook()
        val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment, p, now, now.plusSeconds(3600))
        offers.add(offer)
        val engine = TradeEngine(offers, DurableFilePokemonPort(dir.resolve("pokemon.state")), RecordingTradeJournal(), Clock.fixed(now, ZoneOffset.UTC))

        assertTrue(engine.withdraw(UUID.randomUUID(), offer.id) is TradeResult.Rejected)
        assertNotNull(offers.find(offer.id))
    }
}
