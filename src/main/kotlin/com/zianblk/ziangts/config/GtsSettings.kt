package com.zianblk.ziangts.config

import com.zianblk.ziangts.economy.AvecoinsCatalog
import net.neoforged.neoforge.common.ModConfigSpec

/** World-specific server settings. Trading stays opt-in while reconstruction is being validated. */
object GtsSettings {
    private val builder = ModConfigSpec.Builder()
    val enabled = builder.comment("Enable trading only in a test world until recovery and multiplayer tests are complete.")
        .define("tradingEnabled", false)
    val provider = builder.defineInList("economyProvider", "vanilla_item", listOf("vanilla_item", "avecoins_wallet"))
    val currency = builder.comment("AVECOINS denomination used when /gts sell omits the currency argument.")
        .define("currency", "avecoins:coppercoin")
    val maxListings = builder.defineInRange("maxListings", 20, 1, 1000)
    val expirationHours = builder.defineInRange("expirationHours", 48, 1, 8760)
    val spec: ModConfigSpec = builder.build()

    fun snapshot(): GtsConfig {
        val configuredCurrency = currency.get()
        return GtsConfig(
            currency = configuredCurrency.takeIf(AvecoinsCatalog::isSupported) ?: "avecoins:coppercoin",
            economyProvider = provider.get(),
            maxListings = maxListings.get(),
            expirationTimeHours = expirationHours.get().toLong()
        )
    }
}
