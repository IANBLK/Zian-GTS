package com.zianblk.ziangts.economy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AvecoinsCatalogTest {
    @Test fun `only known AVECOINS denominations are supported`() {
        assertTrue(AvecoinsCatalog.isSupported("avecoins:coppercoin"))
        assertTrue(AvecoinsCatalog.isSupported("avecoins:netheriteticket"))
        assertFalse(AvecoinsCatalog.isSupported("minecraft:diamond"))
        assertFalse(AvecoinsCatalog.isSupported("other:coppercoin"))
    }

    @Test fun `currency identifiers have readable market names`() {
        assertEquals("CopperCoin", AvecoinsCatalog.displayName("avecoins:coppercoin"))
        assertEquals("DiamondTicket", AvecoinsCatalog.displayName("avecoins:diamondticket"))
    }
}
