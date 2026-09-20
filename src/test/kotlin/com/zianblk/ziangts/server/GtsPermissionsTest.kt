package com.zianblk.ziangts.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GtsPermissionsTest {
    @Test fun `default players cannot use administrative nodes`() {
        listOf("history", "history.archive", "recovery", "recovery.resolve").forEach {
            assertFalse(GtsPermissions.defaultAccess("ziangts.admin.$it", false))
            assertTrue(GtsPermissions.defaultAccess("ziangts.admin.$it", true))
        }
    }

    @Test fun `ordinary commands remain available without LuckPerms`() {
        listOf("use", "list", "mine", "sell", "buy", "cancel", "claim").forEach {
            assertTrue(GtsPermissions.defaultAccess("ziangts.$it", false))
        }
    }
}
