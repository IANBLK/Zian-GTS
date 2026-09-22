package com.zianblk.ziangts.v2.testadapter

import com.zianblk.ziangts.v2.domain.PokemonEnvelope
import com.zianblk.ziangts.v2.port.PokemonMutation
import com.zianblk.ziangts.v2.port.PokemonPort
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.util.Base64
import java.util.UUID

/** Durable filesystem-backed PokemonPort for hard-kill integration tests. */
class DurableFilePokemonPort(private val file: Path) : PokemonPort {
    private val pokemon = linkedMapOf<UUID, MutableMap<UUID, PokemonEnvelope>>()

    init { load() }

    fun seed(playerId: UUID, value: PokemonEnvelope) {
        pokemon.getOrPut(playerId) { linkedMapOf() }[value.pokemonId] = value
        persist()
    }

    override fun inspectOwned(playerId: UUID, pokemonId: UUID) = pokemon[playerId]?.get(pokemonId)

    override fun owns(playerId: UUID, pokemonId: UUID) = inspectOwned(playerId, pokemonId) != null

    override fun removeOwned(operationId: UUID, playerId: UUID, pokemonId: UUID): PokemonMutation {
        val removed = pokemon[playerId]?.remove(pokemonId)
            ?: return PokemonMutation.Rejected("pokemon is not owned by player")
        persist()
        return PokemonMutation.Applied
    }

    override fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope): PokemonMutation {
        val owned = this.pokemon.getOrPut(playerId) { linkedMapOf() }
        if (pokemon.pokemonId in owned) return PokemonMutation.Rejected("pokemon already present")
        owned[pokemon.pokemonId] = pokemon
        persist()
        return PokemonMutation.Applied
    }

    private fun load() {
        if (!Files.exists(file)) return
        Files.readAllLines(file, StandardCharsets.UTF_8).filter { it.isNotBlank() }.forEach { line ->
            val p = line.split('|', limit = 7)
            require(p.size == 7)
            val owner = UUID.fromString(p[0])
            val id = UUID.fromString(p[1])
            val envelope = PokemonEnvelope(
                id, p[2], p[3].toInt(), p[4].toBooleanStrict(), p[5].toBooleanStrict(),
                String(Base64.getDecoder().decode(p[6]), StandardCharsets.UTF_8)
            )
            pokemon.getOrPut(owner) { linkedMapOf() }[id] = envelope
        }
    }

    private fun persist() {
        Files.createDirectories(file.parent)
        val lines = pokemon.flatMap { (owner, values) ->
            values.values.map { p ->
                val encoded = Base64.getEncoder().encodeToString(p.serialized.toByteArray(StandardCharsets.UTF_8))
                "$owner|${p.pokemonId}|${p.species}|${p.level}|${p.shiny}|${p.alpha}|$encoded"
            }
        }
        val text = lines.joinToString("\n") + if (lines.isEmpty()) "" else "\n"
        FileChannel.open(file, CREATE, WRITE, TRUNCATE_EXISTING).use { channel ->
            val bytes = StandardCharsets.UTF_8.encode(text)
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
    }
}
