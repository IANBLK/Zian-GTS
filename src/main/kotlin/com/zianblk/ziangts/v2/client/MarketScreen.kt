package com.zianblk.ziangts.v2.client

import com.zianblk.ziangts.v2.domain.MarketTab
import com.zianblk.ziangts.v2.network.V2MarketClient
import com.zianblk.ziangts.v2.network.V2MarketClientState
import com.zianblk.ziangts.v2.network.MarketAction
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * First native Minecraft screen for Zian GTS V2.
 *
 * This slice intentionally renders only server-projected text data. Pokemon
 * models/icons and mutating actions are added after the screen lifecycle and
 * paging path are verified on NeoForge/Youer.
 */
class MarketScreen : Screen(Component.literal("Zian GTS")) {
    private var state = MarketUiState()
    private var observedRevision = -1L
    private var previousButton: Button? = null
    private var nextButton: Button? = null
    private var observedActionRevision = -1L
    private var actionMessage: String? = null
    private var actionPending = false

    override fun init() {
        super.init()
        addRenderableWidget(
            Button.builder(Component.literal("Mercado")) {
                switchTab(MarketTab.MARKET)
            }.bounds(width / 2 - 105, 18, 100, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Mis anuncios")) {
                switchTab(MarketTab.MY_OFFERS)
            }.bounds(width / 2 + 5, 18, 100, 20).build()
        )
        previousButton = addRenderableWidget(
            Button.builder(Component.literal("<")) {
                val next = state.previousPage()
                if (next !== state) {
                    state = next
                    requestCurrent()
                }
            }.bounds(width / 2 - 65, height - 34, 30, 20).build()
        )
        nextButton = addRenderableWidget(
            Button.builder(Component.literal(">")) {
                val next = state.nextPage()
                if (next !== state) {
                    state = next
                    requestCurrent()
                }
            }.bounds(width / 2 + 35, height - 34, 30, 20).build()
        )
        updateNavigationButtons()
        requestCurrent()
    }

    private fun rebuildEntryButtons() {
        clearWidgets()
        init()
        val response = state.response ?: return
        var y = 57
        response.entries.forEach { entry ->
            val action = when {
                entry.canWithdraw -> MarketAction.WITHDRAW
                entry.canBuy -> MarketAction.BUY
                else -> null
            }
            if (action != null) {
                addRenderableWidget(
                    Button.builder(Component.literal(if (action == MarketAction.BUY) "Comprar" else "Retirar")) {
                        if (!actionPending) {
                            actionPending = true
                            actionMessage = "Procesando..."
                            V2MarketClient.requestAction(entry.offerId, action)
                        }
                    }.bounds(width / 2 + 105, y, 65, 16).build()
                ).active = !actionPending
            }
            y += 18
        }
    }

    override fun tick() {
        super.tick()
        val revision = V2MarketClientState.revision()
        if (revision != observedRevision) {
            observedRevision = revision
            val response = V2MarketClientState.snapshot()
            if (response != null && response.tab == state.tab) {
                state = state.accept(response)
                rebuildEntryButtons()
            }
        }
        val actionRev = V2MarketClientState.actionRevision()
        if (actionRev != observedActionRevision) {
            observedActionRevision = actionRev
            val result = V2MarketClientState.actionSnapshot()
            if (result != null) {
                actionPending = false
                actionMessage = result.message
                requestCurrent()
            }
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        super.render(graphics, mouseX, mouseY, partialTick)

        graphics.drawCenteredString(font, title, width / 2, 5, 0xFFFFFF)
        val response = state.response
        if (state.loading && response == null) {
            graphics.drawCenteredString(font, Component.literal("Cargando mercado..."), width / 2, 54, 0xAAAAAA)
            return
        }
        if (response == null) {
            graphics.drawCenteredString(font, Component.literal("Sin datos del mercado"), width / 2, 54, 0xAAAAAA)
            return
        }

        graphics.drawCenteredString(
            font,
            Component.literal("Página ${response.page}/${response.totalPages.coerceAtLeast(1)}"),
            width / 2,
            44,
            0xCCCCCC
        )

        var y = 62
        for (entry in response.entries) {
            val line = "${entry.species} Nv.${entry.level}  ${entry.price} ${entry.currency}  ${entry.sellerName}"
            graphics.drawString(font, Component.literal(line), width / 2 - 150, y, 0xFFFFFF)
            y += 18
        }
        actionMessage?.let { graphics.drawCenteredString(font, Component.literal(it), width / 2, height - 52, 0xCCCCCC) }
        if (response.entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No hay anuncios en esta página"), width / 2, 72, 0xAAAAAA)
        }
    }

    private fun switchTab(tab: MarketTab) {
        if (state.tab == tab) return
        state = state.switchTab(tab)
        updateNavigationButtons()
        requestCurrent()
    }

    private fun updateNavigationButtons() {
        previousButton?.active = !state.loading && state.page > 1
        nextButton?.active = !state.loading && (state.response?.hasNext == true)
    }

    private fun requestCurrent() {
        state = state.beginRequest()
        updateNavigationButtons()
        when (state.tab) {
            MarketTab.MARKET -> V2MarketClient.requestMarket(
                page = state.page,
                pageSize = state.pageSize,
                filter = state.filter,
                sort = state.sort
            )
            MarketTab.MY_OFFERS -> V2MarketClient.requestMyOffers(
                page = state.page,
                pageSize = state.pageSize,
                sort = state.sort
            )
        }
    }
}
