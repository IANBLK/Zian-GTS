package com.zianblk.ziangts.v2.runtime

import net.neoforged.fml.ModList
import java.util.UUID

/**
 * Narrow AVECOINS interop boundary used by V2.
 *
 * The integration is reflective on purpose: Zian GTS does not package AVECOINS
 * implementation classes and only relies on the WalletStore/WalletData/CraftingConfig
 * contract (checked by signature, not by version number). A failed save is treated as an uncertain external mutation.
 */
class AvecoinsWallet internal constructor(
    private val currencyId: String, crafting: Class<*>, private val store: Class<*>, private val data: Class<*>,
) {
    constructor(currencyId: String) : this(currencyId, cls("config.CraftingConfig"), cls("shop.WalletStore"), cls("shop.WalletData"))

    sealed interface Mutation {
        data object Applied : Mutation
        data class Rejected(val reason: String) : Mutation
    }

    companion object {
        /**
         * Contract fingerprint: every class, field and method signature GTS calls. Any version
         * (2.3, 3.0, 4.1...) that matches is accepted; otherwise the list says what is missing.
         */
        internal fun missingContract(crafting: Class<*>?, store: Class<*>?, data: Class<*>?): List<String> {
            val missing = mutableListOf<String>()
            if (crafting == null) missing += "class CraftingConfig" else missing += requireMember("CraftingConfig.MANAGED_RESULTS") {
                static(crafting.getField("MANAGED_RESULTS"), "MANAGED_RESULTS")
            }
            if (store == null) missing += "class WalletStore"
            if (data == null) missing += "class WalletData"
            if (store == null || data == null) return missing
            val bool = Boolean::class.javaPrimitiveType
            val long = Long::class.javaPrimitiveType
            missing += requireMember("WalletStore.get()") { check(static(store.getMethod("get"), "get").returnType == data) }
            missing += requireMember("WalletStore.save(WalletData)") {
                check(static(store.getMethod("save", data), "save").returnType == bool)
            }
            missing += requireMember("WalletData.copy()") { check(data.getMethod("copy").returnType == data) }
            missing += requireMember("WalletData.balance(UUID,String)") {
                check(data.getMethod("balance", UUID::class.java, String::class.java).returnType == long)
            }
            missing += requireMember("WalletData.balances(UUID)") {
                check(Map::class.java.isAssignableFrom(data.getMethod("balances", UUID::class.java).returnType))
            }
            missing += requireMember("WalletData.credit(UUID,String,long)") {
                check(data.getMethod("credit", UUID::class.java, String::class.java, long).returnType == Void.TYPE)
            }
            missing += requireMember("WalletData.debit(UUID,String,long)") {
                check(data.getMethod("debit", UUID::class.java, String::class.java, long).returnType == bool)
            }
            missing += requireMember("WalletData.SLOT_COUNT=27/STACK_SIZE=64") {
                check(data.getField("SLOT_COUNT").getInt(null) == 27 && data.getField("STACK_SIZE").getInt(null) == 64)
            }
            return missing
        }

        private fun <T : java.lang.reflect.Member> static(member: T, name: String): T {
            check(java.lang.reflect.Modifier.isStatic(member.modifiers)) { "$name not static" }
            return member
        }

        private inline fun requireMember(name: String, block: () -> Unit): List<String> =
            try { block(); emptyList() } catch (_: Exception) { listOf(name) } catch (_: LinkageError) { listOf(name) }

        private fun cls(name: String): Class<*> { verifyContract(); return Class.forName("net.sundggs.avecoins.$name") }

        private fun load(name: String): Class<*>? =
            try { Class.forName(name) } catch (_: ClassNotFoundException) { null } catch (_: LinkageError) { null }

        fun supportedCurrencies(): List<String> {
            verifyContract()
            val crafting = Class.forName("net.sundggs.avecoins.config.CraftingConfig")
            val managed = crafting.getField("MANAGED_RESULTS").get(null) as Set<*>
            return managed.filterIsInstance<String>().sorted()
        }

        /** Refuse wallets whose contract does not match before the market accepts any trade. */
        fun verifyContract(): String {
            val mod = ModList.get().getModContainerById("avecoins").orElseThrow {
                IllegalStateException("AVECOINS is not installed")
            }
            val version = mod.modInfo.version.toString()
            val crafting = load("net.sundggs.avecoins.config.CraftingConfig")
            val missing = missingContract(crafting, load("net.sundggs.avecoins.shop.WalletStore"),
                load("net.sundggs.avecoins.shop.WalletData")).toMutableList()
            if (missing.isEmpty()) {
                val managed = crafting!!.getField("MANAGED_RESULTS").get(null)
                if (!(managed is Set<*> && managed.isNotEmpty() && managed.all { it is String && it.isNotBlank() })) {
                    missing += "CraftingConfig.MANAGED_RESULTS (non-empty Set<String>)"
                }
            }
            check(missing.isEmpty()) {
                "AVECOINS $version does not match the GTS wallet contract; missing or changed: ${missing.joinToString()}"
            }
            return version
        }
    }

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
        check(missingContract(crafting, store, data).isEmpty()) { "AVECOINS wallet contract mismatch" }
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

    /** Candidate must hold exactly the expected balance and the live wallet must be untouched; otherwise nothing is saved. */
    private fun requireOutcome(live: Any?, candidate: Any?, playerId: UUID, before: Long, expected: Long) =
        check(readBalance.invoke(candidate, playerId, currencyId) as Long == expected &&
            readBalance.invoke(live, playerId, currencyId) as Long == before) { "AVECOINS postcondition failed; nothing saved" }

    fun withdraw(playerId: UUID, amount: Long): Mutation = synchronized(store) {
        require(amount > 0)
        val live = get.invoke(null)
        val candidate = copy.invoke(live)
        val before = readBalance.invoke(live, playerId, currencyId) as Long
        if (debit.invoke(candidate, playerId, currencyId, amount) != true) {
            return@synchronized Mutation.Rejected("insufficient_funds")
        }
        requireOutcome(live, candidate, playerId, before, before - amount)
        check(save.invoke(null, candidate) == true) {
            "AVECOINS save outcome is uncertain; do not retry automatically"
        }
        Mutation.Applied
    }

    fun deposit(playerId: UUID, amount: Long): Mutation = synchronized(store) {
        require(amount > 0)
        if (capacity(playerId) < amount) return@synchronized Mutation.Rejected("wallet_full")
        val live = get.invoke(null)
        val candidate = copy.invoke(live)
        val before = readBalance.invoke(live, playerId, currencyId) as Long
        credit.invoke(candidate, playerId, currencyId, amount)
        requireOutcome(live, candidate, playerId, before, before + amount)
        check(save.invoke(null, candidate) == true) {
            "AVECOINS save outcome is uncertain; do not retry automatically"
        }
        Mutation.Applied
    }
}
