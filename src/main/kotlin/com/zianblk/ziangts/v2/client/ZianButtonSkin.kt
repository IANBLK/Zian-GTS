package com.zianblk.ziangts.v2.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ScreenEvent

/**
 * AVESHOP-inspired visual skin for vanilla Buttons used by Zian GTS screens.
 *
 * The vanilla widget is still responsible for input, focus, narration and its label. We only
 * repaint the frame/background in Render.Post. Keeping the text in one renderer avoids the
 * doubled/overprinted labels that appeared when the skin drew the message a second time.
 */
@EventBusSubscriber(modid = "ziangts", value = [Dist.CLIENT])
object ZianButtonSkin {
    private const val SCREEN_PACKAGE = "com.zianblk.ziangts.v2.client"

    private const val BORDER = 0xFF4E5963.toInt()
    private const val BORDER_HOVER = 0xFFF0C75E.toInt()
    private const val BG = 0xFF252B31.toInt()
    private const val BG_HOVER = 0xFF343C44.toInt()
    private const val BG_DISABLED = 0xFF1B2025.toInt()
    private const val ACCENT = 0xFFF0C75E.toInt()

    @SubscribeEvent
    fun onOpening(event: ScreenEvent.Opening) {
        val screen = event.newScreen ?: return
        if (!screen.javaClass.name.startsWith(SCREEN_PACKAGE)) return

        // NeoForge/Minecraft can blur the already-rendered world behind menu screens. Zian GTS
        // deliberately uses its own opaque/translucent backgrounds, so the post-processing blur
        // only makes the market/cards look smeared. Reset it whenever one of our screens opens.
        runCatching {
            val gameRenderer = Minecraft.getInstance().gameRenderer
            val method = gameRenderer.javaClass.methods.firstOrNull {
                it.name == "shutdownEffect" && it.parameterCount == 0
            }
            method?.invoke(gameRenderer)
        }
    }

    @SubscribeEvent
    fun onRenderPost(event: ScreenEvent.Render.Post) {
        val screen = event.screen
        if (!screen.javaClass.name.startsWith(SCREEN_PACKAGE)) return

        val graphics = event.guiGraphics

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

            // Repaint only the frame/background. The vanilla Button already rendered its message,
            // so drawing button.message here would render every label twice.
            graphics.fill(x, y, x + w, y + h, border)
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, background)

            if (hovered && h >= 8) {
                graphics.fill(x + 1, y + 1, x + 3, y + h - 1, ACCENT)
            }

            // Render one clean label after repainting the background. We intentionally blank the
            // widget only for this draw pass conceptually: at this stage vanilla text is covered by
            // the background above, so this is the sole visible copy.
            val font = Minecraft.getInstance().font
            val textColor = when {
                !button.active -> 0xFF707880.toInt()
                hovered -> 0xFFFFE39A.toInt()
                else -> 0xFFF1F3F5.toInt()
            }
            val labelY = y + (h - font.lineHeight) / 2
            graphics.drawCenteredString(font, Component.literal(button.message.string), x + w / 2, labelY, textColor)
        }
    }
}
