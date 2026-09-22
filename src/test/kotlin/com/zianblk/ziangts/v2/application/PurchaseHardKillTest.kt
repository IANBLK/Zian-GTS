package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.port.TradeStage
import com.zianblk.ziangts.v2.testadapter.DurableFileEconomyPort
import com.zianblk.ziangts.v2.testadapter.DurableFilePokemonPort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

class PurchaseHardKillTest {
    @TempDir lateinit var dir: Path
    private val buyer = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val pokemon = UUID.fromString("33333333-3333-3333-3333-333333333333")

    private fun crashAt(stage: TradeStage): Int {
        val java = Path.of(System.getProperty("java.home"), "bin",
            if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val p = ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
            PurchaseCrashProbe::class.java.name, dir.toString(), stage.name)
            .redirectErrorStream(true).start()
        assertTrue(p.waitFor(20, TimeUnit.SECONDS), "purchase probe timed out")
        return p.exitValue()
    }

    @Test fun hardKillAfterPaymentLeavesChargeButNoPokemon() {
        assertEquals(29, crashAt(TradeStage.PAYMENT_APPLIED))
        assertEquals(12, DurableFileEconomyPort(dir.resolve("economy.state"))
            .balance(buyer, "avecoins:coppercoin"))
        assertFalse(DurableFilePokemonPort(dir.resolve("pokemon.state")).owns(buyer, pokemon))
    }

    @Test fun hardKillAfterDeliveryLeavesChargeAndPokemon() {
        assertEquals(29, crashAt(TradeStage.POKEMON_DELIVERED))
        assertEquals(12, DurableFileEconomyPort(dir.resolve("economy.state"))
            .balance(buyer, "avecoins:coppercoin"))
        assertTrue(DurableFilePokemonPort(dir.resolve("pokemon.state")).owns(buyer, pokemon))
    }
}
