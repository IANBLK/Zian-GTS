package com.zianblk.ziangts.v2.port

import com.zianblk.ziangts.v2.domain.PokemonEnvelope
import java.util.UUID

/**
 * External Cobblemon boundary for the independent V2 core.
 *
 * The core owns no Cobblemon classes. Adapters translate between Cobblemon
 * runtime objects and PokemonEnvelope at this boundary.
 */
interface PokemonPort {
    fun inspectOwned(playerId: UUID, pokemonId: UUID): PokemonEnvelope?
    fun removeOwned(operationId: UUID, playerId: UUID, pokemonId: UUID): PokemonMutation
    fun deliver(operationId: UUID, playerId: UUID, pokemon: PokemonEnvelope): PokemonMutation
    fun owns(playerId: UUID, pokemonId: UUID): Boolean
}

sealed interface PokemonMutation {
    data object Applied : PokemonMutation
    data class Rejected(val reason: String) : PokemonMutation
    data class Uncertain(val reason: String) : PokemonMutation
}
