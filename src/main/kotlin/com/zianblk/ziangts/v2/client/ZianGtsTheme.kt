package com.zianblk.ziangts.v2.client

import net.minecraft.client.gui.GuiGraphics

/** Shared colors and background for GTS screens, aligned with Zian Utilities. */
internal object ZianGtsTheme {
    const val GOLD = 0xFFF0C75E.toInt()
    const val BACKGROUND = 0xE00D1117.toInt()
    const val BUTTON_BACKGROUND = 0xFF20262D.toInt()
    const val BUTTON_DISABLED = 0xFF161A20.toInt()
    const val BUTTON_BORDER = 0xFF526575.toInt()
    const val BUTTON_DISABLED_BORDER = 0xFF3B4148.toInt()
    const val TEXT = 0xFFF4F7FA.toInt()
    const val TEXT_DISABLED = 0xFF7C838B.toInt()

    fun background(graphics: GuiGraphics, width: Int, height: Int) {
        graphics.fill(0, 0, width, height, BACKGROUND)
        graphics.fill(0, 0, width, 4, GOLD)
    }
}

