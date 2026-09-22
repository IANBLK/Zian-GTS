package com.zianblk.ziangts.v2.application

import com.zianblk.ziangts.v2.domain.*
import com.zianblk.ziangts.v2.port.*
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

sealed interface ClaimResult {
    data class Success(val amount: Long, val key: ProceedsKey) : ClaimResult
    data class Rejected(val reason: String) : ClaimResult
    data class Quarantined(val operationId: UUID, val reason: String) : ClaimResult
}

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
    private val history: HistoryPort? = null,
    private val maxOffersPerPlayer: Int = 20,
    private val offerLifetime: Duration = Duration.ofHours(48),
    private val faultHook: (TradeStage) -> Unit = {},
    private val mutationThreadCheck: () -> Boolean = { true }
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

        val envelope = try {
            pokemon.inspectOwned(sellerId, pokemonId)
        } catch (error: Exception) {
            return@mutate TradeResult.Rejected("pokemon ownership check failed")
        } ?: return@mutate TradeResult.Rejected("pokemon is not owned by seller")

        val ticket = try {
            journal.begin(TradeOperation.PUBLISH, pokemonId)
        } catch (error: Exception) {
            return@mutate TradeResult.Rejected("journal unavailable")
        }
        try {
            journal.stage(ticket, TradeStage.BEFORE_POKEMON_REMOVE)
        } catch (error: Exception) {
            return@mutate TradeResult.Quarantined(ticket.operationId, "journal persistence failed before pokemon removal")
        }
        val removed = try {
            pokemon.removeOwned(ticket.operationId, sellerId, pokemonId)
        } catch (error: Exception) {
            journal.quarantine(ticket, "pokemon adapter threw during removal: ${error.javaClass.simpleName}")
            return@mutate TradeResult.Quarantined(ticket.operationId, "pokemon removal outcome uncertain")
        }
        when (removed) {
            PokemonMutation.Applied -> journal.stage(ticket, TradeStage.POKEMON_REMOVED)
            is PokemonMutation.Rejected -> {
                // Rejected means the adapter knows removal did not happen.
                journal.abort(ticket, "pokemon removal rejected with no external side effect: ${removed.reason}")
                return@mutate TradeResult.Rejected(removed.reason)
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
        } catch (error: Exception) {
            journal.quarantine(ticket, "offer persistence failed after pokemon removal: ${error.javaClass.simpleName}")
            return@mutate TradeResult.Quarantined(ticket.operationId, "offer persistence failed")
        }

        // The offer is already durable here. Journal failures must not be mislabeled as market persistence failures.
        try {
            journal.stage(ticket, TradeStage.RUNTIME_COMPLETE)
        } catch (error: Exception) {
            try {
                journal.quarantine(ticket, "journal runtime-complete stage failed after offer persistence: ${error.javaClass.simpleName}")
            } catch (quarantineError: Exception) {
                error.addSuppressed(quarantineError)
                throw error
            }
            return@mutate TradeResult.Quarantined(ticket.operationId, "journal persistence failed after offer creation")
        }

        try {
            journal.complete(ticket)
        } catch (error: Exception) {
            // Do not try to roll back the durable offer or disguise this as an OfferBook failure.
            throw error
        }
        TradeResult.Success(offer)
    }

    fun purchase(buyerId: UUID, offerId: OfferId): TradeResult = mutate {
        val offer = offers.find(offerId) ?: return@mutate TradeResult.Rejected("offer not found")
        val now = Instant.now(clock)
        if (!offer.purchasableBy(buyerId, now))
            return@mutate TradeResult.Rejected("offer is not purchasable")
        val canWithdraw = try {
            economy.canWithdraw(buyerId, offer.payment.currency, offer.payment.amount)
        } catch (error: Exception) {
            return@mutate TradeResult.Rejected("economy availability check failed")
        }
        if (!canWithdraw) return@mutate TradeResult.Rejected("insufficient funds")

        val ticket = try {
            journal.begin(TradeOperation.PURCHASE, offerId.value)
        } catch (error: Exception) {
            return@mutate TradeResult.Rejected("journal unavailable")
        }

        // Reserve first so a reentrant/double purchase cannot observe the offer as available.
        val reserved = offers.remove(offerId)
        if (reserved == null) {
            journal.abort(ticket, "offer disappeared before reservation; no external side effect")
            return@mutate TradeResult.Rejected("offer disappeared")
        }

        journal.stage(ticket, TradeStage.BEFORE_PAYMENT)
        val charged = try {
            economy.withdraw(ticket.operationId, buyerId, reserved.payment.currency, reserved.payment.amount)
        } catch (error: Exception) {
            journal.quarantine(ticket, "payment adapter threw after reservation: ${error.javaClass.simpleName}")
            return@mutate TradeResult.Quarantined(ticket.operationId, "payment outcome uncertain")
        }
        when (charged) {
            EconomyResult.Applied -> {
                journal.stage(ticket, TradeStage.PAYMENT_APPLIED)
                faultHook(TradeStage.PAYMENT_APPLIED)
            }
            is EconomyResult.Rejected -> {
                // Payment is known not to have happened, so restoring the reservation is safe.
                try {
                    offers.add(reserved)
                } catch (error: Exception) {
                    journal.quarantine(ticket, "payment rejected but offer restoration failed: ${error.javaClass.simpleName}")
                    return@mutate TradeResult.Quarantined(ticket.operationId, "offer restoration failed")
                }
                journal.abort(ticket, "payment rejected and offer reservation restored: ${charged.reason}")
                return@mutate TradeResult.Rejected(charged.reason)
            }
            is EconomyResult.Uncertain -> {
                journal.quarantine(ticket, "payment outcome uncertain: ${charged.reason}")
                return@mutate TradeResult.Quarantined(ticket.operationId, charged.reason)
            }
        }

        journal.stage(ticket, TradeStage.BEFORE_POKEMON_DELIVERY)
        val delivered = try {
            pokemon.deliver(ticket.operationId, buyerId, reserved.pokemon)
        } catch (error: Exception) {
            journal.quarantine(ticket, "pokemon adapter threw after payment: ${error.javaClass.simpleName}")
            return@mutate TradeResult.Quarantined(ticket.operationId, "pokemon delivery outcome uncertain")
        }
        when (delivered) {
            PokemonMutation.Applied -> {
                journal.stage(ticket, TradeStage.POKEMON_DELIVERED)
                faultHook(TradeStage.POKEMON_DELIVERED)
            }
            is PokemonMutation.Rejected -> {
                // Delivery is known not to have happened. Compensate the already-applied payment,
                // then restore the reserved offer. Any uncertain/failed compensation must quarantine.
                val refunded = try {
                    economy.deposit(ticket.operationId, buyerId, reserved.payment.currency, reserved.payment.amount)
                } catch (error: Exception) {
                    journal.quarantine(ticket, "pokemon delivery rejected but refund adapter threw: ${error.javaClass.simpleName}")
                    return@mutate TradeResult.Quarantined(ticket.operationId, "buyer refund outcome uncertain")
                }
                when (refunded) {
                    EconomyResult.Applied -> Unit
                    is EconomyResult.Rejected -> {
                        journal.quarantine(ticket, "pokemon delivery rejected but buyer refund was rejected: ${refunded.reason}")
                        return@mutate TradeResult.Quarantined(ticket.operationId, "buyer refund rejected")
                    }
                    is EconomyResult.Uncertain -> {
                        journal.quarantine(ticket, "pokemon delivery rejected but buyer refund is uncertain: ${refunded.reason}")
                        return@mutate TradeResult.Quarantined(ticket.operationId, "buyer refund outcome uncertain")
                    }
                }
                try {
                    offers.add(reserved)
                } catch (error: Exception) {
                    journal.quarantine(ticket, "buyer refunded after delivery rejection but offer restoration failed: ${error.javaClass.simpleName}")
                    return@mutate TradeResult.Quarantined(ticket.operationId, "offer restoration failed after buyer refund")
                }
                journal.abort(ticket, "pokemon delivery rejected; buyer refunded and offer restored: ${delivered.reason}")
                return@mutate TradeResult.Rejected(delivered.reason)
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
        } catch (error: Exception) {
            journal.quarantine(ticket, "seller proceeds persistence failed after delivery: ${error.javaClass.simpleName}")
            return@mutate TradeResult.Quarantined(ticket.operationId, "seller proceeds persistence failed")
        }

        journal.stage(ticket, TradeStage.PROCEEDS_CREDITED)
        faultHook(TradeStage.PROCEEDS_CREDITED)

        try {
            history?.append(
                TradeHistoryRecord(
                    ticket.operationId,
                    reserved.id,
                    reserved.owner.playerId,
                    buyerId,
                    reserved.pokemon.pokemonId,
                    reserved.pokemon.species,
                    reserved.payment,
                    Instant.now(clock)
                )
            )
        } catch (error: Exception) {
            journal.quarantine(ticket, "history persistence failed after proceeds credit: ${error.javaClass.simpleName}")
            return@mutate TradeResult.Quarantined(ticket.operationId, "history persistence failed")
        }

        journal.stage(ticket, TradeStage.HISTORY_APPENDED)
        faultHook(TradeStage.HISTORY_APPENDED)
        journal.stage(ticket, TradeStage.RUNTIME_COMPLETE)
        journal.complete(ticket)
        TradeResult.Success(reserved)
    }

    fun claim(ownerId: UUID, key: ProceedsKey): ClaimResult = mutateClaim {
        val amount = try {
            proceeds.balance(ownerId, key)
        } catch (error: Exception) {
            return@mutateClaim ClaimResult.Rejected("proceeds availability check failed")
        }
        if (amount <= 0) return@mutateClaim ClaimResult.Rejected("no proceeds available")

        val ticket = try {
            journal.begin(TradeOperation.CLAIM, ownerId)
        } catch (error: Exception) {
            return@mutateClaim ClaimResult.Rejected("journal unavailable")
        }
        // Reserve internally first. A second/reentrant claim can no longer see this balance.
        try {
            proceeds.debit(ownerId, key, amount)
        } catch (error: Exception) {
            journal.quarantine(ticket, "proceeds reservation failed: ${error.javaClass.simpleName}")
            return@mutateClaim ClaimResult.Quarantined(ticket.operationId, "proceeds reservation failed")
        }
        journal.stage(ticket, TradeStage.PROCEEDS_RESERVED)
        faultHook(TradeStage.PROCEEDS_RESERVED)
        journal.stage(ticket, TradeStage.BEFORE_PAYMENT)

        val deposited = try {
            economy.deposit(ticket.operationId, ownerId, key.currency, amount)
        } catch (error: Exception) {
            journal.quarantine(ticket, "claim payment adapter threw after proceeds reservation: ${error.javaClass.simpleName}")
            return@mutateClaim ClaimResult.Quarantined(ticket.operationId, "claim payment outcome uncertain")
        }
        return@mutateClaim when (deposited) {
            EconomyResult.Applied -> {
                journal.stage(ticket, TradeStage.PAYMENT_APPLIED)
                faultHook(TradeStage.PAYMENT_APPLIED)
                journal.stage(ticket, TradeStage.RUNTIME_COMPLETE)
                journal.complete(ticket)
                ClaimResult.Success(amount, key)
            }
            is EconomyResult.Rejected -> {
                // Known non-application: restoring our reserved proceeds is safe.
                try {
                    proceeds.credit(ownerId, key, amount)
                } catch (error: Exception) {
                    journal.quarantine(ticket, "claim deposit rejected but proceeds restoration failed: ${error.javaClass.simpleName}")
                    return@mutateClaim ClaimResult.Quarantined(ticket.operationId, "proceeds restoration failed")
                }
                journal.abort(ticket, "claim deposit rejected and proceeds restored: ${deposited.reason}")
                ClaimResult.Rejected(deposited.reason)
            }
            is EconomyResult.Uncertain -> {
                // Never restore on uncertainty: doing so could allow a duplicate payout.
                journal.quarantine(ticket, "claim deposit outcome uncertain: ${deposited.reason}")
                ClaimResult.Quarantined(ticket.operationId, deposited.reason)
            }
        }
    }

    fun withdraw(ownerId: UUID, offerId: OfferId): TradeResult = mutate {
        val offer = offers.find(offerId) ?: return@mutate TradeResult.Rejected("offer not found")
        if (offer.owner.playerId != ownerId) return@mutate TradeResult.Rejected("offer is not owned by player")

        val ticket = try {
            journal.begin(TradeOperation.WITHDRAW, offerId.value)
        } catch (error: Exception) {
            return@mutate TradeResult.Rejected("journal unavailable")
        }
        val removed = offers.remove(offerId)
        if (removed == null) {
            journal.abort(ticket, "offer disappeared before withdrawal reservation; no external side effect")
            return@mutate TradeResult.Rejected("offer disappeared")
        }

        journal.stage(ticket, TradeStage.BEFORE_POKEMON_DELIVERY)
        val delivered = try {
            pokemon.deliver(ticket.operationId, ownerId, removed.pokemon)
        } catch (error: Exception) {
            journal.quarantine(ticket, "pokemon adapter threw during offer return: ${error.javaClass.simpleName}")
            return@mutate TradeResult.Quarantined(ticket.operationId, "pokemon return outcome uncertain")
        }
        when (delivered) {
            PokemonMutation.Applied -> {
                journal.stage(ticket, TradeStage.POKEMON_DELIVERED)
                journal.stage(ticket, TradeStage.RUNTIME_COMPLETE)
                journal.complete(ticket)
                TradeResult.Success(removed)
            }
            is PokemonMutation.Rejected -> {
                // Delivery is known not to have happened, so restoring the offer is safe.
                try {
                    offers.add(removed)
                } catch (error: Exception) {
                    journal.quarantine(ticket, "pokemon return rejected but offer restoration failed: ${error.javaClass.simpleName}")
                    return@mutate TradeResult.Quarantined(ticket.operationId, "offer restoration failed")
                }
                journal.abort(ticket, "pokemon return rejected and offer reservation restored: ${delivered.reason}")
                TradeResult.Rejected(delivered.reason)
            }
            is PokemonMutation.Uncertain -> {
                journal.quarantine(ticket, "pokemon return uncertain: ${delivered.reason}")
                TradeResult.Quarantined(ticket.operationId, delivered.reason)
            }
        }
    }

    private fun mutateClaim(action: () -> ClaimResult): ClaimResult {
        check(mutationThreadCheck()) { "TradeEngine mutation attempted outside the authorized server thread" }
        if (mutating) return ClaimResult.Rejected("market mutation already in progress")
        mutating = true
        return try { action() } finally { mutating = false }
    }

    private fun mutate(action: () -> TradeResult): TradeResult {
        check(mutationThreadCheck()) { "TradeEngine mutation attempted outside the authorized server thread" }
        if (mutating) return TradeResult.Rejected("market mutation already in progress")
        mutating = true
        return try { action() } finally { mutating = false }
    }
}
