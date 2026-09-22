package com.zianblk.ziangts.v2.testadapter

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

class DurablePortHardKillTest {
    @TempDir lateinit var dir: Path

    private fun runProbe(scenario: String): Int {
        val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val process = ProcessBuilder(
            java.toString(), "-cp", System.getProperty("java.class.path"),
            DurablePortCrashProbe::class.java.name, dir.toString(), scenario
        ).redirectErrorStream(true).start()
        assertTrue(process.waitFor(20, TimeUnit.SECONDS), "probe timed out")
        return process.exitValue()
    }

    @Test fun withdrawalIsDurableAcrossHardKill() {
        assertEquals(29, runProbe("economy_withdraw"))
        val player = UUID.fromString("11111111-1111-1111-1111-111111111111")
        assertEquals(6, DurableFileEconomyPort(dir.resolve("economy.state")).balance(player, "avecoins:coppercoin"))
    }

    @Test fun deliveryIsDurableAcrossHardKill() {
        assertEquals(29, runProbe("pokemon_delivery"))
        val buyer = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val pokemon = UUID.fromString("33333333-3333-3333-3333-333333333333")
        assertTrue(DurableFilePokemonPort(dir.resolve("pokemon.state")).owns(buyer, pokemon))
    }
}
