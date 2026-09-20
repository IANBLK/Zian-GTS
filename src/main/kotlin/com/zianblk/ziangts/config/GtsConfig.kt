package com.zianblk.ziangts.config

/**
 * Runtime configuration for Zian GTS.
 *
 * The reference mod defaults were 48 hours and 20 listings. New offers use
 * AVECOINS currency exclusively; legacy listing data remains readable so it
 * can be recovered safely.
 */
data class GtsConfig(
    val expirationTimeHours: Long = 48,
    val enableDebugLogging: Boolean = false,
    val maxListings: Int = 20,
    val currency: String = "avecoins:coppercoin",
    val economyProvider: String = "vanilla_item"
) {
    init {
        require(expirationTimeHours > 0) { "expirationTimeHours must be greater than zero" }
        require(maxListings > 0) { "maxListings must be greater than zero" }
        require(currency.isNotBlank()) { "currency must not be blank" }
        require(economyProvider.isNotBlank()) { "economyProvider must not be blank" }
    }
}
