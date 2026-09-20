package com.zianblk.ziangts.data

import java.time.Instant
import java.util.UUID

/**
 * Immutable audit record for a completed GTS purchase.
 *
 * pokemonSnapshot is intentionally opaque to the history layer. The Cobblemon
 * adapter will provide a stable serialized snapshot so later investigations
 * do not depend on the Pokemon still existing in a player's storage.
 */
data class TransactionRecord(
    val transactionId: UUID,
    val listingId: UUID,
    val buyerId: UUID,
    val buyerName: String,
    val sellerId: UUID,
    val sellerName: String,
    val amount: Long,
    val currency: String,
    val pokemonSnapshot: String,
    val completedAt: Instant,
    val economyProvider: String = "vanilla_item"
) {
    fun toNbt(): net.minecraft.nbt.CompoundTag = net.minecraft.nbt.CompoundTag().apply {
        putUUID("transactionId", transactionId)
        putUUID("listingId", listingId)
        putUUID("buyerId", buyerId)
        putString("buyerName", buyerName)
        putUUID("sellerId", sellerId)
        putString("sellerName", sellerName)
        putLong("amount", amount)
        putString("currency", currency)
        putString("economyProvider", economyProvider)
        putString("pokemonSnapshot", pokemonSnapshot)
        putLong("completedAt", completedAt.toEpochMilli())
    }

    init {
        require(buyerName.isNotBlank()) { "buyerName must not be blank" }
        require(sellerName.isNotBlank()) { "sellerName must not be blank" }
        require(amount > 0) { "amount must be greater than zero" }
        require(currency.isNotBlank()) { "currency must not be blank" }
        require(pokemonSnapshot.isNotBlank()) { "pokemonSnapshot must not be blank" }
    }
}
