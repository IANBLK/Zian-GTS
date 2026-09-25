package com.zianblk.ziangts.v2.client

import com.zianblk.ziangts.v2.domain.MarketTab
import com.zianblk.ziangts.v2.domain.OfferFilter
import com.zianblk.ziangts.v2.domain.OfferSort
import com.zianblk.ziangts.v2.network.V2MarketClient
import com.zianblk.ziangts.v2.network.V2MarketClientState
import com.zianblk.ziangts.v2.network.MarketAction
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import org.joml.Quaternionf
import java.util.UUID
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
    private var filterButton: Button? = null
    private var sortButton: Button? = null
    private val previewStates = mutableMapOf<UUID, FloatingState>()

    override fun init() {
        super.init()
        observedActionRevision = V2MarketClientState.actionRevision()
        actionMessage = null
        actionPending = false

        val compact = width < 600
        val row1Y = 18
        val row2Y = if (compact) 41 else 41
        val primaryW = if (compact) 86 else 100
        val smallW = if (compact) 68 else 75
        val primaryGap = 5
        val row1Total = primaryW * 2 + smallW + primaryGap * 2
        val row1X = (width - row1Total) / 2

        addRenderableWidget(
            Button.builder(Component.literal("Mercado")) { switchTab(MarketTab.MARKET) }
                .bounds(row1X, row1Y, primaryW, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Mis anuncios")) { switchTab(MarketTab.MY_OFFERS) }
                .bounds(row1X + primaryW + primaryGap, row1Y, primaryW, 20).build()
        )
        addRenderableWidget(
            Button.builder(Component.literal("Publicar")) { minecraft?.setScreen(PublishOfferScreen(this)) }
                .bounds(row1X + primaryW * 2 + primaryGap * 2, row1Y, smallW, 20).build()
        )

        val secondWidths = if (compact) listOf(78, 78, 68, 68) else listOf(100, 100, 75, 75)
        val secondGap = 5
        val secondTotal = secondWidths.sum() + secondGap * 3
        var secondX = (width - secondTotal) / 2
        filterButton = addRenderableWidget(
            Button.builder(Component.literal(filterLabel())) {
                if (state.tab == MarketTab.MARKET) {
                    state = state.copy(filter = nextFilter(state.filter), page = 1)
                    refreshControls()
                    requestCurrent()
                }
            }.bounds(secondX, row2Y, secondWidths[0], 18).build()
        )
        secondX += secondWidths[0] + secondGap
        sortButton = addRenderableWidget(
            Button.builder(Component.literal(sortLabel())) {
                state = state.copy(sort = nextSort(state.sort), page = 1)
                refreshControls()
                requestCurrent()
            }.bounds(secondX, row2Y, secondWidths[1], 18).build()
        )
        secondX += secondWidths[1] + secondGap
        addRenderableWidget(
            Button.builder(Component.literal("Ganancias")) { minecraft?.setScreen(ProceedsScreen(this)) }
                .bounds(secondX, row2Y, secondWidths[2], 18).build()
        )
        secondX += secondWidths[2] + secondGap
        addRenderableWidget(
            Button.builder(Component.literal("Historial")) { minecraft?.setScreen(HistoryScreen(this)) }
                .bounds(secondX, row2Y, secondWidths[3], 18).build()
        )

        refreshControls()
        val layout = MarketLayout.calculate(width, height)
        previousButton = addRenderableWidget(
            Button.builder(Component.literal("<")) {
                val next = state.previousPage()
                if (next !== state) {
                    state = next
                    requestCurrent()
                }
            }.bounds(width / 2 - 65, layout.footerY, 30, 20).build()
        )
        nextButton = addRenderableWidget(
            Button.builder(Component.literal(">")) {
                val next = state.nextPage()
                if (next !== state) {
                    state = next
                    requestCurrent()
                }
            }.bounds(width / 2 + 35, layout.footerY, 30, 20).build()
        )
        updateNavigationButtons()
        requestCurrent()
    }

    private fun rebuildEntryButtons() {
        actionButtons.forEach { removeWidget(it) }
        actionButtons.clear()
        val response = state.response ?: return
        val layout = MarketLayout.calculate(width, height)
        val cardW = layout.cardWidth
        val cardH = layout.cardHeight
        response.entries.take(6).forEachIndexed { index, entry ->
            val x = layout.cardX(index)
            val y = layout.cardY(index)
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
                refreshControls()
                updateNavigationButtons()
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
        // Custom dark marketplace surface. Avoid menu blur so Pokémon previews and text stay crisp.
        graphics.fill(0, 0, width, height, 0xE00D1117.toInt())
        graphics.fill(0, 0, width, 4, 0xFFF0C75E.toInt())
        super.render(graphics, mouseX, mouseY, partialTick)

        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFF7E4A6.toInt())
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
            61,
            0xCCCCCC
        )

        val layout = MarketLayout.calculate(width, height)
        val cardW = layout.cardWidth
        val cardH = layout.cardHeight
        response.entries.take(6).forEachIndexed { index, entry ->
            val x = layout.cardX(index)
            val y = layout.cardY(index)
            // Layered card: border, body, preview well and price footer.
            graphics.fill(x, y, x + cardW, y + cardH, 0xFF59616B.toInt())
            graphics.fill(x + 1, y + 1, x + cardW - 1, y + cardH - 1, 0xF0181D24.toInt())
            graphics.fill(x + 1, y + 1, x + cardW - 1, y + 4, rarityAccent(entry))
            graphics.fill(x + cardW / 2, y + 6, x + cardW - 7, y + 67, 0x8010161D.toInt())
            graphics.renderOutline(x + cardW / 2, y + 6, cardW / 2 - 7, 61, 0xFF343D47.toInt())

            renderPokemonPreview(graphics, entry, x + (cardW * 3) / 4, y + 39, partialTick)
            val species = entry.species.substringAfter(':').replaceFirstChar { it.uppercase() }
            graphics.drawString(font, Component.literal(species), x + 8, y + 10, 0xFFF4F7FA.toInt())
            graphics.drawString(font, Component.literal("Nv. " + entry.level), x + 8, y + 23, 0xFF9FAAB5.toInt())

            var badgeY = y + 37
            listOfNotNull(
                if (entry.legendary) "LEGENDARIO" else null,
                if (entry.shiny) "SHINY" else null,
                if (entry.alpha) "ALPHA" else null
            ).take(3).forEach { badge ->
                drawBadge(graphics, badge, x + 8, badgeY, rarityAccent(entry))
                badgeY += 12
            }

            val footerTop = y + cardH - 52
            graphics.fill(x + 1, footerTop, x + cardW - 1, y + cardH - 1, 0xC010141A.toInt())
            val priceLabel = entry.price.toString()
            graphics.drawString(font, Component.literal(priceLabel), x + 8, footerTop + 7, 0xFFF7E4A6.toInt())
            renderCurrencyIcon(graphics, entry.currency, x + 12 + font.width(priceLabel), footerTop + 3, mouseX, mouseY)
            val seller = fitText("Vendedor: " + entry.sellerName, cardW - 16)
            graphics.drawString(font, Component.literal(seller), x + 8, footerTop + 24, 0xFFB7C0C9.toInt())
            val seconds = ((entry.expiresAtEpochMilli - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            val time = if (seconds >= 3600) "Expira " + (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m" else "Expira " + (seconds / 60) + "m"
            graphics.drawString(font, Component.literal(time), x + cardW - 8 - font.width(time), footerTop + 24, 0xFF7F8A96.toInt())
        }
        actionMessage?.let { graphics.drawCenteredString(font, Component.literal(it), width / 2, MarketLayout.calculate(width, height).footerY - 16, 0xCCCCCC) }
        if (response.entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No hay anuncios en esta página"), width / 2, 86, 0xAAAAAA)
        }
    }



    private fun rarityAccent(entry: com.zianblk.ziangts.v2.network.MarketEntryDto): Int = when {
        entry.legendary && entry.shiny -> 0xFFE6A8FF.toInt()
        entry.legendary -> 0xFFF0C75E.toInt()
        entry.shiny -> 0xFF75D7FF.toInt()
        entry.alpha -> 0xFFFF8B8B.toInt()
        else -> 0xFF6F7C88.toInt()
    }

    private fun drawBadge(graphics: GuiGraphics, label: String, x: Int, y: Int, accent: Int) {
        val badgeW = (font.width(label) + 8).coerceAtMost(72)
        graphics.fill(x, y, x + badgeW, y + 10, 0xD0202730.toInt())
        graphics.fill(x, y, x + 2, y + 10, accent)
        graphics.drawString(font, Component.literal(label), x + 5, y + 1, accent, false)
    }

    private fun fitText(value: String, maxWidth: Int): String {
        if (font.width(value) <= maxWidth) return value
        var result = value
        while (result.isNotEmpty() && font.width(result + "…") > maxWidth) result = result.dropLast(1)
        return result + "…"
    }

    private fun renderPokemonPreview(
        graphics: GuiGraphics,
        entry: com.zianblk.ziangts.v2.network.MarketEntryDto,
        centerX: Int,
        centerY: Int,
        partialTick: Float
    ) {
        val pose = previewStates.getOrPut(entry.offerId) { FloatingState() }
        pose.currentAspects = buildSet {
            if (entry.shiny) add("shiny")
            if (entry.alpha) add("alpha")
        }
        graphics.pose().pushPose()
        try {
            graphics.pose().translate(centerX.toDouble(), centerY.toDouble(), 90.0)
            drawProfilePokemon(
                ResourceLocation.parse(entry.species),
                graphics.pose(),
                Quaternionf().rotationXYZ(0.08f, 0.45f, 0f),
                state = pose,
                partialTicks = partialTick,
                scale = 22f
            )
        } finally {
            graphics.pose().popPose()
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true
        if (button != 0) return false
        val response = state.response ?: return false
        val layout = MarketLayout.calculate(width, height)
        val cardW = layout.cardWidth
        val cardH = layout.cardHeight
        response.entries.take(6).forEachIndexed { index, entry ->
            val x = layout.cardX(index)
            val y = layout.cardY(index)
            if (mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH - 25) {
                minecraft?.setScreen(PokemonDetailsScreen(entry, this))
                return true
            }
        }
        return false
    }

    private fun renderCurrencyIcon(graphics: GuiGraphics, currency: String, x: Int, y: Int, mouseX: Int, mouseY: Int) {
        val id = ResourceLocation.tryParse(currency) ?: return
        val item = BuiltInRegistries.ITEM.get(id)
        if (BuiltInRegistries.ITEM.getKey(item) != id) return
        graphics.renderItem(ItemStack(item), x, y)
        if (mouseX in x until (x + 16) && mouseY in y until (y + 16)) {
            graphics.renderTooltip(font, Component.literal(currencyDisplayName(id.path)), mouseX, mouseY)
        }
    }

    private fun currencyDisplayName(path: String): String {
        val known = mapOf(
            "coppercoin" to "Moneda de Cobre",
            "ironcoin" to "Moneda de Hierro",
            "goldcoin" to "Moneda de Oro",
            "diamondcoin" to "Moneda de Diamante",
            "netheritecoin" to "Moneda de Netherita",
            "goldticket" to "Ticket de Oro",
            "diamondticket" to "Ticket de Diamante",
            "netheriteticket" to "Ticket de Netherita"
        )
        return known[path] ?: path
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }


    private fun refreshControls() {
        filterButton?.message = Component.literal(filterLabel())
        filterButton?.active = state.tab == MarketTab.MARKET
        sortButton?.message = Component.literal(sortLabel())
        sortButton?.active = true
    }

    private fun nextFilter(current: OfferFilter): OfferFilter {
        val values = listOf(OfferFilter.ALL, OfferFilter.SHINY, OfferFilter.ALPHA, OfferFilter.LEGENDARY, OfferFilter.LEGENDARY_SHINY)
        val index = values.indexOf(current).coerceAtLeast(0)
        return values[(index + 1) % values.size]
    }

    private fun nextSort(current: OfferSort): OfferSort {
        val values = listOf(OfferSort.NEWEST, OfferSort.OLDEST, OfferSort.PRICE_LOW, OfferSort.PRICE_HIGH, OfferSort.LEVEL_LOW, OfferSort.LEVEL_HIGH)
        val index = values.indexOf(current).coerceAtLeast(0)
        return values[(index + 1) % values.size]
    }

    private fun filterLabel(): String = "Filtro: " + when (state.filter) {
        OfferFilter.ALL -> "Todos"
        OfferFilter.SHINY -> "Shiny"
        OfferFilter.ALPHA -> "Alpha"
        OfferFilter.LEGENDARY -> "Legendarios"
        OfferFilter.LEGENDARY_SHINY -> "Legendario Shiny"
        OfferFilter.OWN -> "Míos"
    }

    private fun sortLabel(): String = "Orden: " + when (state.sort) {
        OfferSort.NEWEST -> "Recientes"
        OfferSort.OLDEST -> "Antiguos"
        OfferSort.PRICE_LOW -> "Precio ↑"
        OfferSort.PRICE_HIGH -> "Precio ↓"
        OfferSort.LEVEL_LOW -> "Nivel ↑"
        OfferSort.LEVEL_HIGH -> "Nivel ↓"
    }

    private fun switchTab(tab: MarketTab) {
        if (state.tab == tab) return
        state = state.switchTab(tab)
        refreshControls()
        updateNavigationButtons()
        requestCurrent()
    }

    private fun updateNavigationButtons() {
        previousButton?.active = !state.loading && state.page > 1
        nextButton?.active = !state.loading && (state.response?.hasNext == true)
    }

    private fun requestCurrent() {
        state = state.beginRequest()
        refreshControls()
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
