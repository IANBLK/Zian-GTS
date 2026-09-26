package com.zianblk.ziangts.v2.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ScreenEvent

/**
 * Visual skin for every vanilla Button used by Zian GTS screens.
 *
 * This deliberately does not replace or move widgets. The original Button remains responsible
 * for hitboxes, narration, keyboard focus and click handling; we only paint the Zian GTS skin
 * over it after the screen has rendered. That keeps the already-tested market/detail layouts and
 * actions intact while giving every screen one consistent AVESHOP-inspired visual language.
 */
@EventBusSubscriber(modid = "ziangts", value = [Dist.CLIENT])
object ZianButtonSkin {
    private const val SCREEN_PACKAGE = "com.zianblk.ziangts.v2.client"

    private const val BORDER = 0xFF4E5963.toInt()
    private const val BORDER_HOVER = 0xFFF0C75E.toInt()
    private const val BG = 0xFF252B31.toInt()
    private const val BG_HOVER = 0xFF343C44.toInt()
    private const val BG_DISABLED = 0xFF1B2025.toInt()
    private const val TEXT = 0xFFF1F3F5.toInt()
    private const val TEXT_HOVER = 0xFFFFE39A.toInt()
    private const val TEXT_DISABLED = 0xFF707880.toInt()
    private const val ACCENT = 0xFFF0C75E.toInt()

    @SubscribeEvent
    fun onRenderPost(event: ScreenEvent.Render.Post) {
        val screen = event.screen
        if (!screen.javaClass.name.startsWith(SCREEN_PACKAGE)) return

        val graphics = event.guiGraphics
        val font = Minecraft.getInstance().font

        screen.children().filterIsInstance<Button>().forEach { button ->
            if (!button.visible) return@forEach

            val x = button.x
            val y = button.y
            val w = button.width
            val h = button.height
            if (w <= 2 || h <= 2) return@forEach

            val hovered = button.active && event.mouseX >= x && event.mouseX < x + w &&
                event.mouseY >= y && event.mouseY < y + h

            val border = if (hovered) BORDER_HOVER else BORDER
            val background = when {
                !button.active -> BG_DISABLED
                hovered -> BG_HOVER
                else -> BG
            }
            val text = when {
                !button.active -> TEXT_DISABLED
                hovered -> TEXT_HOVER
                else -> TEXT
            }

            // Crisp one-pixel frame, intentionally texture-free so GUI scale does not distort it.
            graphics.fill(x, y, x + w, y + h, border)
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, background)

            // AVESHOP-like interaction accent. It appears only on hover/focus and never changes
            // the widget dimensions, so Comprar/Retirar and pagination keep their exact hitboxes.
            if (hovered && h >= 8) {
                graphics.fill(x + 1, y + 1, x + 3, y + h - 1, ACCENT)
            }

            val labelY = y + (h - font.lineHeight) / 2
            graphics.drawCenteredString(font, button.message, x + w / 2, labelY, text)
        }
    }
}
