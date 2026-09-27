package com.zianblk.ziangts.v2.runtime

import net.neoforged.fml.ModList
import java.util.UUID

/**
 * Narrow AVECOINS interop boundary used by V2.
 *
 * The integration is reflective on purpose: Zian GTS does not package AVECOINS
 * implementation classes and only relies on the inspected WalletStore/WalletData
 * contract. A failed save is treated as an uncertain external mutation.
 */
class AvecoinsWallet(private val currencyId: String) {
    sealed interface Mutation {
        data object Applied : Mutation
        data class Rejected(val reason: String) : Mutation
    }

    companion object {
        private val supportedVersions = setOf("2.3", "2.4")

        internal fun supportsVersion(version: String): Boolean = version in supportedVersions

        fun supportedCurrencies(): List<String> {
            verifyContract()
            val crafting = Class.forName("net.sundggs.avecoins.config.CraftingConfig")
            val managed = crafting.getField("MANAGED_RESULTS").get(null) as Set<*>
            return managed.filterIsInstance<String>().sorted()
        }

        /** Refuse uninspected wallet versions before the market accepts any trade. */
        fun verifyContract(): String {
            val mod = ModList.get().getModContainerById("avecoins").orElseThrow {
                IllegalStateException("AVECOINS is not installed")
            }
            val version = mod.modInfo.version.toString()
            check(supportsVersion(version)) {
                "AVECOINS $version is not inspected; supported versions: 2.3, 2.4"
            }
            val crafting = Class.forName("net.sundggs.avecoins.config.CraftingConfig")
            val store = Class.forName("net.sundggs.avecoins.shop.WalletStore")
            val data = Class.forName("net.sundggs.avecoins.shop.WalletData")
            val managedField = crafting.getField("MANAGED_RESULTS")
            check(java.lang.reflect.Modifier.isStatic(managedField.modifiers)) { "AVECOINS currencies changed" }
            val managed = managedField.get(null)
            check(managed is Set<*> && managed.isNotEmpty() && managed.all { it is String && it.isNotBlank() }) {
                "AVECOINS currencies changed"
            }
            val getMethod = store.getMethod("get")
            val saveMethod = store.getMethod("save", data)
            check(java.lang.reflect.Modifier.isStatic(getMethod.modifiers) && getMethod.returnType == data)
            check(java.lang.reflect.Modifier.isStatic(saveMethod.modifiers)
                && saveMethod.returnType == Boolean::class.javaPrimitiveType)
            check(data.getMethod("copy").returnType == data)
            check(data.getMethod("balance", UUID::class.java, String::class.java).returnType == Long::class.javaPrimitiveType)
            check(Map::class.java.isAssignableFrom(data.getMethod("balances", UUID::class.java).returnType))
            check(data.getMethod("credit", UUID::class.java, String::class.java, Long::class.javaPrimitiveType).returnType == Void.TYPE)
            check(data.getMethod("debit", UUID::class.java, String::class.java, Long::class.javaPrimitiveType).returnType == Boolean::class.javaPrimitiveType)
            check(data.getField("SLOT_COUNT").getInt(null) == 27 && data.getField("STACK_SIZE").getInt(null) == 64) {
                "Unexpected AVECOINS wallet layout"
            }
            return version
        }
    }

    private val store: Class<*>
    private val data: Class<*>
    private val get: java.lang.reflect.Method
    private val copy: java.lang.reflect.Method
    private val save: java.lang.reflect.Method
    private val readBalance: java.lang.reflect.Method
    private val readBalances: java.lang.reflect.Method
    private val credit: java.lang.reflect.Method
    private val debit: java.lang.reflect.Method
    private val slots: Int
    private val stackSize: Int

    init {
        verifyContract()
        store = Class.forName("net.sundggs.avecoins.shop.WalletStore")
        data = Class.forName("net.sundggs.avecoins.shop.WalletData")
        val crafting = Class.forName("net.sundggs.avecoins.config.CraftingConfig")
        val managed = crafting.getField("MANAGED_RESULTS").get(null) as Set<*>
        require(currencyId in managed) { "Unsupported AVECOINS currency" }

        get = store.getMethod("get")
        copy = data.getMethod("copy")
        save = store.getMethod("save", data)
        readBalance = data.getMethod("balance", UUID::class.java, String::class.java)
        readBalances = data.getMethod("balances", UUID::class.java)
        credit = data.getMethod("credit", UUID::class.java, String::class.java, Long::class.javaPrimitiveType)
        debit = data.getMethod("debit", UUID::class.java, String::class.java, Long::class.javaPrimitiveType)
        slots = data.getField("SLOT_COUNT").getInt(null)
        stackSize = data.getField("STACK_SIZE").getInt(null)
        check(slots == 27 && stackSize == 64) { "Unexpected AVECOINS wallet layout" }
    }

    fun balance(playerId: UUID): Long = synchronized(store) {
        readBalance.invoke(get.invoke(null), playerId, currencyId) as Long
    }

    private fun capacity(playerId: UUID): Long = synchronized(store) {
        val values = readBalances.invoke(get.invoke(null), playerId) as Map<*, *>
        val balances = values.entries.associate { (key, value) -> (key as String) to (value as Long) }
        val maximum = Math.multiplyExact(slots.toLong(), stackSize.toLong())
        require(balances.values.all { it in 0..maximum })
        val otherSlots = balances.filterKeys { it != currencyId }.values
            .sumOf { (it + stackSize - 1) / stackSize }
        val current = balances[currencyId] ?: 0
        ((slots - otherSlots).coerceAtLeast(0) * stackSize - current).coerceAtLeast(0)
    }

    fun withdraw(playerId: UUID, amount: Long): Mutation = synchronized(store) {
        require(amount > 0)
        val candidate = copy.invoke(get.invoke(null))
        if (debit.invoke(candidate, playerId, currencyId, amount) != true) {
            return@synchronized Mutation.Rejected("insufficient_funds")
        }
        check(save.invoke(null, candidate) == true) {
            "AVECOINS save outcome is uncertain; do not retry automatically"
        }
        Mutation.Applied
    }

    fun deposit(playerId: UUID, amount: Long): Mutation = synchronized(store) {
        require(amount > 0)
        if (capacity(playerId) < amount) return@synchronized Mutation.Rejected("wallet_full")
        val candidate = copy.invoke(get.invoke(null))
        credit.invoke(candidate, playerId, currencyId, amount)
        check(save.invoke(null, candidate) == true) {
            "AVECOINS save outcome is uncertain; do not retry automatically"
        }
        Mutation.Applied
    }
}

