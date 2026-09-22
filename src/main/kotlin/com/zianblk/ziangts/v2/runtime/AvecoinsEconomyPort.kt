package com.zianblk.ziangts.v2.runtime

import com.zianblk.ziangts.economy.AvecoinsWalletProvider
import com.zianblk.ziangts.economy.EconomyResult as LegacyEconomyResult
import com.zianblk.ziangts.v2.port.EconomyPort
import com.zianblk.ziangts.v2.port.EconomyResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * V2 boundary for AVECOINS.
 *
 * This class deliberately adapts the separately isolated AVECOINS interop component;
 * TradeEngine never depends on AVECOINS, NeoForge reflection or wallet implementation details.
 * operationId is accepted end-to-end so a future AVECOINS API with idempotency can use it.
 */
class AvecoinsEconomyPort(
    private val providerFactory: (String) -> AvecoinsWalletProvider = ::AvecoinsWalletProvider
) : EconomyPort {
    private val providers = ConcurrentHashMap<String, AvecoinsWalletProvider>()

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

    private fun provider(currency: String): AvecoinsWalletProvider =
        providers.computeIfAbsent(currency, providerFactory)

    private inline fun mutate(currency: String, action: (AvecoinsWalletProvider) -> LegacyEconomyResult): EconomyResult =
        try {
            when (val result = action(provider(currency))) {
                LegacyEconomyResult.Success -> EconomyResult.Applied
                is LegacyEconomyResult.Failure -> EconomyResult.Rejected(result.reason)
            }
        } catch (error: Exception) {
            // The old isolated provider throws when AVECOINS save() cannot prove the outcome.
            // Never translate that into Rejected: retrying could duplicate or erase money.
            EconomyResult.Uncertain("AVECOINS mutation outcome uncertain: ${error.javaClass.simpleName}")
        }
}
