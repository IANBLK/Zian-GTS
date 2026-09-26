package com.zianblk.ziangts.v2.client

import java.util.Collections
import java.util.IdentityHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ScreenEvent

/**
 * Static visual skin for Zian GTS buttons.
 *
 * Vanilla buttons are hidden only for the screen render pass, then restored immediately in
 * Render.Post and painted once with the Zian style. This keeps the real Button instances alive
 * for hitboxes, clicks, keyboard focus and narration while avoiding the duplicate vanilla/custom
 * labels that can otherwise render on top of each other.
 *
 * The skin does not touch screen backgrounds or blur state.
 */
@EventBusSubscriber(modid = "ziangts", value = [Dist.CLIENT])
object ZianButtonSkin {
    private const val SCREEN_PACKAGE = "com.zianblk.ziangts.v2.client"
    private const val BORDER = 0xFF56616B.toInt()
    private const val BG = 0xFF252B31.toInt()
    private const val BG_DISABLED = 0xFF1B2025.toInt()
    private const val TEXT = 0xFFF1F3F5.toInt()
    private const val TEXT_DISABLED = 0xFF707880.toInt()

    private val hiddenForFrame = Collections.newSetFromMap(IdentityHashMap<Button, Boolean>())

    @SubscribeEvent
    fun onRenderPre(event: ScreenEvent.Render.Pre) {
        val screen = event.screen
        if (!screen.javaClass.name.startsWith(SCREEN_PACKAGE)) return

        hiddenForFrame.clear()
        screen.children().filterIsInstance<Button>().forEach { button ->
            if (button.visible) {
                hiddenForFrame += button
                button.visible = false
            }
        }
    }

    @SubscribeEvent
    fun onRenderPost(event: ScreenEvent.Render.Post) {
        val screen = event.screen
        if (!screen.javaClass.name.startsWith(SCREEN_PACKAGE)) return

        val graphics = event.guiGraphics
        val font = Minecraft.getInstance().font

        hiddenForFrame.forEach { button ->
            button.visible = true
            if (button.width <= 2 || button.height <= 2) return@forEach

            val x = button.x
            val y = button.y
            val w = button.width
            val h = button.height
            val background = if (button.active) BG else BG_DISABLED
            val text = if (button.active) TEXT else TEXT_DISABLED

            graphics.fill(x, y, x + w, y + h, BORDER)
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, background)

            val labelY = y + (h - font.lineHeight) / 2
            graphics.drawCenteredString(font, button.message, x + w / 2, labelY, text)
        }
        hiddenForFrame.clear()
    }
}
