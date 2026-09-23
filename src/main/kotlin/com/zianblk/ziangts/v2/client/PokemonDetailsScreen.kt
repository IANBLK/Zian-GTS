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
        // Avoid Screen.renderBackground here: on in-game screens it applies Minecraft's
        // menu blur, which also softens this custom details surface on some clients.
        graphics.fill(0, 0, width, height, 0xD0101418.toInt())
        val panelW = minOf(500, width - 30)
        val panelH = minOf(330, height - 55)
        val left = (width - panelW) / 2
        val top = (height - panelH) / 2 - 6
        graphics.fill(left, top, left + panelW, top + panelH, 0xFF181C20.toInt())
        graphics.renderOutline(left, top, panelW, panelH, 0xFF666D75.toInt())
        graphics.drawCenteredString(font, title, width / 2, top + 10, 0xFFFFFF)

        val speciesName = entry.species.substringAfter(':').replaceFirstChar { it.uppercase() }
        val textX = left + 20
        val columnW = (panelW * 0.52).toInt()
        var y = top + 42
        fun line(label: String, value: String, color: Int = 0xFFE2E8F0.toInt()) {
            graphics.drawString(font, Component.literal("$label: $value"), textX, y, color, false)
            y += 15
        }
        line("Pokémon", speciesName, 0xFFF4D481.toInt())
        line("Nivel", entry.level.toString())
        line("Shiny", if (entry.shiny) "Sí" else "No")
        line("Alpha", if (entry.alpha) "Sí" else "No")
        line("Legendario", if (entry.legendary) "Sí" else "No")
        line("Género", localizedGender(entry.gender))
        line("Naturaleza", localizedNature(entry.nature))
        line("Habilidad", localizedAbility(entry.ability))
        line("Vendedor", entry.sellerName)
        line("Precio", "${entry.price} ${currencyDisplayName(entry.currency)}")
        y += 5
        graphics.drawString(font, Component.literal("IVs"), textX, y, 0xFFF4D481.toInt(), false)
        y += 14
        val ivLabels = listOf("PS", "At.", "Def.", "At. Esp.", "Def. Esp.", "Vel.")
        entry.ivs.zip(ivLabels).chunked(2).forEach { row ->
            val text = row.joinToString("   ") { (value, label) -> "$label: $value" }
            graphics.drawString(font, Component.literal(text), textX, y, 0xFFE2E8F0.toInt(), false)
            y += 12
        }
        y += 4
        graphics.drawString(font, Component.literal("Movimientos"), textX, y, 0xFFF4D481.toInt(), false)
        y += 14
        if (entry.moves.isEmpty()) {
            graphics.drawString(font, Component.literal("Sin movimientos"), textX, y, 0xFFA1A6AB.toInt(), false)
        } else {
            entry.moves.take(4).forEach { move ->
                val name = "• ${localizedMove(move)}"
                val fitted = font.plainSubstrByWidth(name, columnW)
                graphics.drawString(font, Component.literal(fitted), textX, y, 0xFFE2E8F0.toInt(), false)
                y += 12
            }
        }

        val stack = graphics.pose()
        stack.pushPose()
        try {
            stack.translate((left + panelW - 120).toDouble(), (top + 125).toDouble(), 100.0)
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
        // Render widgets explicitly. Calling Screen.render() would invoke the background path again.
        for (renderable in renderables) renderable.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun currencyDisplayName(value: String): String {
        val key = value.substringAfter(':').lowercase()
        val known = mapOf(
            "coppercoin" to "Copper Coin",
            "ironcoin" to "Iron Coin",
            "goldcoin" to "Gold Coin",
            "diamondcoin" to "Diamond Coin",
            "netheritecoin" to "Netherite Coin",
            "copperticket" to "Copper Ticket",
            "ironticket" to "Iron Ticket",
            "goldticket" to "Gold Ticket",
            "diamondticket" to "Diamond Ticket",
            "netheriteticket" to "Netherite Ticket"
        )
        return known[key] ?: friendly(value)
    }

    private fun localizedGender(value: String): String = when (value.substringAfter(':').uppercase()) {
        "MALE" -> "Macho"
        "FEMALE" -> "Hembra"
        "GENDERLESS" -> "Sin género"
        else -> friendly(value)
    }

    private fun localizedNature(value: String): String {
        val key = value.substringAfter(':').lowercase()
        val spanish = mapOf(
            "hardy" to "Fuerte", "lonely" to "Huraña", "brave" to "Audaz", "adamant" to "Firme", "naughty" to "Pícara",
            "bold" to "Osada", "docile" to "Dócil", "relaxed" to "Plácida", "impish" to "Agitada", "lax" to "Floja",
            "timid" to "Miedosa", "hasty" to "Activa", "serious" to "Seria", "jolly" to "Alegre", "naive" to "Ingenua",
            "modest" to "Modesta", "mild" to "Afable", "quiet" to "Mansa", "bashful" to "Tímida", "rash" to "Alocada",
            "calm" to "Serena", "gentle" to "Amable", "sassy" to "Grosera", "careful" to "Cauta", "quirky" to "Rara"
        )
        return spanish[key] ?: friendly(value)
    }

    private fun localizedAbility(value: String): String {
        val key = value.substringAfter(':').lowercase()
        val spanish = mapOf(
            "superluck" to "Afortunado", "multitype" to "Multitipo"
        )
        return spanish[key] ?: friendly(value)
    }

    private fun localizedMove(value: String): String {
        val key = value.substringAfter(':').lowercase().replace("_", "").replace("-", "")
        val spanish = mapOf(
            "feint" to "Amago", "quickattack" to "Ataque Rápido", "futuresight" to "Premonición", "doubleteam" to "Doble Equipo",
            "punishment" to "Castigo", "seismictoss" to "Movimiento Sísmico", "refresh" to "Alivio", "naturegift" to "Don Natural"
        )
        return spanish[key] ?: friendly(value)
    }

    private fun friendly(value: String): String =
        value.substringAfter(':').replace('_', ' ').split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    override fun isPauseScreen() = false
}
