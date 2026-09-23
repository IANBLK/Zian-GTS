package com.zianblk.ziangts.v2.runtime

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ZianGtsV2RuntimeLifecycleTest {
    @Test
    fun journalStillClosesWhenHistoryCloseFails() {
        var historyClosed = false
        var journalClosed = false
        val history = AutoCloseable {
            historyClosed = true
            throw IllegalStateException("history close failed")
        }
        val journal = AutoCloseable {
            journalClosed = true
        }

        val failure = ZianGtsV2Runtime.closeRuntimeResources(history, journal)

        assertTrue(historyClosed)
        assertTrue(journalClosed)
        assertNotNull(failure)
        assertEquals("history close failed", failure!!.message)
        assertEquals(0, failure.suppressed.size)
    }

    @Test
    fun bothCloseFailuresArePreservedWithoutSkippingJournal() {
        var journalClosed = false
        val history = AutoCloseable {
            throw IllegalStateException("history close failed")
        }
        val journal = AutoCloseable {
            journalClosed = true
            throw IllegalArgumentException("journal close failed")
        }

        val failure = ZianGtsV2Runtime.closeRuntimeResources(history, journal)

        assertTrue(journalClosed)
        assertNotNull(failure)
        assertEquals("history close failed", failure!!.message)
        assertEquals(1, failure.suppressed.size)
        assertEquals("journal close failed", failure.suppressed.single().message)
    }

    @Test
    fun successfulCloseReturnsNoFailure() {
        var historyClosed = false
        var journalClosed = false

        val failure = ZianGtsV2Runtime.closeRuntimeResources(
            AutoCloseable { historyClosed = true },
            AutoCloseable { journalClosed = true }
        )

        assertTrue(historyClosed)
        assertTrue(journalClosed)
        assertNull(failure)
    }
}
