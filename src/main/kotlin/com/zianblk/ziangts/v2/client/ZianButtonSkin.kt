package com.zianblk.ziangts.v2.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ScreenEvent

/**
 * Static visual skin for Zian GTS buttons.
 *
 * Important: no hover color shift and no NeoForge background manipulation lives here. The market
 * screen disables its native blur in renderBackground(), and this skin only repaints the vanilla
 * button after Screen.render has finished. Hitboxes, clicks, focus and narration remain vanilla.
 */
@EventBusSubscriber(modid = "ziangts", value = [Dist.CLIENT])
object ZianButtonSkin {
    private const val SCREEN_PACKAGE = "com.zianblk.ziangts.v2.client"
    private const val BORDER = 0xFF56616B.toInt()
    private const val BG = 0xFF252B31.toInt()
    private const val BG_DISABLED = 0xFF1B2025.toInt()
    private const val TEXT = 0xFFF1F3F5.toInt()
    private const val TEXT_DISABLED = 0xFF707880.toInt()

    @SubscribeEvent
    fun onRenderPost(event: ScreenEvent.Render.Post) {
        val screen = event.screen
        if (!screen.javaClass.name.startsWith(SCREEN_PACKAGE)) return

        val graphics = event.guiGraphics
        val font = Minecraft.getInstance().font

        screen.children().filterIsInstance<Button>().forEach { button ->
            if (!button.visible || button.width <= 2 || button.height <= 2) return@forEach

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
    }
}
