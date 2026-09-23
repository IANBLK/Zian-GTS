package com.zianblk.ziangts.v2.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class OfferId(val value: UUID)

data class OfferOwner(val playerId: UUID, val displayName: String) {
    init { require(displayName.isNotBlank()) }
}

data class PaymentSpec(val adapter: String, val currency: String, val amount: Long) {
    init {
        require(adapter.matches(Regex("[a-z0-9_.-]+")))
        require(currency.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")))
        require(amount > 0)
    }
}

data class PokemonEnvelope(
    val pokemonId: UUID,
    val species: String,
    val level: Int,
    val shiny: Boolean,
    val alpha: Boolean,
    val gender: String = "UNKNOWN",
    val nature: String = "unknown",
    val ability: String = "unknown",
    val ivs: List<Int> = List(6) { 0 },
    val moves: List<String> = emptyList(),
    val serialized: String = "",
    val legendary: Boolean = false
) {
    init {
        require(species.isNotBlank())
        require(level > 0)
        require(ivs.size == 6)
        require(moves.size <= 4)
        // Legacy/test envelopes may omit the serialized payload; production Cobblemon offers include it.
    }
}

enum class OfferState { CURRENT, EXPIRED }

data class TradeOffer(
    val id: OfferId,
    val owner: OfferOwner,
    val payment: PaymentSpec,
    val pokemon: PokemonEnvelope,
    val publishedAt: Instant,
    val expiresAt: Instant
) {
    init { require(expiresAt.isAfter(publishedAt)) }

    fun stateAt(now: Instant): OfferState =
        if (now.isBefore(expiresAt)) OfferState.CURRENT else OfferState.EXPIRED

    fun purchasableBy(playerId: UUID, now: Instant): Boolean =
        owner.playerId != playerId && stateAt(now) == OfferState.CURRENT
}
