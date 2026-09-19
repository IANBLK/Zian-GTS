package com.zianblk.ziangts.config

import net.neoforged.neoforge.common.ModConfigSpec

/** World-specific server settings. Trading stays opt-in while reconstruction is being validated. */
object GtsSettings {
    private val builder = ModConfigSpec.Builder()
    val enabled = builder.comment("Enable trading only in a test world until recovery and multiplayer tests are complete.")
        .define("tradingEnabled", false)
    val currency = builder.define("currency", "minecraft:diamond")
    val maxListings = builder.defineInRange("maxListings", 20, 1, 1000)
    val expirationHours = builder.defineInRange("expirationHours", 48, 1, 8760)
    val spec: ModConfigSpec = builder.build()

    fun snapshot() = GtsConfig(
        currency = currency.get(),
        maxListings = maxListings.get(),
        expirationTimeHours = expirationHours.get().toLong()
    )
}
