package com.zianblk.ziangts.v2.port

import java.util.UUID

/**
 * External economy boundary for the independent V2 core.
 *
 * operationId is stable for one market transaction and is reserved for
 * adapters that can provide idempotency now or in the future.
 */
interface EconomyPort {
    fun canWithdraw(playerId: UUID, currency: String, amount: Long): Boolean
    fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult
    fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult
}

sealed interface EconomyResult {
    data object Applied : EconomyResult
    data class Rejected(val reason: String) : EconomyResult
    data class Uncertain(val reason: String) : EconomyResult
}
