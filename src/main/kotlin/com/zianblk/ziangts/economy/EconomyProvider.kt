package com.zianblk.ziangts.economy

import java.util.UUID

/**
 * Economy boundary used by GTS purchases.
 *
 * AVECOINS will be implemented as an adapter. Keeping the core behind this
 * interface prevents economy-specific code from leaking into listing logic.
 */
interface EconomyProvider {
    val currencyId: String

    fun balance(playerId: UUID): Long

    fun capacity(playerId: UUID): Long

    fun withdraw(playerId: UUID, amount: Long): EconomyResult

    fun deposit(playerId: UUID, amount: Long): EconomyResult
}

sealed interface EconomyResult {
    data object Success : EconomyResult
    data class Failure(val reason: String) : EconomyResult
}

