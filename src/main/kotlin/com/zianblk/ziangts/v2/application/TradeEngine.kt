package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

sealed interface TradeResult {
    data class Success(val offer: TradeOffer) : TradeResult
    data class Rejected(val reason: String) : TradeResult
    data class Quarantined(val operationId: UUID, val reason: String) : TradeResult
}

class TradeEngine(
    private val offers: OfferBook,
    private val pokemon: PokemonPort,
    private val economy: EconomyPort,
    private val proceeds: ProceedsStore,
    private val journal: TradeJournalPort,
    private val clock: Clock,
    private val maxOffersPerPlayer: Int = 20,
    private val offerLifetime: Duration = Duration.ofHours(48),
    private val faultHook: (TradeStage) -> Unit = {}
) {
    init {
        require(maxOffersPerPlayer > 0)
        require(!offerLifetime.isZero && !offerLifetime.isNegative)
    }

    private var mutating = false

    fun publish(
        sellerId: UUID,
        sellerName: String,
        pokemonId: UUID,
        payment: PaymentSpec
    ): TradeResult = mutate {
        if (offers.countOwnedBy(sellerId) >= maxOffersPerPlayer)
            return@mutate TradeResult.Rejected("offer limit reached")
        if (offers.containsPokemon(pokemonId))
            return@mutate TradeResult.Rejected("pokemon already offered")

        val envelope = pokemon.inspectOwned(sellerId, pokemonId)
            ?: return@mutate TradeResult.Rejected("pokemon is not owned by seller")

        val ticket = journal.begin(TradeOperation.PUBLISH, pokemonId)
        journal.stage(ticket, TradeStage.BEFORE_POKEMON_REMOVE)
        when (val removed = pokemon.removeOwned(ticket.operationId, sellerId, pokemonId)) {
            PokemonMutation.Applied -> journal.stage(ticket, TradeStage.POKEMON_REMOVED)
            is PokemonMutation.Rejected -> {
                journal.quarantine(ticket, "pokemon removal rejected after journal begin: ${removed.reason}")
                return@mutate TradeResult.Quarantined(ticket.operationId, removed.reason)
            }
            is PokemonMutation.Uncertain -> {
                journal.quarantine(ticket, "pokemon removal uncertain: ${removed.reason}")
                return@mutate TradeResult.Quarantined(ticket.operationId, removed.reason)
            }
        }

        val now = Instant.now(clock)
        val offer = TradeOffer(
            OfferId(UUID.randomUUID()),
            OfferOwner(sellerId, sellerName),
            payment,
            envelope,
            now,
            now.plus(offerLifetime)
        )
        try {
            offers.add(offer)
            journal.stage(ticket, TradeStage.RUNTIME_COMPLETE)
            journal.complete(ticket)
            TradeResult.Success(offer)
        } catch (error: Exception) {
            journal.quarantine(ticket, "offer persistence failed after pokemon removal: ${error.javaClass.simpleName}")
            TradeResult.Quarantined(ticket.operationId, "offer persistence failed")
        }
    }

    fun purchase(buyerId: UUID, offerId: OfferId): TradeResult = mutate {
        val offer = offers.find(offerId) ?: return@mutate TradeResult.Rejected("offer not found")
        val now = Instant.now(clock)
        if (!offer.purchasableBy(buyerId, now))
            return@mutate TradeResult.Rejected("offer is not purchasable")
        if (!economy.canWithdraw(buyerId, offer.payment.currency, offer.payment.amount))
            return@mutate TradeResult.Rejected("insufficient funds")

        val ticket = journal.begin(TradeOperation.PURCHASE, offerId.value)

        // Reserve first so a reentrant/double purchase cannot observe the offer as available.
        val reserved = offers.remove(offerId)
            ?: return@mutate TradeResult.Rejected("offer disappeared")

        journal.stage(ticket, TradeStage.BEFORE_PAYMENT)
        when (val charged = economy.withdraw(
            ticket.operationId, buyerId, reserved.payment.currency, reserved.payment.amount
        )) {
            EconomyResult.Applied -> {
                journal.stage(ticket, TradeStage.PAYMENT_APPLIED)
                faultHook(TradeStage.PAYMENT_APPLIED)
            }
            is EconomyResult.Rejected -> {
                // Payment is known not to have happened, so restoring the reservation is safe.
                offers.add(reserved)
                journal.quarantine(ticket, "payment rejected after reservation: ${charged.reason}")
                return@mutate TradeResult.Quarantined(ticket.operationId, charged.reason)
            }
            is EconomyResult.Uncertain -> {
                journal.quarantine(ticket, "payment outcome uncertain: ${charged.reason}")
                return@mutate TradeResult.Quarantined(ticket.operationId, charged.reason)
            }
        }

        journal.stage(ticket, TradeStage.BEFORE_POKEMON_DELIVERY)
        when (val delivered = pokemon.deliver(ticket.operationId, buyerId, reserved.pokemon)) {
            PokemonMutation.Applied -> {
                journal.stage(ticket, TradeStage.POKEMON_DELIVERED)
                faultHook(TradeStage.POKEMON_DELIVERED)
            }
            is PokemonMutation.Rejected -> {
                journal.quarantine(ticket, "pokemon delivery rejected after payment: ${delivered.reason}")
                return@mutate TradeResult.Quarantined(ticket.operationId, delivered.reason)
            }
            is PokemonMutation.Uncertain -> {
                journal.quarantine(ticket, "pokemon delivery uncertain after payment: ${delivered.reason}")
                return@mutate TradeResult.Quarantined(ticket.operationId, delivered.reason)
            }
        }

        try {
            proceeds.credit(
                reserved.owner.playerId,
                ProceedsKey(reserved.payment.adapter, reserved.payment.currency),
                reserved.payment.amount
            )
            journal.stage(ticket, TradeStage.PROCEEDS_CREDITED)
            journal.stage(ticket, TradeStage.RUNTIME_COMPLETE)
            journal.complete(ticket)
            TradeResult.Success(reserved)
        } catch (error: Exception) {
            journal.quarantine(ticket, "seller proceeds failed after delivery: ${error.javaClass.simpleName}")
            TradeResult.Quarantined(ticket.operationId, "seller proceeds failed")
        }
    }

    fun withdraw(ownerId: UUID, offerId: OfferId): TradeResult = mutate {
        val offer = offers.find(offerId) ?: return@mutate TradeResult.Rejected("offer not found")
        if (offer.owner.playerId != ownerId) return@mutate TradeResult.Rejected("offer is not owned by player")

        val ticket = journal.begin(TradeOperation.WITHDRAW, offerId.value)
        val removed = offers.remove(offerId)
            ?: return@mutate TradeResult.Rejected("offer disappeared")

        journal.stage(ticket, TradeStage.BEFORE_POKEMON_DELIVERY)
        when (val delivered = pokemon.deliver(ticket.operationId, ownerId, removed.pokemon)) {
            PokemonMutation.Applied -> {
                journal.stage(ticket, TradeStage.POKEMON_DELIVERED)
                journal.stage(ticket, TradeStage.RUNTIME_COMPLETE)
                journal.complete(ticket)
                TradeResult.Success(removed)
            }
            is PokemonMutation.Rejected -> {
                journal.quarantine(ticket, "pokemon return rejected after offer reservation: ${delivered.reason}")
                TradeResult.Quarantined(ticket.operationId, delivered.reason)
            }
            is PokemonMutation.Uncertain -> {
                journal.quarantine(ticket, "pokemon return uncertain: ${delivered.reason}")
                TradeResult.Quarantined(ticket.operationId, delivered.reason)
            }
        }
    }

    private fun mutate(action: () -> TradeResult): TradeResult {
        if (mutating) return TradeResult.Rejected("market mutation already in progress")
        mutating = true
        return try { action() } finally { mutating = false }
    }
}
