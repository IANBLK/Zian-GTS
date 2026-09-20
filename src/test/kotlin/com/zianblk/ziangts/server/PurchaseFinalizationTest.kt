package com.zianblk.ziangts.server

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PurchaseFinalizationTest {
    @Test fun `credit failure is quarantined without retry or history append`() {
        var credits = 0
        var history = 0
        val failure = IllegalStateException("credit failed")
        val incidents = mutableListOf<Pair<String, Exception>>()
        assertDoesNotThrow {
            PurchaseFinalization.complete({ credits++; throw failure }, { history++ },
                { operation, error -> incidents.add(operation to error) })
        }
        assertEquals(1, credits)
        assertEquals(0, history)
        assertEquals(listOf("buy_credit" to failure), incidents)
    }

    @Test fun `history failure retains completed credit and is not retried`() {
        var credits = 0
        var writes = 0
        val failure = IllegalStateException("history failed")
        val incidents = mutableListOf<Pair<String, Exception>>()
        assertDoesNotThrow {
            PurchaseFinalization.complete({ credits++ }, { writes++; throw failure },
                { operation, error -> incidents.add(operation to error) })
        }
        assertEquals(1, credits)
        assertEquals(1, writes)
        assertEquals(listOf("buy_history" to failure), incidents)
    }

    @Test fun `successful finalization credits before recording without quarantine`() {
        val operations = mutableListOf<String>()
        PurchaseFinalization.complete({ operations.add("credit") }, { operations.add("history") },
            { _, _ -> fail("Unexpected quarantine") })
        assertEquals(listOf("credit", "history"), operations)
    }
}
