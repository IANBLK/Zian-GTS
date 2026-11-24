package com.zianblk.ziangts.v2.client

import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component

/** Nombres de Cobblemon y monedas en el idioma del cliente; si no hay traducción se muestra el id legible. */
object ClientNames {
    fun friendly(value: String): String = value.substringAfter(':').replace('_', ' ').replace('-', ' ')
        .split(' ').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    private fun key(prefix: String, value: String, suffix: String = ""): String? {
        val id = value.substringAfter(':').lowercase().replace("_", "").replace("-", "")
        return if (id.isBlank()) null else prefix + id + suffix
    }

    /** Naturaleza, habilidad o movimiento (prefijo cobblemon.nature., .ability., .move.). */
    fun cobblemon(prefix: String, value: String): Component {
        val key = key(prefix, value) ?: return Component.literal(friendly(value))
        return if (I18n.exists(key)) Component.translatable(key) else Component.literal(friendly(value))
    }

    fun species(value: String): String {
        val key = key("cobblemon.species.", value, ".name") ?: return friendly(value)
        return if (I18n.exists(key)) I18n.get(key) else friendly(value)
    }

    fun currency(value: String): String {
        val path = value.substringAfter(':').lowercase()
        return if (UiText.hasCurrency(path)) UiText.currency(path).string else friendly(value)
    }
}
