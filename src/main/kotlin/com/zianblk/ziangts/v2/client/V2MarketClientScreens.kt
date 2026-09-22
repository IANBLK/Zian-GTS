package com.zianblk.ziangts.v2.client

import net.minecraft.client.Minecraft

/**
 * Client-only bridge for V2 screen operations.
 *
 * Network registration must invoke this bridge only on the physical client.
 * Keeping Minecraft client classes here prevents the server-side runtime and
 * business layers from acquiring client dependencies.
 */
object V2MarketClientScreens {
    fun openMarket() {
        val minecraft = Minecraft.getInstance()
        minecraft.execute {
            minecraft.setScreen(MarketScreen())
        }
    }
}
