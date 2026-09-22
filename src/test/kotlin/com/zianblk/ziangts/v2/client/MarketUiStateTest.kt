package com.zianblk.ziangts.v2.client

import com.zianblk.ziangts.v2.domain.MarketTab
import com.zianblk.ziangts.v2.domain.OfferFilter
import com.zianblk.ziangts.v2.domain.OfferSort
import com.zianblk.ziangts.v2.network.MARKET_PROTOCOL_VERSION
import com.zianblk.ziangts.v2.network.MarketPageResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MarketUiStateTest {
    @Test
    fun switchingTabsResetsPageAndUsesServerCompatibleFilter() {
        val market = MarketUiState(page = 3, filter = OfferFilter.SHINY)
        val mine = market.switchTab(MarketTab.MY_OFFERS)

        assertEquals(MarketTab.MY_OFFERS, mine.tab)
        assertEquals(1, mine.page)
        assertEquals(OfferFilter.OWN, mine.filter)
        assertNull(mine.response)

        val back = mine.switchTab(MarketTab.MARKET)
        assertEquals(MarketTab.MARKET, back.tab)
        assertEquals(OfferFilter.ALL, back.filter)
        assertEquals(1, back.page)
    }

    @Test
    fun filterAndSortChangesResetPagination() {
        val state = MarketUiState(page = 4)
        val filtered = state.withFilter(OfferFilter.SHINY)
        assertEquals(1, filtered.page)
        assertEquals(OfferFilter.SHINY, filtered.filter)

        val sorted = state.withSort(OfferSort.PRICE_LOW)
        assertEquals(1, sorted.page)
        assertEquals(OfferSort.PRICE_LOW, sorted.sort)
    }

    @Test
    fun marketCannotUseLegacyOwnFilter() {
        assertThrows(IllegalArgumentException::class.java) {
            MarketUiState(tab = MarketTab.MARKET, filter = OfferFilter.OWN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MarketUiState().withFilter(OfferFilter.OWN)
        }
    }

    @Test
    fun navigationHonorsServerPageBounds() {
        val response = response(page = 2, totalPages = 3)
        val state = MarketUiState(page = 2).accept(response)

        assertEquals(1, state.previousPage().page)
        assertEquals(3, state.nextPage().page)

        val last = state.copy(page = 3, response = response(page = 3, totalPages = 3))
        assertSame(last, last.nextPage())

        val first = MarketUiState()
        assertSame(first, first.previousPage())
    }

    @Test
    fun acceptClearsLoadingAndRejectsWrongTab() {
        val loading = MarketUiState().beginRequest()
        assertTrue(loading.loading)

        val accepted = loading.accept(response(page = 1, totalPages = 1))
        assertFalse(accepted.loading)

        assertThrows(IllegalArgumentException::class.java) {
            MarketUiState(tab = MarketTab.MY_OFFERS, filter = OfferFilter.OWN)
                .accept(response(page = 1, totalPages = 1))
        }
    }

    private fun response(page: Int, totalPages: Int) = MarketPageResponse(
        protocolVersion = MARKET_PROTOCOL_VERSION,
        tab = MarketTab.MARKET,
        entries = emptyList(),
        total = if (totalPages == 0) 0 else totalPages * 6,
        page = page,
        pageSize = 6,
        totalPages = totalPages,
        hasPrevious = page > 1,
        hasNext = page < totalPages,
        filter = OfferFilter.ALL,
        sort = OfferSort.NEWEST
    )
}
