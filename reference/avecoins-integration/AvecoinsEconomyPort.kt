package com.zianblk.ziangts.v2.runtime

import com.zianblk.ziangts.v2.port.EconomyPort
import com.zianblk.ziangts.v2.port.EconomyResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** V2 economy adapter. TradeEngine stays independent from AVECOINS implementation details. */
class AvecoinsEconomyPort : EconomyPort {
    private val providers = ConcurrentHashMap<String, AvecoinsWallet>()

    override fun canWithdraw(playerId: UUID, currency: String, amount: Long): Boolean {
        if (amount <= 0) return false
        return try { provider(currency).balance(playerId) >= amount }
        catch (_: Exception) { false }
    }

    override fun withdraw(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
        if (amount <= 0) return EconomyResult.Rejected("invalid_amount")
        return mutate(currency) { it.withdraw(playerId, amount) }
    }

    override fun deposit(operationId: UUID, playerId: UUID, currency: String, amount: Long): EconomyResult {
        if (amount <= 0) return EconomyResult.Rejected("invalid_amount")
        return mutate(currency) { it.deposit(playerId, amount) }
    }

    private fun provider(currency: String): AvecoinsWallet =
        providers.computeIfAbsent(currency, ::AvecoinsWallet)

    private inline fun mutate(currency: String, action: (AvecoinsWallet) -> AvecoinsWallet.Mutation): EconomyResult =
        try {
            when (val result = action(provider(currency))) {
                AvecoinsWallet.Mutation.Applied -> EconomyResult.Applied
                is AvecoinsWallet.Mutation.Rejected -> EconomyResult.Rejected(result.reason)
            }
        } catch (error: Exception) {
            EconomyResult.Uncertain("AVECOINS mutation outcome uncertain: ${error.javaClass.simpleName}")
        }
}