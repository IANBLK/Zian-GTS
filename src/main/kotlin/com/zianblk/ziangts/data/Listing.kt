package com.zianblk.ziangts.data

import java.time.Instant
import java.util.UUID

/**
 * Stable server-side identity and commercial metadata for one GTS listing.
 *
 * Pokemon serialization is deliberately kept outside this model until the
 * Cobblemon 1.8 serialization API is wired and tested.
 */
data class Listing(
    val id: UUID,
    val sellerId: UUID,
    val sellerName: String,
    val price: Long,
    val currency: String,
    val createdAt: Instant,
    val expiresAt: Instant?
) {
    init {
        require(sellerName.isNotBlank()) { "sellerName must not be blank" }
        require(price > 0) { "price must be greater than zero" }
        require(currency.isNotBlank()) { "currency must not be blank" }
        require(expiresAt == null || expiresAt.isAfter(createdAt)) {
            "expiresAt must be after createdAt"
        }
    }

    fun isExpired(now: Instant = Instant.now()): Boolean =
        expiresAt?.let { !now.isBefore(it) } ?: false
}
