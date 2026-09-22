package com.zianblk.ziangts.v2.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class TradeOfferTest {
    private fun offer(owner: UUID = UUID.randomUUID()): TradeOffer {
        val now = Instant.parse("2026-09-22T00:00:00Z")
        return TradeOffer(
            OfferId(UUID.randomUUID()),
            OfferOwner(owner, "Seller"),
            PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 8),
            PokemonEnvelope(UUID.randomUUID(), "cobblemon:gimmighoul", 17, false, false, "{pokemon:test}"),
            now,
            now.plus(48, ChronoUnit.HOURS)
        )
    }

    @Test fun ownerCannotPurchaseOwnOffer() {
        val owner = UUID.randomUUID()
        val offer = offer(owner)
        assertFalse(offer.purchasableBy(owner, offer.publishedAt.plusSeconds(1)))
    }

    @Test fun expiredOfferCannotBePurchased() {
        val offer = offer()
        assertEquals(OfferState.EXPIRED, offer.stateAt(offer.expiresAt))
        assertFalse(offer.purchasableBy(UUID.randomUUID(), offer.expiresAt))
    }

    @Test fun bookRejectsSamePokemonTwice() {
        val book = InMemoryOfferBook()
        val first = offer()
        book.add(first)
        val duplicate = first.copy(id = OfferId(UUID.randomUUID()))
        assertThrows(IllegalArgumentException::class.java) { book.add(duplicate) }
    }

    @Test fun paymentMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 0)
        }
    }
}
