package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TradeEngineThreadContractTest {
    @Test fun unauthorizedThreadFailsBeforeAnyMutationOrJournalBegin() {
        val seller = UUID.randomUUID()
        val pokemonId = UUID.randomUUID()
        val offers = InMemoryOfferBook()
        val journal = RecordingTradeJournal()
        var pokemonTouched = false
        var economyTouched = false

        val pokemon = object : PokemonPort {
            override fun inspectOwned(ownerId: UUID, id: UUID): PokemonEnvelope? {
                pokemonTouched = true
                return PokemonEnvelope(id, "cobblemon:test", 10, false, false, "{}")
            }
            override fun removeOwned(operationId: UUID, ownerId: UUID, id: UUID): PokemonMutation {
                pokemonTouched = true
                return PokemonMutation.Applied
            }
            override fun deliver(operationId: UUID, ownerId: UUID, pokemon: PokemonEnvelope): PokemonMutation {
                pokemonTouched = true
                return PokemonMutation.Applied
            }
            override fun owns(ownerId: UUID, id: UUID): Boolean {
                pokemonTouched = true
                return true
            }
        }
        val economy = object : EconomyPort {
            override fun canWithdraw(playerId: UUID, currency: String, amount: Long): Boolean {
                economyTouched = true
                return true
            }
            override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
                economyTouched = true
                return EconomyResult.Applied
            }
            override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
                economyTouched = true
                return EconomyResult.Applied
            }
        }
        val engine = TradeEngine(
            offers, pokemon, economy, InMemoryProceedsStore(), journal,
            Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC),
            mutationThreadCheck = { false }
        )

        val error = assertThrows(IllegalStateException::class.java) {
            engine.publish(
                seller,
                "seller",
                pokemonId,
                PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8)
            )
        }

        assertTrue(error.message!!.contains("authorized server thread"))
        assertTrue(journal.events.isEmpty())
        assertTrue(offers.all().isEmpty())
        assertFalse(pokemonTouched)
        assertFalse(economyTouched)
    }

    @Test fun authorizedThreadStillAllowsMutationFlow() {
        val seller = UUID.randomUUID()
        val pokemonId = UUID.randomUUID()
        val envelope = PokemonEnvelope(pokemonId, "cobblemon:test", 10, false, false, "{}")
        var owned = true
        val pokemon = object : PokemonPort {
            override fun inspectOwned(ownerId: UUID, id: UUID) = if (owned && id == pokemonId) envelope else null
            override fun removeOwned(operationId: UUID, ownerId: UUID, id: UUID): PokemonMutation {
                owned = false
                return PokemonMutation.Applied
            }
            override fun deliver(operationId: UUID, ownerId: UUID, pokemon: PokemonEnvelope) = PokemonMutation.Applied
            override fun owns(ownerId: UUID, id: UUID) = owned && id == pokemonId
        }
        val engine = TradeEngine(
            InMemoryOfferBook(), pokemon,
            object : EconomyPort {
                override fun canWithdraw(playerId: UUID, currency: String, amount: Long) = true
                override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long) = EconomyResult.Applied
                override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long) = EconomyResult.Applied
            },
            InMemoryProceedsStore(), RecordingTradeJournal(),
            Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC),
            mutationThreadCheck = { true }
        )

        assertTrue(engine.publish(
            seller, "seller", pokemonId,
            PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8)
        ) is TradeResult.Success)
    }
}
