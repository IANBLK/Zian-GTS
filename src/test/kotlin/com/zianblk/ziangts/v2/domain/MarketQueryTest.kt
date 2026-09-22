package com.zianblk.ziangts.v2.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MarketQueryTest {
    private val now = Instant.parse("2026-09-22T16:00:00Z")
    private val viewer = UUID.randomUUID()
    private val other = UUID.randomUUID()

    @Test fun publicMarketExcludesExpiredOffersButOwnIncludesThem() {
        val current = offer(other, "cobblemon:gimmighoul", 20, 10, shiny = false, alpha = false, published = now.minusSeconds(60), expires = now.plusSeconds(60))
        val expiredOwn = offer(viewer, "cobblemon:mewtwo", 70, 100, shiny = true, alpha = false, published = now.minusSeconds(7200), expires = now.minusSeconds(1))
        val book = InMemoryOfferBook().apply { add(current); add(expiredOwn) }

        val public = book.query(MarketQuery(viewer = viewer, now = now))
        val own = book.query(MarketQuery(viewer = viewer, filter = OfferFilter.OWN, now = now))

        assertEquals(listOf(current.id), public.offers.map { it.id })
        assertEquals(listOf(expiredOwn.id), own.offers.map { it.id })
    }

    @Test fun filtersShinyAlphaLegendaryAndLegendaryShiny() {
        val shiny = offer(other, "cobblemon:gimmighoul", 20, 10, shiny = true, alpha = false)
        val alpha = offer(other, "cobblemon:charizard", 50, 20, shiny = false, alpha = true)
        val legendary = offer(other, "cobblemon:mewtwo", 70, 30, shiny = false, alpha = false)
        val legendaryShiny = offer(other, "cobblemon:rayquaza", 80, 40, shiny = true, alpha = false)
        val book = InMemoryOfferBook().apply { listOf(shiny, alpha, legendary, legendaryShiny).forEach(::add) }

        assertEquals(setOf(shiny.id, legendaryShiny.id), ids(book, OfferFilter.SHINY))
        assertEquals(setOf(alpha.id), ids(book, OfferFilter.ALPHA))
        assertEquals(setOf(legendary.id, legendaryShiny.id), ids(book, OfferFilter.LEGENDARY))
        assertEquals(setOf(legendaryShiny.id), ids(book, OfferFilter.LEGENDARY_SHINY))
    }


    @Test fun ownershipProjectionComposesWithPokemonFilters() {
        val ownShiny = offer(viewer, "cobblemon:gimmighoul", 20, 10, shiny = true)
        val otherShiny = offer(other, "cobblemon:rayquaza", 80, 40, shiny = true)
        val otherNormal = offer(other, "cobblemon:charizard", 50, 20)
        val book = InMemoryOfferBook().apply { listOf(ownShiny, otherShiny, otherNormal).forEach(::add) }

        val mine = book.query(MarketQuery(viewer, filter = OfferFilter.SHINY, ownership = OfferOwnership.MINE, now = now))
        val others = book.query(MarketQuery(viewer, filter = OfferFilter.SHINY, ownership = OfferOwnership.OTHERS, now = now))

        assertEquals(listOf(ownShiny.id), mine.offers.map { it.id })
        assertEquals(listOf(otherShiny.id), others.offers.map { it.id })
    }

    @Test fun ownershipOthersNeverLeaksExpiredOffers() {
        val currentOther = offer(other, "cobblemon:gimmighoul", 20, 10)
        val expiredOther = offer(other, "cobblemon:mewtwo", 70, 100, expires = now.minusSeconds(1))
        val book = InMemoryOfferBook().apply { add(currentOther); add(expiredOther) }

        val result = book.query(MarketQuery(viewer, ownership = OfferOwnership.OTHERS, now = now))

        assertEquals(listOf(currentOther.id), result.offers.map { it.id })
    }

    @Test fun sortingAndPaginationAreStable() {
        val a = offer(other, "cobblemon:a", 10, 30, published = now.minusSeconds(30))
        val b = offer(other, "cobblemon:b", 30, 10, published = now.minusSeconds(20))
        val c = offer(other, "cobblemon:c", 20, 20, published = now.minusSeconds(10))
        val book = InMemoryOfferBook().apply { listOf(a, b, c).forEach(::add) }

        val lowPrice = book.query(MarketQuery(viewer, page = 1, pageSize = 2, sort = OfferSort.PRICE_LOW, now = now))
        val page2 = book.query(MarketQuery(viewer, page = 2, pageSize = 2, sort = OfferSort.PRICE_LOW, now = now))
        val highLevel = book.query(MarketQuery(viewer, sort = OfferSort.LEVEL_HIGH, now = now))

        assertEquals(3, lowPrice.total)
        assertEquals(listOf(b.id, c.id), lowPrice.offers.map { it.id })
        assertEquals(listOf(a.id), page2.offers.map { it.id })
        assertEquals(listOf(b.id, c.id, a.id), highLevel.offers.map { it.id })
    }

    @Test fun pagePastEndIsEmptyWithoutError() {
        val book = InMemoryOfferBook().apply { add(offer(other, "cobblemon:gimmighoul", 20, 10)) }
        val result = book.query(MarketQuery(viewer, page = 99, pageSize = 6, now = now))
        assertTrue(result.offers.isEmpty())
        assertEquals(1, result.total)
    }

    private fun ids(book: OfferBook, filter: OfferFilter) =
        book.query(MarketQuery(viewer, filter = filter, now = now)).offers.map { it.id }.toSet()

    private fun offer(
        owner: UUID,
        species: String,
        level: Int,
        price: Long,
        shiny: Boolean = false,
        alpha: Boolean = false,
        published: Instant = now.minusSeconds(30),
        expires: Instant = now.plusSeconds(3600)
    ) = TradeOffer(
        OfferId(UUID.randomUUID()), OfferOwner(owner, "Seller"),
        PaymentSpec("avecoins_wallet", "avecoins:coppercoin", price),
        PokemonEnvelope(UUID.randomUUID(), species, level, shiny, alpha, "{test:true}"),
        published, expires
    )
}
