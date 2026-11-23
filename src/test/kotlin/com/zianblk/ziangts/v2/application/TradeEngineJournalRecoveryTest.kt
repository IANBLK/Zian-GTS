package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.persistence.FileRecoveryStore
import com.zianblk.ziangts.v2.port.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TradeEngineJournalRecoveryTest {
    private val now = Instant.parse("2026-09-22T16:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val payment = PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8)
    private val seller = UUID.randomUUID()
    private val buyer = UUID.randomUUID()
    private val p = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, serialized = "{test:true}")
    private val offer = TradeOffer(OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"), payment, p, now, now.plusSeconds(3600))

    @Test fun purchaseFailureBeforePaymentRestoresOfferAndDoesNotCharge() {
        val offers = InMemoryOfferBook().apply { add(offer) }
        val economy = Economy(); val pokemon = Pokemon()
        val engine = TradeEngine(offers, pokemon, economy, InMemoryProceedsStore(), FailOnStage(TradeStage.BEFORE_PAYMENT), clock)

        assertTrue(engine.purchase(buyer, offer.id) is TradeResult.Quarantined)

        assertNotNull(offers.find(offer.id))
        assertEquals(0, economy.withdrawCalls)
        assertEquals(0, pokemon.deliverCalls)
    }

    @Test fun withdrawFailureBeforeDeliveryRestoresOffer() {
        val offers = InMemoryOfferBook().apply { add(offer) }
        val pokemon = Pokemon()
        val engine = TradeEngine(offers, pokemon, Economy(), InMemoryProceedsStore(), FailOnStage(TradeStage.BEFORE_POKEMON_DELIVERY), clock)

        assertTrue(engine.withdraw(seller, offer.id) is TradeResult.Quarantined)

        assertNotNull(offers.find(offer.id))
        assertEquals(0, pokemon.deliverCalls)
    }

    @Test fun purchaseFailureAfterPaymentWritesRecoveryFileAndDoesNotDeliver(@TempDir dir: Path) {
        val offers = InMemoryOfferBook().apply { add(offer) }
        val pokemon = Pokemon()
        val journal = FailOnStage(TradeStage.PAYMENT_APPLIED)
        val engine = TradeEngine(offers, pokemon, Economy(), InMemoryProceedsStore(), journal, clock, recovery = FileRecoveryStore(dir))

        val result = engine.purchase(buyer, offer.id) as TradeResult.Quarantined

        assertEquals(0, pokemon.deliverCalls)
        assertNull(offers.find(offer.id))
        val file = dir.resolve("${result.operationId}.json")
        val json = Files.readString(file)
        listOf(offer.id.value, buyer, seller, p.pokemonId).forEach { assertTrue(json.contains(it.toString()), "missing $it") }
        assertTrue(json.contains("{test:true}") && json.contains("PAYMENT_APPLIED") && json.contains("\"amount\": 8"))
        assertTrue(json.contains("\"paymentApplied\": true"))
        assertTrue(json.contains("\"formatVersion\": 1"))
        assertTrue(journal.quarantined)
    }

    @Test fun purchaseFailureBeforePaymentWithRestoreFailureDumpsWithoutPayment(@TempDir dir: Path) {
        val inner = InMemoryOfferBook().apply { add(offer) }
        val offers = object : OfferBook by inner { override fun add(offer: TradeOffer) = error("store down") }
        val economy = Economy(); val pokemon = Pokemon()
        val engine = TradeEngine(offers, pokemon, economy, InMemoryProceedsStore(), FailOnStage(TradeStage.BEFORE_PAYMENT), clock, recovery = FileRecoveryStore(dir))

        val result = engine.purchase(buyer, offer.id) as TradeResult.Quarantined

        assertEquals(0, economy.withdrawCalls)
        assertEquals(0, pokemon.deliverCalls)
        val json = Files.readString(dir.resolve("${result.operationId}.json"))
        assertTrue(json.contains("{test:true}") && json.contains("BEFORE_PAYMENT"))
        assertTrue(json.contains("\"paymentApplied\": false"))
    }

    @Test fun recoveryWriteFailureStillQuarantinesWithoutThrowing() {
        val offers = InMemoryOfferBook().apply { add(offer) }
        val failing = object : RecoveryPort { override fun dump(record: RecoveryRecord) = error("disk full") }
        val engine = TradeEngine(offers, Pokemon(), Economy(), InMemoryProceedsStore(), FailOnStage(TradeStage.BEFORE_POKEMON_DELIVERY), clock, recovery = failing)

        assertTrue(engine.purchase(buyer, offer.id) is TradeResult.Quarantined)
    }

    private class FailOnStage(private val failStage: TradeStage) : TradeJournalPort {
        var quarantined = false
        override fun begin(operation: TradeOperation, subjectId: UUID) = JournalTicket(UUID.randomUUID(), operation)
        override fun stage(ticket: JournalTicket, stage: TradeStage) { if (stage == failStage) error("simulated journal failure at $stage") }
        override fun complete(ticket: JournalTicket) = Unit
        override fun abort(ticket: JournalTicket, reason: String) = Unit
        override fun quarantine(ticket: JournalTicket, reason: String) { quarantined = true }
    }

    private class Economy : EconomyPort {
        var withdrawCalls = 0
        override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = true
        override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult { withdrawCalls++; return EconomyResult.Applied }
        override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long) = EconomyResult.Applied
    }

    private class Pokemon : PokemonPort {
        var deliverCalls = 0
        override fun inspectOwned(playerId: UUID, pokemonId: UUID): PokemonEnvelope? = null
        override fun removeOwned(operationId: UUID, playerId: UUID, pokemonId: UUID) = PokemonMutation.Applied
        override fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope): PokemonMutation { deliverCalls++; return PokemonMutation.Applied }
        override fun owns(playerId: UUID, pokemonId: UUID) = false
    }
}
