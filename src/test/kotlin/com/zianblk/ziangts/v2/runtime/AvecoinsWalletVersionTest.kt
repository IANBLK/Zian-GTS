package com.zianblk.ziangts.v2.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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

    open class WData {
        companion object { const val SLOT_COUNT = 27; const val STACK_SIZE = 64 }
        var coins = 10L
        open fun copy(): WData = WData().also { it.coins = coins }
        fun balance(id: UUID, currency: String): Long = coins
        fun balances(id: UUID): Map<String, Long> = emptyMap()
        open fun credit(id: UUID, currency: String, amount: Long) { coins += amount }
        open fun debit(id: UUID, currency: String, amount: Long): Boolean { coins -= amount; return true }
    }
    class SameCopyData : WData() { override fun copy(): WData = this }
    class ClippingData : WData() {
        override fun copy(): WData = ClippingData().also { it.coins = coins }
        override fun credit(id: UUID, currency: String, amount: Long) { coins += amount - 1 }
        override fun debit(id: UUID, currency: String, amount: Long): Boolean { coins -= amount - 1; return true }
    }
    object WStore {
        var data = WData(); var saves = 0
        @JvmStatic fun get(): WData = data
        @JvmStatic fun save(candidate: WData): Boolean { saves++; data = candidate; return true }
    }

    private fun walletWith(data: WData): AvecoinsWallet {
        WStore.data = data; WStore.saves = 0
        return AvecoinsWallet("avecoins:coppercoin", FakeCrafting::class.java, WStore.javaClass, WData::class.java)
    }

    @Test
    fun withdrawAndDepositFailWithoutSavingWhenCopyIsNotACopy() {
        val id = UUID.randomUUID()
        val wallet = walletWith(SameCopyData())
        assertThrows(IllegalStateException::class.java) { wallet.withdraw(id, 5) }
        assertThrows(IllegalStateException::class.java) { wallet.deposit(id, 5) }
        assertEquals(0, WStore.saves)
        assertEquals(10L, WStore.data.coins)
    }

    @Test
    fun withdrawAndDepositFailWithoutSavingWhenWalletClipsSilently() {
        val id = UUID.randomUUID()
        val wallet = walletWith(ClippingData())
        assertThrows(IllegalStateException::class.java) { wallet.withdraw(id, 5) }
        assertThrows(IllegalStateException::class.java) { wallet.deposit(id, 5) }
        assertEquals(0, WStore.saves)
    }

    @Test
    fun honestWalletStillWorks() {
        val wallet = walletWith(WData())
        assertEquals(AvecoinsWallet.Mutation.Applied, wallet.withdraw(UUID.randomUUID(), 4))
        assertEquals(6L, WStore.data.coins)
    }

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
