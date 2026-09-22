package com.zianblk.ziangts.v2.testadapter

import com.zianblk.ziangts.v2.domain.PokemonEnvelope
import java.nio.file.Path
import java.util.UUID

/**
 * Child-process probe used by hard-kill tests. It deliberately halts the JVM
 * immediately after a durable adapter mutation, bypassing shutdown hooks.
 */
object DurablePortCrashProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2)
        val root = Path.of(args[0])
        when (args[1]) {
            "economy_withdraw" -> {
                val player = UUID.fromString("11111111-1111-1111-1111-111111111111")
                DurableFileEconomyPort(root.resolve("economy.state")).apply {
                    setBalance(player, "avecoins:coppercoin", 10)
                    withdraw(UUID.randomUUID(), player, "avecoins:coppercoin", 4)
                }
            }
            "pokemon_delivery" -> {
                val buyer = UUID.fromString("22222222-2222-2222-2222-222222222222")
                val p = PokemonEnvelope(
                    UUID.fromString("33333333-3333-3333-3333-333333333333"),
                    "cobblemon:gimmighoul", 17, false, false, "{test:true}"
                )
                DurableFilePokemonPort(root.resolve("pokemon.state"))
                    .deliver(UUID.randomUUID(), buyer, p)
            }
            else -> error("unknown scenario")
        }
        Runtime.getRuntime().halt(29)
    }
}
