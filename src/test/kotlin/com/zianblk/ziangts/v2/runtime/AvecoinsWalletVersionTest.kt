package com.zianblk.ziangts.v2.runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AvecoinsWalletVersionTest {
    @Test
    fun onlyReviewedVersionsMayTrade() {
        assertTrue(AvecoinsWallet.supportsVersion("2.3"))
        assertTrue(AvecoinsWallet.supportsVersion("2.4"))
        assertFalse(AvecoinsWallet.supportsVersion("2.5"))
        assertFalse(AvecoinsWallet.supportsVersion("3.0"))
    }
}

