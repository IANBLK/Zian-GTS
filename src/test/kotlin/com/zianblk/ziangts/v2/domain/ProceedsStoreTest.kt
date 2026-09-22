package com.zianblk.ziangts.v2.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class ProceedsStoreTest {
    private val key = ProceedsKey("avecoins_wallet", "avecoins:coppercoin")

    @Test fun proceedsAccumulateAndDebitIndependently() {
        val owner = UUID.randomUUID()
        val store = InMemoryProceedsStore()
        store.credit(owner, key, 8)
        store.credit(owner, key, 5)
        assertEquals(13, store.balance(owner, key))
        store.debit(owner, key, 10)
        assertEquals(3, store.balance(owner, key))
    }

    @Test fun cannotDebitMoreThanAvailable() {
        val owner = UUID.randomUUID()
        val store = InMemoryProceedsStore()
        store.credit(owner, key, 2)
        assertThrows(IllegalArgumentException::class.java) { store.debit(owner, key, 3) }
        assertEquals(2, store.balance(owner, key))
    }
}
