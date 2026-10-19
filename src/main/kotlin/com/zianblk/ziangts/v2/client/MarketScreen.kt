package com.zianblk.ziangts.v2.client

import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.zianblk.ziangts.v2.domain.MarketTab
import com.zianblk.ziangts.v2.domain.OfferFilter
import com.zianblk.ziangts.v2.domain.OfferSort
import com.zianblk.ziangts.v2.network.MarketAction
import com.zianblk.ziangts.v2.network.V2MarketClient
import com.zianblk.ziangts.v2.network.V2MarketClientState
import java.util.UUID
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf

class MarketScreen : ResponsiveScreen(Component.translatableWithFallback("ui.ziangts.47e7e9085cc1", "Zian GTS")) {
    private var searchQuery=""
    private var initialized=false
    private var state = MarketUiState()
    private var observedRevision = -1L
    private var previousButton: Button? = null
    private var nextButton: Button? = null
    private var observedActionRevision = -1L
    private var actionMessage: String? = null
    private var actionPending = false
    private var actionStarted=0L
    private val actionButtons = mutableListOf<Button>()
    private var filterButton: Button? = null
    private var sortButton: Button? = null
    private val previewStates = mutableMapOf<UUID, FloatingState>()

    override fun init() {
        fitViewport(640,440)
        super.init()
        if(!initialized){
            observedActionRevision=V2MarketClientState.actionRevision()
            initialized=true
        }
        actionButtons.clear()

        addRenderableWidget(GtsButton.create(Component.translatableWithFallback("ui.ziangts.4b0816bbd5e9", "Cerrar")){onClose()}.bounds(width-76,18,64,20).build())
        val compact = width < 600
        val row1Y = 18
        val row2Y = 41
        val primaryW = if (compact) 86 else 100
        val smallW = if (compact) 68 else 75
        val primaryGap = 5
        val row1Total = primaryW * 2 + smallW + primaryGap * 2
        val row1X = (width - row1Total) / 2

        addRenderableWidget(
            GtsButton.create(Component.translatableWithFallback("ui.ziangts.9e1c498be862", "Mercado")) { switchTab(MarketTab.MARKET) }
                .bounds(row1X, row1Y, primaryW, 20)
                .build()
        )
        addRenderableWidget(
            GtsButton.create(Component.translatableWithFallback("ui.ziangts.967115867746", "Mis anuncios")) { switchTab(MarketTab.MY_OFFERS) }
                .bounds(row1X + primaryW + primaryGap, row1Y, primaryW, 20)
                .build()
        )
        addRenderableWidget(
            GtsButton.create(Component.translatableWithFallback("ui.ziangts.9df643f06945", "Publicar")) { minecraft?.setScreen(PublishOfferScreen(this)) }
                .bounds(row1X + primaryW * 2 + primaryGap * 2, row1Y, smallW, 20)
                .build()
        )

        val secondWidths = if (compact) listOf(78, 78, 68, 68) else listOf(100, 100, 75, 75)
        val secondGap = 5
        val secondTotal = secondWidths.sum() + secondGap * 3
        var secondX = (width - secondTotal) / 2

        filterButton = addRenderableWidget(
            GtsButton.create(Component.literal(filterLabel())) {
                if (state.tab == MarketTab.MARKET) {
                    minecraft?.setScreen(MarketChoiceScreen(this,"Filtrar ofertas",OfferFilter.entries.filter{it!=OfferFilter.OWN}.map{it.name to filterName(it)}) { id ->
                        state=state.withFilter(OfferFilter.valueOf(id));refreshControls();requestCurrent()
                    })
                }
            }.bounds(secondX, row2Y, secondWidths[0], 18).build()
        )
        secondX += secondWidths[0] + secondGap

        sortButton = addRenderableWidget(
            GtsButton.create(Component.literal(sortLabel())) {
                minecraft?.setScreen(MarketChoiceScreen(this,"Ordenar ofertas",OfferSort.entries.map{it.name to sortName(it)}) { id ->
                    state=state.withSort(OfferSort.valueOf(id));refreshControls();requestCurrent()
                })
            }.bounds(secondX, row2Y, secondWidths[1], 18).build()
        )
        secondX += secondWidths[1] + secondGap

        addRenderableWidget(
            GtsButton.create(Component.translatableWithFallback("ui.ziangts.f5b08aebcf92", "Ganancias")) { minecraft?.setScreen(ProceedsScreen(this)) }
                .bounds(secondX, row2Y, secondWidths[2], 18)
                .build()
        )
        secondX += secondWidths[2] + secondGap
        addRenderableWidget(
            GtsButton.create(Component.translatableWithFallback("ui.ziangts.302027905ce5", "Historial")) { minecraft?.setScreen(HistoryScreen(this)) }
                .bounds(secondX, row2Y, secondWidths[3], 18)
                .build()
        )

        val search=addRenderableWidget(net.minecraft.client.gui.components.EditBox(font,width/2-110,64,180,16,Component.translatableWithFallback("ui.ziangts.89df234cef3a", "Buscar especie o vendedor")))
        search.setMaxLength(64);search.value=searchQuery;search.setHint(Component.translatableWithFallback("ui.ziangts.1d58c9f3fe44", "Especie o vendedor…"));search.setResponder{searchQuery=it}
        addRenderableWidget(GtsButton.create(Component.translatableWithFallback("ui.ziangts.a62405e2cefb", "Buscar")){state=state.copy(page=1);requestCurrent()}.bounds(width/2+76,63,60,18).build())
        refreshControls()
        val layout = MarketLayout.calculate(width, height)
        previousButton = addRenderableWidget(
            GtsButton.create(Component.literal("<")) {
                val next = state.previousPage()
                if (next !== state) {
                    state = next
                    requestCurrent()
                }
            }.bounds(width / 2 - 65, layout.footerY, 30, 20).build()
        )
        nextButton = addRenderableWidget(
            GtsButton.create(Component.literal(">")) {
                val next = state.nextPage()
                if (next !== state) {
                    state = next
                    requestCurrent()
                }
            }.bounds(width / 2 + 35, layout.footerY, 30, 20).build()
        )
        updateNavigationButtons()
        rebuildEntryButtons()
        if(!state.loading)requestCurrent()
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
                val buttonWidth = 62
                val button = addRenderableWidget(
                    GtsButton.create(Component.literal(if (action == MarketAction.BUY) "Comprar" else "Retirar")) {
                        if (!actionPending) {
                            minecraft?.setScreen(MarketConfirmScreen(this,
                                if(action==MarketAction.BUY) "Confirmar compra" else "Retirar anuncio",
                                listOf(entry.species.substringAfter(':').replace('_',' ')+" · Nv. "+entry.level,
                                    "Vendedor: "+entry.sellerName,
                                    "Precio: "+entry.price+" "+currencyDisplayName(entry.currency.substringAfter(':')),
                                    if(action==MarketAction.BUY)"Revisa el precio y el Pokémon antes de pagar." else "El Pokémon se devolverá a tu almacenamiento cuando se confirme la retirada.")) {
                                if(!actionPending){actionPending=true;actionStarted=System.nanoTime();actionMessage="Esperando al servidor…";actionButtons.forEach{it.active=false};V2MarketClient.requestAction(entry.offerId,action)}
                            })
                        }
                    }.bounds(x + cardW - buttonWidth - 7, y + cardH - 17, buttonWidth, 15).build()
                )
                button.active = !actionPending
                actionButtons += button
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
                previewStates.keys.retainAll(response.entries.map { it.offerId }.toSet())
                state = state.accept(response)
                refreshControls()
                updateNavigationButtons()
                rebuildEntryButtons()
            }
        }

        if(actionPending&&System.nanoTime()-actionStarted>8_000_000_000L)actionMessage="Respuesta tardía. Consulta el mercado; no repitas la operación."
        val actionRevision = V2MarketClientState.actionRevision()
        if (actionRevision != observedActionRevision) {
            observedActionRevision = actionRevision
            val result = V2MarketClientState.actionSnapshot()
            if (result != null) {
                actionPending = false
                actionMessage = result.message
                requestCurrent()
            }
        }
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Intentionally empty. The market owns its complete background so NeoForge's native
        // menu blur cannot be inserted between the custom cards and the widgets.
    }

    override fun renderCanvas(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        ZianGtsTheme.background(graphics, width, height)
        graphics.drawCenteredString(font, title, width / 2, 6, 0xFFF7E4A6.toInt())

        val response = state.response
        if (state.loading && response == null) {
            graphics.drawCenteredString(font, Component.translatableWithFallback("ui.ziangts.9f2c6ad20d44", "Cargando mercado..."), width / 2, 54, 0xAAAAAA)
            renderWidgets(graphics, mouseX, mouseY, partialTick)
            return
        }
        if (response == null) {
            graphics.drawCenteredString(font, Component.translatableWithFallback("ui.ziangts.1731c978f4d3", "Sin datos del mercado"), width / 2, 54, 0xAAAAAA)
            renderWidgets(graphics, mouseX, mouseY, partialTick)
            return
        }

        graphics.drawCenteredString(
            font,
            UiText.pageCounter(response.page, response.totalPages),
            width / 2,
            height-43,
            0xCCCCCC
        )

        val layout = MarketLayout.calculate(width, height)
        val cardW = layout.cardWidth
        val cardH = layout.cardHeight

        response.entries.take(6).forEachIndexed { index, entry ->
            val x = layout.cardX(index)
            val y = layout.cardY(index)
            graphics.fill(x, y, x + cardW, y + cardH, 0xFF59616B.toInt())
            graphics.fill(x + 1, y + 1, x + cardW - 1, y + cardH - 1, 0xF0181D24.toInt())
            graphics.fill(x + 1, y + 1, x + cardW - 1, y + 4, rarityAccent(entry))

            val species = entry.species.substringAfter(':').replace('_',' ').replaceFirstChar { it.uppercase() }
            graphics.drawString(font, fitText(species,cardW-16), x+8,y+10,ZianGtsTheme.GOLD)
            val gender=when(entry.gender.uppercase()){ "MALE"->"♂ Macho";"FEMALE"->"♀ Hembra";else->"Sin sexo" }
            graphics.drawString(font,fitText("$gender · Nv. ${entry.level}",cardW-88),x+80,y+25,0xFFB8C0C8.toInt())
            val labels=listOf("PS","Atq","Def","AtE","DfE","Vel")
            labels.forEachIndexed { i,label ->
                graphics.drawString(font,"$label ${entry.ivs.getOrElse(i){0}}",x+80+(i%2)*49,y+41+(i/2)*11,0xFFD2DCE6.toInt())
            }
            graphics.fill(x+8,y+25,x+72,y+76,0x8010161D.toInt())
            graphics.renderOutline(x+8,y+25,64,51,0xFF343D47.toInt())
            canvasScissor(graphics,x+9,y+26,x+71,y+75)
            try{renderPokemonPreview(graphics,entry,x+40,y+31,partialTick)}finally{graphics.disableScissor()}
            val traits=listOfNotNull(if(entry.shiny)"✦ Shiny" else null,if(entry.legendary)"Legendario" else null,if(entry.alpha)"Alfa" else null).joinToString(" · ").ifEmpty{"IVs del Pokémon"}
            graphics.drawString(font,fitText(traits,cardW-16),x+8,y+80,rarityAccent(entry))
            val footerTop=y+94
            graphics.fill(x+1,footerTop,x+cardW-1,y+cardH-1,0xC010141A.toInt())
            val priceLabel=fitText("Precio: ${entry.price}",cardW-40)
            graphics.drawString(font,priceLabel,x+8,footerTop+5,0xFFF7E4A6.toInt())
            renderCurrencyIcon(graphics,entry.currency,x+12+font.width(priceLabel),footerTop+1,mouseX,mouseY)
            if(mouseX in x until x+cardW && mouseY in y until footerTop){
                queueTooltip(listOf(Component.literal(species),UiText.genderLevel(gender, entry.level),Component.literal("IVs: "+entry.ivs.joinToString(" / ")),Component.literal("Naturaleza: "+entry.nature.substringAfter(':')),Component.literal("Habilidad: "+entry.ability.substringAfter(':')),Component.translatableWithFallback("ui.ziangts.2088184a2408", "Haz clic para ver los detalles")))
            }

            val seller = fitText("Vendedor: ${entry.sellerName}", cardW - 16)
            graphics.drawString(font, Component.literal(seller), x + 8, footerTop + 20, 0xFFB7C0C9.toInt())

            val seconds = ((entry.expiresAtEpochMilli - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            val time = if (seconds >= 3600) {
                "Expira ${seconds / 3600}h ${(seconds % 3600) / 60}m"
            } else {
                "Expira ${seconds / 60}m"
            }
            graphics.drawString(font, Component.literal(time), x + 8, footerTop + 34, 0xFF7F8A96.toInt())
        }

        renderWidgets(graphics, mouseX, mouseY, partialTick)

        actionMessage?.let {
            graphics.drawCenteredString(
                font,
                font.plainSubstrByWidth(it,width-24),
                width / 2,
                layout.footerY - font.lineHeight,
                0xCCCCCC
            )
        }

        if (response.entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal(if(state.tab==MarketTab.MY_OFFERS)"Todavía no tienes Pokémon publicados" else "No hay anuncios de otros jugadores con este filtro"), width / 2, 86, 0xAAAAAA)
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
        val badgeWidth = (font.width(label) + 8).coerceAtMost(72)
        graphics.fill(x, y, x + badgeWidth, y + 10, 0xD0202730.toInt())
        graphics.fill(x, y, x + 2, y + 10, accent)
        graphics.drawString(font, Component.literal(label), x + 5, y + 1, accent, false)
    }

    private fun fitText(value: String, maxWidth: Int): String {
        if (font.width(value) <= maxWidth) return value
        var result = value
        while (result.isNotEmpty() && font.width(result + "…") > maxWidth) {
            result = result.dropLast(1)
        }
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
                scale = 22.0f
            )
        } finally {
            graphics.pose().popPose()
        }
    }

    override fun canvasMouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.canvasMouseClicked(mouseX, mouseY, button)) return true
        if (button != 0) return false
        val response = state.response ?: return false
        val layout = MarketLayout.calculate(width, height)
        val cardW = layout.cardWidth
        val cardH = layout.cardHeight

        response.entries.take(6).forEachIndexed { index, entry ->
            val x = layout.cardX(index)
            val y = layout.cardY(index)
            if (mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH - 22) {
                minecraft?.setScreen(PokemonDetailsScreen(entry, this))
                return true
            }
        }
        return false
    }

    private fun renderCurrencyIcon(
        graphics: GuiGraphics,
        currency: String,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val id = ResourceLocation.tryParse(currency) ?: return
        val item = BuiltInRegistries.ITEM.get(id)
        if (BuiltInRegistries.ITEM.getKey(item) != id) return
        graphics.renderItem(ItemStack(item), x, y)
        if (mouseX in x until (x + 16) && mouseY in y until (y + 16)) {
            queueTooltip(listOf(Component.literal(currencyDisplayName(id.path))))
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
        val values = listOf(
            OfferFilter.ALL,
            OfferFilter.SHINY,
            OfferFilter.ALPHA,
            OfferFilter.LEGENDARY,
            OfferFilter.LEGENDARY_SHINY
        )
        val index = values.indexOf(current).coerceAtLeast(0)
        return values[(index + 1) % values.size]
    }

    private fun nextSort(current: OfferSort): OfferSort {
        val values = listOf(
            OfferSort.NEWEST,
            OfferSort.OLDEST,
            OfferSort.PRICE_LOW,
            OfferSort.PRICE_HIGH,
            OfferSort.LEVEL_LOW,
            OfferSort.LEVEL_HIGH
        )
        val index = values.indexOf(current).coerceAtLeast(0)
        return values[(index + 1) % values.size]
    }

    private fun filterName(value:OfferFilter):String {val before=state;state=state.copy(filter=value);val label=filterLabel();state=before;return label}
    private fun sortName(value:OfferSort):String {val before=state;state=state.copy(sort=value);val label=sortLabel();state=before;return label}
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

    override fun onClose(){if(!GuiReturn.returnToMenu())super.onClose()}
    private fun requestCurrent() {
        state = state.beginRequest()
        refreshControls()
        updateNavigationButtons()
        when (state.tab) {
            MarketTab.MARKET -> V2MarketClient.requestMarket(
                page = state.page,
                pageSize = state.pageSize,
                filter = state.filter,
                sort = state.sort, search=searchQuery
            )
            MarketTab.MY_OFFERS -> V2MarketClient.requestMyOffers(
                page = state.page,
                pageSize = state.pageSize,
                sort = state.sort, search=searchQuery
            )
        }
    }
}
