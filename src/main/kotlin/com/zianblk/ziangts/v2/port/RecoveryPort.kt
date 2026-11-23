package com.zianblk.ziangts.v2.port

import com.google.gson.GsonBuilder
import com.zianblk.ziangts.v2.domain.TradeOffer
import java.time.Instant
import java.util.UUID

/** Everything needed to restore by hand a Pokemon whose journal evidence could not be persisted. */
data class RecoveryRecord(
    val ticket: JournalTicket,
    val stage: TradeStage,
    val offer: TradeOffer,
    val buyerId: UUID?,
    val sellerId: UUID,
    val at: Instant,
    val error: String
) {
    fun toJson(): String {
        val p = offer.pokemon
        val data = linkedMapOf<String, Any?>(
            "formatVersion" to 1,
            "ticket" to ticket.operationId.toString(),
            "operation" to ticket.operation.name,
            "failedStage" to stage.name,
            // Solo compra: PAYMENT_APPLIED y BEFORE_POKEMON_DELIVERY ocurren tras cobrar; withdraw no tiene comprador.
            "paymentApplied" to (buyerId != null && (stage == TradeStage.PAYMENT_APPLIED || stage == TradeStage.BEFORE_POKEMON_DELIVERY)),
            "offerId" to offer.id.value.toString(),
            "buyerId" to buyerId?.toString(),
            "sellerId" to sellerId.toString(),
            "price" to mapOf("adapter" to offer.payment.adapter, "currency" to offer.payment.currency, "amount" to offer.payment.amount),
            "timestamp" to at.toString(),
            "error" to error,
            "pokemon" to linkedMapOf(
                "pokemonId" to p.pokemonId.toString(), "species" to p.species, "level" to p.level,
                "shiny" to p.shiny, "alpha" to p.alpha, "gender" to p.gender, "nature" to p.nature,
                "ability" to p.ability, "ivs" to p.ivs, "moves" to p.moves, "legendary" to p.legendary,
                "serialized" to p.serialized
            )
        )
        return GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create().toJson(data)
    }
}

interface RecoveryPort {
    /** Must persist durably or throw. */
    fun dump(record: RecoveryRecord)
}
