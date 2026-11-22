package com.zianblk.ziangts.v2.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

/** The contract is matched by class/method signatures, so any AVECOINS version with them (2.5, 3.0, 4.1...) is accepted. */
class AvecoinsWalletVersionTest {
    class FakeCrafting { companion object { @JvmField val MANAGED_RESULTS: Set<String> = setOf("avecoins:coppercoin") } }
    class FakeData {
        companion object { const val SLOT_COUNT = 27; const val STACK_SIZE = 64 }
        fun copy(): FakeData = FakeData()
        fun balance(id: UUID, currency: String): Long = 0
        fun balances(id: UUID): Map<String, Long> = emptyMap()
        fun credit(id: UUID, currency: String, amount: Long) {}
        fun debit(id: UUID, currency: String, amount: Long): Boolean = true
    }
    object FakeStore {
        @JvmStatic fun get(): FakeData = FakeData()
        @JvmStatic fun save(data: FakeData): Boolean = true
    }
    // Same shape as FakeStore but save() takes another type and get() is an instance method.
    class BrokenStore { fun get(): FakeData = FakeData(); fun save(data: String): Boolean = true }
    class DataWithoutDebit { fun copy() = this; fun balance(id: UUID, c: String): Long = 0; fun balances(id: UUID): Map<String, Long> = emptyMap(); fun credit(id: UUID, c: String, a: Long) {} }
    class DataWrongDebit { fun copy() = this; fun balance(id: UUID, c: String): Long = 0; fun balances(id: UUID): Map<String, Long> = emptyMap(); fun credit(id: UUID, c: String, a: Long) {}; fun debit(id: UUID, c: String, a: Long): Long = 0 }

    @Test
    fun fullContractIsAcceptedRegardlessOfVersionNumber() {
        // The version string is not part of the decision: same result for 2.5, 3.0 and 4.1.
        for (version in listOf("2.3", "2.5", "3.0", "4.1")) {
            assertEquals(emptyList<String>(), AvecoinsWallet.missingContract(
                FakeCrafting::class.java, FakeStore.javaClass, FakeData::class.java), version)
        }
    }

    @Test
    fun missingMethodIsRejectedAndNamed() {
        val missing = AvecoinsWallet.missingContract(FakeCrafting::class.java, FakeStore.javaClass, DataWithoutDebit::class.java)
        assertTrue(missing.any { it.startsWith("WalletData.debit") }, missing.toString())
    }

    @Test
    fun changedSignatureIsRejected() {
        assertTrue(AvecoinsWallet.missingContract(FakeCrafting::class.java, FakeStore.javaClass, DataWrongDebit::class.java)
            .any { it.startsWith("WalletData.debit") })
        assertTrue(AvecoinsWallet.missingContract(FakeCrafting::class.java, BrokenStore::class.java, FakeData::class.java)
            .any { it.startsWith("WalletStore") })
    }

    @Test
    fun missingClassesAreRejected() {
        assertEquals(listOf("class CraftingConfig", "class WalletStore", "class WalletData"),
            AvecoinsWallet.missingContract(null, null, null))
    }
}
