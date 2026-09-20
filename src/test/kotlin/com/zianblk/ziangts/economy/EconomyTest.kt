package com.zianblk.ziangts.economy

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EconomyTest {
    @Test fun `legacy physical currency and wallet earnings have distinct identities`() {
        val physical = EconomyKey.parse("avecoins:goldcoin")
        val wallet = EconomyKey("avecoins_wallet", "avecoins:goldcoin")
        assertEquals("vanilla_item", physical.provider)
        assertNotEquals(physical.storageKey, wallet.storageKey)
        assertEquals(wallet, EconomyKey.parse(wallet.storageKey))
        assertThrows(IllegalArgumentException::class.java) { EconomyKey.parse("unknown|avecoins:goldcoin") }
    }

    @Test fun `wallet capacity includes other currencies slot rounding`() {
        assertEquals(1728L, WalletCapacity.available(emptyMap(), "avecoins:goldcoin"))
        assertEquals(1664L, WalletCapacity.available(mapOf("avecoins:ironcoin" to 1L), "avecoins:goldcoin"))
        assertEquals(1600L, WalletCapacity.available(mapOf("avecoins:ironcoin" to 65L), "avecoins:goldcoin"))
        assertEquals(0L, WalletCapacity.available(mapOf("avecoins:ironcoin" to 1664L, "avecoins:goldcoin" to 64L), "avecoins:goldcoin"))
        assertEquals(1L, WalletCapacity.available(mapOf("avecoins:goldcoin" to 1727L), "avecoins:goldcoin"))
    }

    @Test fun `invalid wallet balances cannot create spendable capacity`() {
        assertThrows(IllegalArgumentException::class.java) { WalletCapacity.available(mapOf("coin" to -1L), "coin") }
        assertThrows(IllegalArgumentException::class.java) { WalletCapacity.available(mapOf("coin" to Long.MAX_VALUE), "coin") }
    }
}
