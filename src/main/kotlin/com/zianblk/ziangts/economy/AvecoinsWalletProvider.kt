package com.zianblk.ziangts.economy

import net.neoforged.fml.ModList
import java.util.UUID

/**
 * Optional interop with AVECOINS 2.3's public WalletStore/WalletData methods.
 * No AVECOINS bytecode or decompiled implementation is included in this project.
 * Exact configured denominations only; automatic coin exchange is not implied.
 */
class AvecoinsWalletProvider(override val currencyId: String) : EconomyProvider {
    companion object {
        /** Returns the exact denomination ids exposed by AVECOINS 2.3. */
        fun supportedCurrencies(): List<String> {
            val mod = ModList.get().getModContainerById("avecoins").orElseThrow {
                IllegalStateException("AVECOINS is not installed")
            }
            check(mod.modInfo.version.toString() == "2.3") { "Only the inspected AVECOINS 2.3 contract is supported" }
            val crafting = Class.forName("net.sundggs.avecoins.config.CraftingConfig")
            val managed = crafting.getField("MANAGED_RESULTS").get(null) as Set<*>
            return managed.filterIsInstance<String>().sorted()
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
        val mod = ModList.get().getModContainerById("avecoins").orElseThrow {
            IllegalStateException("AVECOINS is not installed")
        }
        check(mod.modInfo.version.toString() == "2.3") { "Only the inspected AVECOINS 2.3 contract is supported" }
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

    override fun balance(playerId: UUID): Long = synchronized(store) {
        readBalance.invoke(get.invoke(null), playerId, currencyId) as Long
    }

    override fun capacity(playerId: UUID): Long = synchronized(store) {
        val values = readBalances.invoke(get.invoke(null), playerId) as Map<*, *>
        val balances = values.entries.associate { (key, value) -> (key as String) to (value as Long) }
        WalletCapacity.available(balances, currencyId, slots, stackSize)
    }

    override fun withdraw(playerId: UUID, amount: Long): EconomyResult = synchronized(store) {
        require(amount > 0)
        val candidate = copy.invoke(get.invoke(null))
        if (debit.invoke(candidate, playerId, currencyId, amount) != true) {
            return@synchronized EconomyResult.Failure("insufficient_funds")
        }
        check(save.invoke(null, candidate) == true) { "AVECOINS save outcome is uncertain; do not retry automatically" }
        EconomyResult.Success
    }

    override fun deposit(playerId: UUID, amount: Long): EconomyResult = synchronized(store) {
        require(amount > 0)
        if (capacity(playerId) < amount) return@synchronized EconomyResult.Failure("wallet_full")
        val candidate = copy.invoke(get.invoke(null))
        credit.invoke(candidate, playerId, currencyId, amount)
        check(save.invoke(null, candidate) == true) { "AVECOINS save outcome is uncertain; do not retry automatically" }
        EconomyResult.Success
    }
}

/** Slot-aware capacity: each denomination occupies its own stacks. */
internal object WalletCapacity {
    fun available(balances: Map<String, Long>, currency: String, slots: Int = 27, stackSize: Int = 64): Long {
        require(slots > 0 && stackSize > 0)
        val maximum = Math.multiplyExact(slots.toLong(), stackSize.toLong())
        require(balances.values.all { it in 0..maximum })
        val otherSlots = balances.filterKeys { it != currency }.values.sumOf { (it + stackSize - 1) / stackSize }
        val current = balances[currency] ?: 0
        return ((slots - otherSlots).coerceAtLeast(0) * stackSize - current).coerceAtLeast(0)
    }
}
