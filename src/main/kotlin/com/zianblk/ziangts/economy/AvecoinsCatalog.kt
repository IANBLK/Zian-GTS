package com.zianblk.ziangts.economy

/**
 * Public AVECOINS 2.3 currency catalog.  The fallback keeps command
 * suggestions useful when AVECOINS is not installed; when it is installed we
 * additionally read its public CraftingConfig lists without linking to its
 * proprietary classes at compile time.
 */
object AvecoinsCatalog {
    private val fallback = linkedSetOf(
        "avecoins:coppercoin", "avecoins:ironcoin", "avecoins:goldcoin",
        "avecoins:diamondcoin", "avecoins:netheritecoin", "avecoins:goldticket",
        "avecoins:diamondticket", "avecoins:netheriteticket"
    )

    fun all(): Set<String> {
        val values = linkedSetOf<String>()
        values += fallback
        runCatching {
            val config = Class.forName("net.sundggs.avecoins.config.CraftingConfig")
            listOf("COINS", "TICKETS", "MANAGED_RESULTS").forEach { field ->
                @Suppress("UNCHECKED_CAST")
                values += (config.getField(field).get(null) as? Iterable<*>)
                    ?.filterIsInstance<String>() ?: emptyList()
            }
        }
        return values
    }
}
