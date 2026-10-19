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
}
