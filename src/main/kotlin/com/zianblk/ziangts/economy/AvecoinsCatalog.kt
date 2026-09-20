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

    fun isSupported(currency: String): Boolean =
        currency.startsWith("avecoins:") && currency in all()

    /** Human-readable denomination used by the market UI (for example CopperCoin). */
    fun displayName(currency: String): String {
        val path = currency.substringAfter(':').substringAfterLast('/')
        val suffix = when {
            path.endsWith("coin") -> "Coin"
            path.endsWith("ticket") -> "Ticket"
            else -> ""
        }
        val base = if (suffix.isEmpty()) path else path.dropLast(suffix.length)
        return base.split('_', '-').filter(String::isNotBlank)
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) } + suffix
    }
}
