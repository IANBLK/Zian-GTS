package com.zianblk.ziangts.v2.network

import com.zianblk.ziangts.v2.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class MarketProtocolTest {
    private val viewer = UUID.randomUUID()
    private val now = Instant.parse("2026-09-22T20:00:00Z")

    @Test fun requestUsesAuthenticatedViewerInsteadOfClientSuppliedIdentity() {
        val request = MarketPageRequest(tab = MarketTab.MARKET, page = 2, pageSize = 6,
            filter = OfferFilter.SHINY, sort = OfferSort.PRICE_LOW)

        val screen = request.toScreenRequest(viewer, now)

        assertEquals(viewer, screen.viewer)
        assertEquals(2, screen.page)
        assertEquals(OfferFilter.SHINY, screen.filter)
        assertEquals(OfferSort.PRICE_LOW, screen.sort)
        assertEquals(now, screen.now)
    }

    @Test fun rejectsUnsupportedProtocolAndInvalidMarketOwnFilter() {
        assertThrows(IllegalArgumentException::class.java) { MarketPageRequest(protocolVersion = 999) }
        assertThrows(IllegalArgumentException::class.java) {
            MarketPageRequest(tab = MarketTab.MARKET, filter = OfferFilter.OWN)
        }
    }

    @Test fun responseContainsOnlyUiProjectionNotPokemonNbt() {
        val seller = UUID.randomUUID()
        val offer = TradeOffer(
            OfferId(UUID.randomUUID()), OfferOwner(seller, "Seller"),
            PaymentSpec("avecoins_wallet", "avecoins:coppercoin", 42),
            PokemonEnvelope(UUID.randomUUID(), "cobblemon:rayquaza", 80, true, false, "{secret_nbt:true}"),
            now.minusSeconds(60), now.plusSeconds(3600)
        )
        val model = MarketScreenModel(
            tab = MarketTab.MARKET,
            entries = listOf(MarketScreenEntry(offer, false, true, false)),
            total = 1, page = 1, pageSize = 6, totalPages = 1,
            hasPrevious = false, hasNext = false,
            filter = OfferFilter.SHINY, sort = OfferSort.NEWEST
        )

        val response = model.toPageResponse()
        val dto = response.entries.single()

        assertEquals(offer.id.value, dto.offerId)
        assertEquals("cobblemon:rayquaza", dto.species)
        assertEquals(42, dto.price)
        assertTrue(dto.canBuy)
        assertFalse(dto.canWithdraw)
        assertFalse(MarketEntryDto::class.java.declaredFields.any { it.name.contains("nbt", ignoreCase = true) })
    }
}
