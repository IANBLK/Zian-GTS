package com.zianblk.ziangts.v2.runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GtsPermissionsTest {
    @Test
    fun explicitSellDenialWinsOverPlayerAndOperatorDefaults() {
        val nodes = listOf("use", "sell")
        val lookup: (String) -> GtsPermissions.Decision = { node ->
            if (node == "ziangts.sell") GtsPermissions.Decision.FALSE else GtsPermissions.Decision.UNDEFINED
        }
        assertFalse(GtsPermissions.evaluate(true, nodes, lookup))
        assertFalse(GtsPermissions.evaluate(false, nodes, lookup))
    }

    @Test
    fun missingAdministrativeNodeRequiresOperatorDefaultOrExplicitGrant() {
        val nodes = listOf("use", "admin.recovery.resolve")
        assertFalse(GtsPermissions.evaluate(false, nodes) { GtsPermissions.Decision.UNDEFINED })
        assertTrue(GtsPermissions.evaluate(true, nodes) { GtsPermissions.Decision.UNDEFINED })
        assertTrue(GtsPermissions.evaluate(false, nodes) {
            if (it == "ziangts.admin.recovery.resolve") GtsPermissions.Decision.TRUE else GtsPermissions.Decision.UNDEFINED
        })
    }

    @Test
    fun providerFailureDoesNotAuthorizeTrade() {
        assertFalse(GtsPermissions.evaluate(true, listOf("use", "buy")) {
            throw IllegalStateException("LuckPerms unavailable")
        })
    }
}
