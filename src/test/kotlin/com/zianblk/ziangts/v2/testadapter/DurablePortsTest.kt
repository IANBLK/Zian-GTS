package com.zianblk.ziangts.v2.testadapter

import com.zianblk.ziangts.v2.domain.PokemonEnvelope
import com.zianblk.ziangts.v2.port.EconomyResult
import com.zianblk.ziangts.v2.port.PokemonMutation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID

class DurablePortsTest {
    @TempDir lateinit var dir: Path

    @Test fun economyMutationSurvivesReopen() {
        val player = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val file = dir.resolve("economy.state")
        DurableFileEconomyPort(file).apply {
            setBalance(player, "avecoins:coppercoin", 10)
            assertEquals(EconomyResult.Applied, withdraw(operation, player, "avecoins:coppercoin", 4))
        }
        assertEquals(6, DurableFileEconomyPort(file).balance(player, "avecoins:coppercoin"))
    }

    @Test fun pokemonMutationSurvivesReopen() {
        val owner = UUID.randomUUID()
        val buyer = UUID.randomUUID()
        val operation = UUID.randomUUID()
        val value = PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{test:true}")
        val file = dir.resolve("pokemon.state")
        DurableFilePokemonPort(file).apply {
            seed(owner, value)
            assertEquals(PokemonMutation.Applied, removeOwned(operation, owner, value.pokemonId))
            assertEquals(PokemonMutation.Applied, deliver(operation, buyer, value))
        }
        DurableFilePokemonPort(file).apply {
            assertFalse(owns(owner, value.pokemonId))
            assertTrue(owns(buyer, value.pokemonId))
        }
    }
}
