package com.zianblk.ziangts.v2.client

import com.zianblk.ziangts.v2.network.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class PublishOfferScreen(private val parent: Screen) : ResponsiveScreen(Component.translatableWithFallback("ui.ziangts.916325d8018b", "Publicar Pokémon")) {
    private val previewStates=mutableMapOf<java.util.UUID,com.cobblemon.mod.common.client.render.models.blockbench.FloatingState>()
    private var initialized=false
    private var options: PublishOptionsResponse? = null
    private var selectedPokemon = 0
    private var selectedCurrency = 0
    private var observedOptions = -1L
    private var observedResult = -1L
    private var message = "Cargando equipo..."
    private var pending = false
    private var confirmArmed = false
    private var priceBox: EditBox? = null
    private var pokemonButton: Button? = null
    private var currencyButton: Button? = null
    private var confirmButton: Button? = null

    override fun init() {
        fitViewport(600,390)
        val left=(width-440)/2;val top=(height-274)/2
        val previousPrice=priceBox?.value.orEmpty()
        priceBox = EditBox(font,left+254,top+131,164,20,Component.translatableWithFallback("ui.ziangts.18e0707ea726", "Precio")).also {
            it.setHint(Component.translatableWithFallback("ui.ziangts.b5b6f862d41c", "Cantidad"))
            it.setFilter { text -> text.isEmpty() || (text.all(Char::isDigit) && text.length <= 12) }
            it.value=previousPrice
            it.setResponder{confirmArmed=false;if(message.startsWith("Confirma:"))message="";refresh()}
            addRenderableWidget(it)
        }
        pokemonButton=addRenderableWidget(GtsButton.create(Component.translatableWithFallback("ui.ziangts.b6057fa91bec", "Elegir del equipo")){cyclePokemon()}.bounds(left+18,top+194,194,20).build())
        currencyButton=addRenderableWidget(GtsButton.create(Component.translatableWithFallback("ui.ziangts.3a724b648064", "Moneda")){cycleCurrency()}.bounds(left+254,top+84,164,20).build())
        confirmButton=addRenderableWidget(GtsButton.create(Component.translatableWithFallback("ui.ziangts.9df643f06945", "Publicar")){publish()}.bounds(left+234,top+239,96,20).build())
        addRenderableWidget(GtsButton.create(Component.translatableWithFallback("ui.ziangts.84a715a785a9", "Volver")){minecraft?.setScreen(parent)}.bounds(left+340,top+239,82,20).build())
        confirmArmed=false
        if(message.startsWith("Confirma:"))message=""
        // Baseline once: resizing must not discard an in-flight result.
        // Baseline the global client revisions before requesting fresh data.
        // Otherwise a successful result from a previous PublishOfferScreen instance
        // is replayed on the first tick and immediately sends the player back.
        if(!initialized){
            observedOptions = V2MarketClientState.publishOptionsRevision()
            observedResult = V2MarketClientState.publishResultRevision()
            initialized=true
        }
        refresh()
        if(!pending)V2MarketClient.requestPublishOptions()
    }

    override fun tick() {
        super.tick()
        val optionRev = V2MarketClientState.publishOptionsRevision()
        if (optionRev != observedOptions) {
            observedOptions = optionRev
            val oldPokemon=options?.party?.getOrNull(selectedPokemon)?.pokemonId
            val oldCurrency=options?.currencies?.getOrNull(selectedCurrency)
            options = V2MarketClientState.publishOptionsSnapshot()
            previewStates.keys.retainAll(options?.party.orEmpty().map { it.pokemonId }.toSet())
            selectedPokemon=options?.party.orEmpty().indexOfFirst{it.pokemonId==oldPokemon}.coerceAtLeast(0)
            selectedCurrency=options?.currencies.orEmpty().indexOf(oldCurrency).coerceAtLeast(0)
            confirmArmed=false
            message = if (options?.party.isNullOrEmpty()) "No tienes Pokémon disponibles en el equipo" else ""
            refresh()
        }
        val resultRev = V2MarketClientState.publishResultRevision()
        if (resultRev != observedResult) {
            observedResult = resultRev
            val result = V2MarketClientState.publishResultSnapshot()
            if (result != null) {
                pending = false
                message = result.message
                if (result.success) {
                    minecraft?.setScreen(parent)
                    return
                }
                refresh()
            }
        }
    }

    private fun cyclePokemon() {
        confirmArmed=false
        val list=options?.party.orEmpty()
        minecraft?.setScreen(MarketChoiceScreen(this,"Elegir Pokémon del equipo",list.map { it.pokemonId.toString() to (friendly(it.species)+" · Nv. "+it.level) }) { id ->
            selectedPokemon=list.indexOfFirst{it.pokemonId.toString()==id}.coerceAtLeast(0);refresh()
        })
    }
    private fun cycleCurrency() {
        confirmArmed=false
        val list=options?.currencies.orEmpty()
        minecraft?.setScreen(MarketChoiceScreen(this,"Elegir moneda",list.map{it to friendly(it)}){id->selectedCurrency=list.indexOf(id).coerceAtLeast(0);refresh()})
    }

    private fun publish() {
        val pokemon = options?.party?.getOrNull(selectedPokemon) ?: return
        val currency = options?.currencies?.getOrNull(selectedCurrency) ?: return
        val amount = priceBox?.value?.toLongOrNull()
        if (amount == null || amount <= 0) {
            confirmArmed = false
            message = "Introduce un precio válido"
            refresh()
            return
        }
        if (!confirmArmed) {
            confirmArmed = true
            message = "Confirma: ${friendly(pokemon.species)} por $amount ${friendly(currency)}"
            refresh()
            return
        }
        pending = true
        confirmArmed = false
        message = "Publicando..."
        refresh()
        V2MarketClient.publish(pokemon.pokemonId, amount, currency)
    }

    private fun refresh() {
        val pokemon = options?.party?.getOrNull(selectedPokemon)
        pokemonButton?.message=Component.literal(if(pokemon!=null)"Cambiar Pokémon" else "Sin Pokémon disponible")
        pokemonButton?.active = !pending && !options?.party.isNullOrEmpty()
        val currency = options?.currencies?.getOrNull(selectedCurrency)
        currencyButton?.message = Component.literal(currency?.let(::friendly) ?: "Sin moneda disponible")
        currencyButton?.active = !pending && !options?.currencies.isNullOrEmpty()
        confirmButton?.message = Component.literal(if (confirmArmed) "Confirmar" else "Publicar")
        confirmButton?.active = !pending && pokemon != null && currency != null
        priceBox?.setEditable(!pending)
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        ZianGtsTheme.background(graphics, width, height)
    }

    override fun renderCanvas(graphics:GuiGraphics,mouseX:Int,mouseY:Int,partialTick:Float){
        val left=(width-440)/2;val top=(height-274)/2
        ZianGtsTheme.background(graphics,width,height)
        graphics.fill(left,top,left+440,top+274,0xF0181D24.toInt())
        graphics.renderOutline(left,top,440,274,0xFF526575.toInt())
        graphics.fill(left,top,left+440,top+3,ZianGtsTheme.GOLD)
        graphics.drawCenteredString(font,title,width/2,top+12,ZianGtsTheme.GOLD)
        graphics.drawCenteredString(font,"Selecciona y revisa antes de publicar",width/2,top+29,0xFF9FAAB5.toInt())
        graphics.fill(left+18,top+51,left+212,top+187,0xFF10161D.toInt())
        graphics.renderOutline(left+18,top+51,194,136,0xFF343D47.toInt())
        val selected=options?.party?.getOrNull(selectedPokemon)
        if(selected==null){
            graphics.drawCenteredString(font,"Equipo vacío",left+115,top+103,0xFF9FAAB5.toInt())
            graphics.drawCenteredString(font,"Necesitas un Pokémon",left+115,top+120,0xFF9FAAB5.toInt())
        }else{
            graphics.drawCenteredString(font,friendly(selected.species),left+115,top+59,ZianGtsTheme.GOLD)
            graphics.drawCenteredString(font,"Nivel ${selected.level} · Slot ${selected.slot}",left+115,top+76,0xFFB8C0C8.toInt())
            graphics.drawCenteredString(font,listOfNotNull(if(selected.shiny)"✦ Shiny" else null,if(selected.legendary)"Legendario" else null,if(selected.alpha)"Alfa" else null).joinToString(" · "),left+115,top+159,ZianGtsTheme.GOLD)
            val pose=previewStates.getOrPut(selected.pokemonId){com.cobblemon.mod.common.client.render.models.blockbench.FloatingState()}
            pose.currentAspects=buildSet{if(selected.shiny)add("shiny");if(selected.alpha)add("alpha")}
            canvasScissor(graphics,left+20,top+90,left+210,top+153)
            graphics.pose().pushPose()
            try{
                graphics.pose().translate((left+115).toDouble(),(top+111).toDouble(),90.0)
                com.cobblemon.mod.common.client.gui.drawProfilePokemon(net.minecraft.resources.ResourceLocation.parse(selected.species),graphics.pose(),org.joml.Quaternionf().rotationXYZ(0.08f,0.45f,0f),state=pose,partialTicks=partialTick,scale=28f)
            }finally{graphics.pose().popPose();graphics.disableScissor()}
        }
        graphics.drawString(font,"Moneda",left+254,top+67,ZianGtsTheme.GOLD)
        graphics.drawString(font,"Precio de venta",left+254,top+115,ZianGtsTheme.GOLD)
        graphics.drawString(font,"Revisa el importe y confirma",left+234,top+173,0xFF9FAAB5.toInt())
        val text=message.ifBlank{"El Pokémon saldrá de tu equipo"}
        font.split(Component.literal(text),198).take(2).forEachIndexed{i,line->graphics.drawString(font,line,left+18,top+221+i*font.lineHeight,0xFFB8C0C8.toInt())}
        if(mouseX in left+18 until left+216 && mouseY in top+221 until top+239)queueTooltip(listOf(Component.literal(text)))
        renderWidgets(graphics,mouseX,mouseY,partialTick)
    }

    private fun friendly(value: String): String = value.substringAfter(':')
        .replace('_', ' ').replace('-', ' ').split(' ').filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
