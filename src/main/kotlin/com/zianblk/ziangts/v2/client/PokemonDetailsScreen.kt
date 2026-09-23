package com.zianblk.ziangts.v2.client

import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.zianblk.ziangts.v2.network.MarketEntryDto
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf

/**
 * Read-only details surface for a market offer.
 *
 * Only server-projected fields are rendered here. More sensitive Pokémon data
 * (IVs, EVs, ability, nature and moves) will be added through an explicit
 * details request instead of trusting client-side guesses.
 */
class PokemonDetailsScreen(
    private val entry: MarketEntryDto,
    private val parent: Screen
) : Screen(Component.literal("Detalles del Pokémon")) {
    private val pose = FloatingState()

    override fun init() {
        addRenderableWidget(
            Button.builder(Component.literal("Volver")) { minecraft?.setScreen(parent) }
                .bounds(width / 2 - 45, height - 34, 90, 20)
                .build()
        )
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        val panelW = minOf(430, width - 30)
        val panelH = minOf(250, height - 55)
        val left = (width - panelW) / 2
        val top = (height - panelH) / 2 - 6
        graphics.fill(left, top, left + panelW, top + panelH, 0xE0181C20.toInt())
        graphics.renderOutline(left, top, panelW, panelH, 0xFF666D75.toInt())
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFF)

        val speciesName = entry.species.substringAfter(':').replaceFirstChar { it.uppercase() }
        val textX = left + 20
        var y = top + 42
        fun line(label: String, value: String, color: Int = 0xFFE2E8F0.toInt()) {
            graphics.drawString(font, Component.literal("$label: $value"), textX, y, color, false)
            y += 15
        }
        line("Pokémon", speciesName, 0xFFF4D481.toInt())
        line("Nivel", entry.level.toString())
        line("Shiny", if (entry.shiny) "Sí" else "No")
        line("Alpha", if (entry.alpha) "Sí" else "No")
        line("Vendedor", entry.sellerName)
        line("Precio", entry.price.toString())

        val stack = graphics.pose()
        stack.pushPose()
        try {
            stack.translate((left + panelW - 105).toDouble(), (top + 100).toDouble(), 100.0)
            pose.currentAspects = buildSet {
                if (entry.shiny) add("shiny")
                if (entry.alpha) add("alpha")
            }
            drawProfilePokemon(
                ResourceLocation.parse(entry.species),
                stack,
                Quaternionf().rotationXYZ(0.08f, 0.45f, 0f),
                state = pose,
                partialTicks = partialTick,
                scale = 48f
            )
        } finally {
            stack.popPose()
        }
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen() = false
}
