package com.zianblk.ziangts.v2.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MarketScreenModelTest {
    private val now = Instant.parse("2026-09-22T20:00:00Z")
    private val viewer = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test fun marketShowsOnlyOtherCurrentOffersAndMarksThemBuyable() {
        val own = offer(viewer, "cobblemon:gimmighoul")
        val otherCurrent = offer(other, "cobblemon:charizard")
        val otherExpired = offer(other, "cobblemon:mewtwo", expires = now.minusSeconds(1))
        val book = InMemoryOfferBook().apply { listOf(own, otherCurrent, otherExpired).forEach(::add) }

        val model = book.screen(MarketScreenRequest(viewer, tab = MarketTab.MARKET, now = now))

        assertEquals(listOf(otherCurrent.id), model.entries.map { it.offer.id })
        assertTrue(model.entries.single().canBuy)
        assertFalse(model.entries.single().canWithdraw)
    }

    @Test fun myOffersIncludesExpiredAndMarksThemWithdrawable() {
        val ownCurrent = offer(viewer, "cobblemon:gimmighoul")
        val ownExpired = offer(viewer, "cobblemon:mewtwo", expires = now.minusSeconds(1))
        val other = offer(other, "cobblemon:charizard")
        val book = InMemoryOfferBook().apply { listOf(ownCurrent, ownExpired, other).forEach(::add) }

        val model = book.screen(MarketScreenRequest(viewer, tab = MarketTab.MY_OFFERS, now = now))

        assertEquals(setOf(ownCurrent.id, ownExpired.id), model.entries.map { it.offer.id }.toSet())
        assertTrue(model.entries.all { it.ownedByViewer && it.canWithdraw && !it.canBuy })
    }

    @Test fun paginationMetadataIsStable() {
        val book = InMemoryOfferBook()
        repeat(13) { book.add(offer(other, "cobblemon:test_$it", published = now.minusSeconds(it.toLong()))) }

        val page2 = book.screen(MarketScreenRequest(viewer, page = 2, pageSize = 6, now = now))
        val page3 = book.screen(MarketScreenRequest(viewer, page = 3, pageSize = 6, now = now))

        assertEquals(13, page2.total)
        assertEquals(3, page2.totalPages)
        assertTrue(page2.hasPrevious)
        assertTrue(page2.hasNext)
        assertEquals(6, page2.entries.size)
        assertTrue(page3.hasPrevious)
        assertFalse(page3.hasNext)
        assertEquals(1, page3.entries.size)
    }

    @Test fun emptyMarketStillHasOneDisplayPage() {
        val model = InMemoryOfferBook().screen(MarketScreenRequest(viewer, now = now))
        assertEquals(0, model.total)
        assertEquals(1, model.totalPages)
        assertFalse(model.hasPrevious)
        assertFalse(model.hasNext)
        assertTrue(model.entries.isEmpty())
    }

    @Test fun marketRejectsLegacyOwnFilter() {
        assertThrows(IllegalArgumentException::class.java) {
            MarketScreenRequest(viewer, tab = MarketTab.MARKET, filter = OfferFilter.OWN, now = now)
        }
    }

    private fun offer(
        owner: UUID,
        species: String,
        published: Instant = now.minusSeconds(30),
        expires: Instant = now.plusSeconds(3600)
    ) = TradeOffer(
        OfferId(UUID.randomUUID()), OfferOwner(owner, "Seller"),
        PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 10),
        PokemonEnvelope(UUID.randomUUID(), species, 20, false, false, "{test:true}"),
        published, expires
    )
}
