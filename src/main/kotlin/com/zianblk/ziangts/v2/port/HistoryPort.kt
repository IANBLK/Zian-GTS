package com.zianblk.ziangts.v2.port

import com.zianblk.ziangts.v2.domain.OfferId
import com.zianblk.ziangts.v2.domain.PaymentSpec
import java.time.Instant
import java.util.UUID

data class TradeHistoryRecord(
    val operationId: UUID,
    val offerId: OfferId,
    val sellerId: UUID,
    val buyerId: UUID,
    val pokemonId: UUID,
    val species: String,
    val payment: PaymentSpec,
    val completedAt: Instant
)

interface HistoryPort {
    fun append(record: TradeHistoryRecord)
    fun all(): List<TradeHistoryRecord>

    /** Completed trades involving one player, newest first, bounded without requiring callers to scan globally. */
    fun forPlayer(playerId: UUID, limit: Int): List<TradeHistoryRecord> {
        require(limit > 0) { "history limit must be positive" }
        return all().asSequence()
            .filter { it.sellerId == playerId || it.buyerId == playerId }
            .sortedByDescending { it.completedAt }
            .take(limit)
            .toList()
    }
}
