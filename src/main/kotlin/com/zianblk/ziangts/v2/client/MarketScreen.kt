package com.zianblk.ziangts.v2.client

import com.zianblk.ziangts.v2.domain.MarketTab
import com.zianblk.ziangts.v2.network.V2MarketClient
import com.zianblk.ziangts.v2.network.V2MarketClientState
import com.zianblk.ziangts.v2.network.MarketAction
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

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
    private val actionButtons = mutableListOf<Button>()

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
        val gap = 8
        val cardW = minOf(176, (width - 96 - gap * 2) / 3).coerceAtLeast(138)
        val cardH = 126
        val left = width / 2 - (cardW * 3 + gap * 2) / 2
        val top = 64
        response.entries.take(6).forEachIndexed { index, entry ->
            val x = left + (index % 3) * (cardW + gap)
            val y = top + (index / 3) * (cardH + gap)
            val action = when {
                entry.canWithdraw -> MarketAction.WITHDRAW
                entry.canBuy -> MarketAction.BUY
                else -> null
            }
            if (action != null) {
                val actionButton = addRenderableWidget(
                    Button.builder(Component.literal(if (action == MarketAction.BUY) "Comprar" else "Retirar")) {
                        if (!actionPending) {
                            actionPending = true
                            actionMessage = "Procesando..."
                            actionButtons.forEach { it.active = false }
                            V2MarketClient.requestAction(entry.offerId, action)
                        }
                    }.bounds(x + 8, y + cardH - 23, cardW - 16, 17).build()
                )
                actionButton.active = !actionPending
                actionButtons += actionButton
            }
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

        val gap = 8
        val cardW = minOf(176, (width - 96 - gap * 2) / 3).coerceAtLeast(138)
        val cardH = 126
        val left = width / 2 - (cardW * 3 + gap * 2) / 2
        val top = 64
        response.entries.take(6).forEachIndexed { index, entry ->
            val x = left + (index % 3) * (cardW + gap)
            val y = top + (index / 3) * (cardH + gap)
            graphics.fill(x, y, x + cardW, y + cardH, 0xB0202020.toInt())
            graphics.fill(x, y, x + cardW, y + 1, 0xFF777777.toInt())
            val species = entry.species.substringAfter(':').replaceFirstChar { it.uppercase() }
            graphics.drawString(font, Component.literal(species), x + 8, y + 8, 0xFFFFFF)
            graphics.drawString(font, Component.literal("Nv. " + entry.level), x + 8, y + 21, 0xBBBBBB)
            val traits = listOfNotNull(if (entry.shiny) "Shiny" else null, if (entry.alpha) "Alpha" else null).joinToString(" · ")
            if (traits.isNotEmpty()) graphics.drawString(font, Component.literal(traits), x + 8, y + 34, 0xFFD966)
            val priceLabel = "Precio: " + entry.price
            graphics.drawString(font, Component.literal(priceLabel), x + 8, y + 51, 0xFFFFFF)
            renderCurrencyIcon(graphics, entry.currency, x + 12 + font.width(priceLabel), y + 47)
            graphics.drawString(font, Component.literal("Vendedor: " + entry.sellerName), x + 8, y + 73, 0xCCCCCC)
            val seconds = ((entry.expiresAtEpochMilli - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            val time = if (seconds >= 3600) "Expira: " + (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m" else "Expira: " + (seconds / 60) + "m"
            graphics.drawString(font, Component.literal(time), x + 8, y + 88, 0x999999)
        }
        actionMessage?.let { graphics.drawCenteredString(font, Component.literal(it), width / 2, height - 52, 0xCCCCCC) }
        if (response.entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No hay anuncios en esta página"), width / 2, 86, 0xAAAAAA)
        }
    }


    private fun renderCurrencyIcon(graphics: GuiGraphics, currency: String, x: Int, y: Int) {
        val id = ResourceLocation.tryParse(currency) ?: return
        val item = BuiltInRegistries.ITEM.get(id)
        if (BuiltInRegistries.ITEM.getKey(item) != id) return
        graphics.renderItem(ItemStack(item), x, y)
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
