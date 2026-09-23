package com.zianblk.ziangts.v2.runtime

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.activestate.InactivePokemonState
import com.zianblk.ziangts.v2.domain.PokemonEnvelope
import com.zianblk.ziangts.v2.port.PokemonMutation
import com.zianblk.ziangts.v2.port.PokemonPort
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.TagParser
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * Cobblemon boundary for V2. The transaction core never receives Cobblemon or Minecraft classes.
 * All mutations are expected to run on the Minecraft server thread.
 */
class CobblemonPokemonPort(
    private val server: MinecraftServer,
    private val registryAccess: RegistryAccess = server.registryAccess()
) : PokemonPort {
    /** Resolves a human-friendly 1-based party slot to the Pokemon UUID for temporary V2 commands. */
    fun partyPokemonId(ownerId: UUID, slot: Int): UUID? {
        require(slot in 1..6) { "party slot must be between 1 and 6" }
        val player = online(ownerId) ?: return null
        requireServerThread()
        return Cobblemon.storage.getParty(player).get(slot - 1)?.uuid
    }

    override fun inspectOwned(ownerId: UUID, pokemonId: UUID): PokemonEnvelope? {
        val player = online(ownerId) ?: return null
        requireServerThread()
        val pokemon = find(player, pokemonId) ?: return null
        if (!pokemon.tradeable || pokemon.state !is InactivePokemonState) return null
        return envelope(pokemon)
    }

    override fun removeOwned(operationId: UUID, ownerId: UUID, pokemonId: UUID): PokemonMutation {
        val player = online(ownerId) ?: return PokemonMutation.Rejected("player_offline")
        requireServerThread()
        val party = Cobblemon.storage.getParty(player)
        val pc = Cobblemon.storage.getPC(player)
        val partyPokemon = party[pokemonId]
        val pcPokemon = pc[pokemonId]
        val pokemon = partyPokemon ?: pcPokemon ?: return PokemonMutation.Rejected("pokemon_not_owned")
        if (!pokemon.tradeable) return PokemonMutation.Rejected("pokemon_not_tradeable")
        if (pokemon.state !is InactivePokemonState) return PokemonMutation.Rejected("pokemon_not_recalled")
        return try {
            val removed = if (partyPokemon != null) party.remove(pokemon) else pc.remove(pokemon)
            if (removed) PokemonMutation.Applied
            else PokemonMutation.Rejected("pokemon_remove_rejected")
        } catch (error: Exception) {
            PokemonMutation.Uncertain("Cobblemon remove threw ${error.javaClass.simpleName}")
        }
    }

    override fun deliver(operationId: UUID, ownerId: UUID, pokemon: PokemonEnvelope): PokemonMutation {
        val player = online(ownerId) ?: return PokemonMutation.Rejected("player_offline")
        requireServerThread()
        if (owns(ownerId, pokemon.pokemonId)) return PokemonMutation.Rejected("pokemon_already_owned")
        val decoded = try { decode(pokemon) }
        catch (error: Exception) { return PokemonMutation.Rejected("pokemon_payload_invalid") }
        return try {
            val party = Cobblemon.storage.getParty(player)
            val pc = Cobblemon.storage.getPC(player)
            when {
                party.getFirstAvailablePosition() != null -> if (party.add(decoded)) PokemonMutation.Applied
                    else PokemonMutation.Rejected("party_delivery_rejected")
                pc.getFirstAvailablePosition() != null -> if (pc.add(decoded)) PokemonMutation.Applied
                    else PokemonMutation.Rejected("pc_delivery_rejected")
                else -> PokemonMutation.Rejected("pokemon_storage_full")
            }
        } catch (error: Exception) {
            PokemonMutation.Uncertain("Cobblemon delivery threw ${error.javaClass.simpleName}")
        }
    }

    override fun owns(ownerId: UUID, pokemonId: UUID): Boolean {
        val player = online(ownerId) ?: return false
        requireServerThread()
        return Cobblemon.storage.getParty(player)[pokemonId] != null ||
            Cobblemon.storage.getPC(player)[pokemonId] != null
    }

    private fun find(player: ServerPlayer, pokemonId: UUID): Pokemon? =
        Cobblemon.storage.getParty(player)[pokemonId] ?: Cobblemon.storage.getPC(player)[pokemonId]

    private fun envelope(pokemon: Pokemon): PokemonEnvelope {
        val tag = pokemon.saveToNBT(registryAccess)
        return PokemonEnvelope(
            pokemon.uuid,
            pokemon.species.resourceIdentifier.toString(),
            pokemon.level,
            pokemon.shiny,
            false, // Alpha is addon-defined metadata; V2 adapter hook will be supplied separately.
            tag.toString()
        )
    }

    private fun decode(envelope: PokemonEnvelope): Pokemon =
        Pokemon().also { it.loadFromNBT(registryAccess, TagParser.parseTag(envelope.serialized)) }

    private fun online(id: UUID): ServerPlayer? = server.playerList.getPlayer(id)

    private fun requireServerThread() {
        check(server.isSameThread) { "CobblemonPokemonPort requires the Minecraft server thread" }
    }
}
