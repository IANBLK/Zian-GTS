package com.zianblk.ziangts.v2.domain

import java.util.UUID

data class ProceedsKey(val adapter: String, val currency: String) {
    init {
        require(adapter.matches(Regex("[a-z0-9_.-]+")))
        require(currency.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")))
    }
}

interface ProceedsStore {
    fun balance(owner: UUID, key: ProceedsKey): Long
    fun credit(owner: UUID, key: ProceedsKey, amount: Long)
    fun debit(owner: UUID, key: ProceedsKey, amount: Long)
    fun balances(owner: UUID): Map<ProceedsKey, Long>
}

class InMemoryProceedsStore : ProceedsStore {
    private val values = linkedMapOf<Pair<UUID, ProceedsKey>, Long>()

    override fun balance(owner: UUID, key: ProceedsKey) = values[owner to key] ?: 0L

    override fun credit(owner: UUID, key: ProceedsKey, amount: Long) {
        require(amount > 0)
        values[owner to key] = Math.addExact(balance(owner, key), amount)
    }

    override fun debit(owner: UUID, key: ProceedsKey, amount: Long) {
        require(amount > 0)
        val current = balance(owner, key)
        require(current >= amount) { "insufficient proceeds" }
        val next = current - amount
        if (next == 0L) values.remove(owner to key) else values[owner to key] = next
    }

    override fun balances(owner: UUID): Map<ProceedsKey, Long> =
        values.filterKeys { it.first == owner }.mapKeys { it.key.second }
}
