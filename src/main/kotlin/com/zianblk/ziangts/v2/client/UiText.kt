package com.zianblk.ziangts.v2.client

import net.minecraft.network.chat.Component

/** Runtime values are translation arguments, never Kotlin source embedded in a language entry. */
object UiText {
    fun pageCounter(page: Int, totalPages: Int): Component = Component.translatableWithFallback(
        "ui.ziangts.page_counter", "Página %s/%s", page.coerceAtLeast(1), totalPages.coerceAtLeast(1))

    fun genderLevel(gender: String, level: Int): Component = Component.translatableWithFallback(
        "ui.ziangts.gender_level", "%s · Nivel %s", gender, level)

    fun ivValue(label: String, value: Int): Component = Component.translatableWithFallback(
        "ui.ziangts.iv_value", "%s: %s/31", label, value.toString().padStart(2, '0'))

    /** Claves construidas por id; el respaldo es siempre el español de es_es. */
    val fallbacks: Map<String, String> = mapOf(
        "ziangts.currency.coppercoin" to "Moneda de Cobre",
        "ziangts.currency.ironcoin" to "Moneda de Hierro",
        "ziangts.currency.goldcoin" to "Moneda de Oro",
        "ziangts.currency.diamondcoin" to "Moneda de Diamante",
        "ziangts.currency.netheritecoin" to "Moneda de Netherita",
        "ziangts.currency.copperticket" to "Ticket de Cobre",
        "ziangts.currency.ironticket" to "Ticket de Hierro",
        "ziangts.currency.goldticket" to "Ticket de Oro",
        "ziangts.currency.diamondticket" to "Ticket de Diamante",
        "ziangts.currency.netheriteticket" to "Ticket de Netherita",
        "ziangts.stat.card.hp" to "PS",
        "ziangts.stat.card.atk" to "Atq",
        "ziangts.stat.card.def" to "Def",
        "ziangts.stat.card.spa" to "AtE",
        "ziangts.stat.card.spd" to "DfE",
        "ziangts.stat.card.spe" to "Vel",
        "ziangts.stat.list.hp" to "PS",
        "ziangts.stat.list.atk" to "At",
        "ziangts.stat.list.def" to "Def",
        "ziangts.stat.list.spa" to "At.E",
        "ziangts.stat.list.spd" to "Def.E",
        "ziangts.stat.list.spe" to "Vel",
        "ziangts.stat.radar.hp" to "PS",
        "ziangts.stat.radar.atk" to "At.",
        "ziangts.stat.radar.def" to "Def.",
        "ziangts.stat.radar.spa" to "At.E",
        "ziangts.stat.radar.spd" to "Def.E",
        "ziangts.stat.radar.spe" to "Vel."
    )

    fun currency(id: String): Component = fromTable("ziangts.currency.$id")

    private val statIds = listOf("hp", "atk", "def", "spa", "spd", "spe")
    fun stat(variant: String, index: Int): Component = fromTable("ziangts.stat.$variant.${statIds[index]}")

    private fun fromTable(key: String): Component = Component.translatableWithFallback(key, fallbacks.getValue(key))
}
