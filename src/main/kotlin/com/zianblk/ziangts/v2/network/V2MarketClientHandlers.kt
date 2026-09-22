package com.zianblk.ziangts.v2.network

import com.zianblk.ziangts.v2.client.V2MarketClientScreens

/**
 * Client-only packet actions kept out of the common network registration class.
 */
object V2MarketClientHandlers {
    fun openMarketScreen() {
        V2MarketClientScreens.openMarket()
    }
}
