package com.zianblk.ziangts.v2.client

import com.zianblk.ziangts.v2.domain.MarketTab
import com.zianblk.ziangts.v2.domain.OfferFilter
import com.zianblk.ziangts.v2.domain.OfferSort
import com.zianblk.ziangts.v2.network.MarketPageResponse

/**
 * Pure client presentation state for the future V2 market screen.
 *
 * It contains only navigation/filter choices and the latest server projection.
 * Business decisions remain server authoritative.
 */
data class MarketUiState(
    val tab: MarketTab = MarketTab.MARKET,
    val page: Int = 1,
    val pageSize: Int = 6,
    val filter: OfferFilter = OfferFilter.ALL,
    val sort: OfferSort = OfferSort.NEWEST,
    val response: MarketPageResponse? = null,
    val loading: Boolean = false
) {
    init {
        require(page >= 1)
        require(pageSize in 1..50)
        require(tab != MarketTab.MARKET || filter != OfferFilter.OWN)
    }

    fun beginRequest(): MarketUiState = copy(loading = true)

    fun accept(next: MarketPageResponse): MarketUiState {
        require(next.tab == tab) { "response tab does not match current UI tab" }
        return copy(
            page = next.page,
            pageSize = next.pageSize,
            filter = next.filter,
            sort = next.sort,
            response = next,
            loading = false
        )
    }

    fun switchTab(next: MarketTab): MarketUiState = when (next) {
        MarketTab.MARKET -> copy(tab = next, page = 1, filter = OfferFilter.ALL, response = null)
        MarketTab.MY_OFFERS -> copy(tab = next, page = 1, filter = OfferFilter.OWN, response = null)
    }

    fun withFilter(next: OfferFilter): MarketUiState {
        require(tab != MarketTab.MARKET || next != OfferFilter.OWN)
        return copy(page = 1, filter = next, response = null)
    }

    fun withSort(next: OfferSort): MarketUiState =
        copy(page = 1, sort = next, response = null)

    fun previousPage(): MarketUiState =
        if (page > 1) copy(page = page - 1, response = null) else this

    fun nextPage(): MarketUiState {
        val max = response?.totalPages ?: return this
        return if (page < max) copy(page = page + 1, response = null) else this
    }
}
