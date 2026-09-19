package com.zianblk.ziangts.config

/**
 * Runtime configuration for Zian GTS.
 *
 * The reference mod defaults were 48 hours, 20 listings and
 * minecraft:diamond. Zian GTS keeps those values as migration-safe defaults,
 * while the economy provider will allow AVECOINS to replace item currency.
 */
data class GtsConfig(
    val expirationTimeHours: Long = 48,
    val enableDebugLogging: Boolean = false,
    val maxListings: Int = 20,
    val currency: String = "minecraft:diamond",
    val economyProvider: String = "vanilla_item"
) {
    init {
        require(expirationTimeHours > 0) { "expirationTimeHours must be greater than zero" }
        require(maxListings > 0) { "maxListings must be greater than zero" }
        require(currency.isNotBlank()) { "currency must not be blank" }
        require(economyProvider.isNotBlank()) { "economyProvider must not be blank" }
    }
}
