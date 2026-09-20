package com.zianblk.ziangts.economy

/** A change of configured provider must never redirect existing offers or earnings. */
data class EconomyKey(val provider: String, val currency: String) {
    init {
        require(provider in setOf("vanilla_item", "avecoins_wallet"))
        require(currency.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")))
    }
    val storageKey: String get() = if (provider == "vanilla_item") currency else "$provider|$currency"
    companion object {
        fun parse(value: String): EconomyKey {
            val split = value.split('|', limit = 2)
            return if (split.size == 1) EconomyKey("vanilla_item", value) else EconomyKey(split[0], split[1])
        }
    }
}
