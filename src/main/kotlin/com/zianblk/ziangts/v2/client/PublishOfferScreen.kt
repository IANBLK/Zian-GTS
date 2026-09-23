package com.zianblk.ziangts.v2.client

import com.zianblk.ziangts.v2.network.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class PublishOfferScreen(private val parent: Screen) : Screen(Component.literal("Publicar Pokémon")) {
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
        priceBox = EditBox(font, width / 2 - 75, height / 2 + 18, 150, 20, Component.literal("Precio")).also {
            it.setHint(Component.literal("Precio"))
            it.setFilter { text -> text.isEmpty() || (text.all(Char::isDigit) && text.length <= 12) }
            addRenderableWidget(it)
        }
        pokemonButton = addRenderableWidget(Button.builder(Component.literal("Pokémon")) { cyclePokemon() }
            .bounds(width / 2 - 100, height / 2 - 55, 200, 20).build())
        currencyButton = addRenderableWidget(Button.builder(Component.literal("Moneda")) { cycleCurrency() }
            .bounds(width / 2 - 100, height / 2 - 12, 200, 20).build())
        confirmButton = addRenderableWidget(Button.builder(Component.literal("Publicar")) { publish() }
            .bounds(width / 2 - 100, height / 2 + 48, 95, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Volver")) { minecraft?.setScreen(parent) }
            .bounds(width / 2 + 5, height / 2 + 48, 95, 20).build())
        refresh()
        V2MarketClient.requestPublishOptions()
    }

    override fun tick() {
        super.tick()
        val optionRev = V2MarketClientState.publishOptionsRevision()
        if (optionRev != observedOptions) {
            observedOptions = optionRev
            options = V2MarketClientState.publishOptionsSnapshot()
            selectedPokemon = 0
            selectedCurrency = 0
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
                if (result.success) V2MarketClient.requestPublishOptions()
                refresh()
            }
        }
    }

    private fun cyclePokemon() {
        confirmArmed = false
        val list = options?.party.orEmpty()
        if (list.isNotEmpty()) selectedPokemon = (selectedPokemon + 1) % list.size
        refresh()
    }

    private fun cycleCurrency() {
        confirmArmed = false
        val list = options?.currencies.orEmpty()
        if (list.isNotEmpty()) selectedCurrency = (selectedCurrency + 1) % list.size
        refresh()
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
        pokemonButton?.message = Component.literal(
            pokemon?.let { "Slot ${it.slot}: ${friendly(it.species)} · Nv. ${it.level}" } ?: "Sin Pokémon disponible"
        )
        pokemonButton?.active = !pending && !options?.party.isNullOrEmpty()
        val currency = options?.currencies?.getOrNull(selectedCurrency)
        currencyButton?.message = Component.literal(currency?.let(::friendly) ?: "Sin moneda disponible")
        currencyButton?.active = !pending && !options?.currencies.isNullOrEmpty()
        confirmButton?.message = Component.literal(if (confirmArmed) "Confirmar" else "Publicar")
        confirmButton?.active = !pending && pokemon != null && currency != null
        priceBox?.setEditable(!pending)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 92, 0xFFFFFF)
        graphics.drawCenteredString(font, Component.literal("Selecciona un Pokémon de tu equipo"), width / 2, height / 2 - 73, 0xBBBBBB)
        if (message.isNotBlank()) graphics.drawCenteredString(font, Component.literal(message), width / 2, height / 2 + 76, 0xCCCCCC)
    }

    private fun friendly(value: String): String = value.substringAfter(':')
        .replace('_', ' ').replace('-', ' ').split(' ').filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}
