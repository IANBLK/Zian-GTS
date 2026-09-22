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
}
