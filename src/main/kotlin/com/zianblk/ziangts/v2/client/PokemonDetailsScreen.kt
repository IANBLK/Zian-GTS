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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
        val layout = DetailsLayout.calculate(width, height)
        addRenderableWidget(
            Button.builder(Component.literal("Volver")) { minecraft?.setScreen(parent) }
                .bounds(width / 2 - 45, layout.buttonY, 90, 20)
                .build()
        )
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xD0101418.toInt())
        val layout = DetailsLayout.calculate(width, height)
        val left = layout.left
        val top = layout.top
        graphics.fill(left, top, left + layout.width, top + layout.height, 0xFF181C20.toInt())
        graphics.renderOutline(left, top, layout.width, layout.height, 0xFF666D75.toInt())
        graphics.fill(left + 1, top + 1, left + layout.width - 1, top + 3, 0xFFF4D481.toInt())
        graphics.drawCenteredString(font, title, width / 2, top + 11, 0xFFFFFF)

        val speciesName = entry.species.substringAfter(':').replaceFirstChar { it.uppercase() }
        if (layout.compact) renderCompactDetails(graphics, layout, speciesName)
        else renderWideDetails(graphics, layout, speciesName)

        renderPokemonModel(graphics, layout, partialTick)
        for (renderable in renderables) renderable.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun renderWideDetails(graphics: GuiGraphics, layout: DetailsLayout, speciesName: String) {
        val textX = layout.contentLeft
        val columnW = (layout.width * 0.48).toInt()
        var y = layout.contentTop
        fun line(label: String, value: String, color: Int = 0xFFE2E8F0.toInt()) {
            graphics.drawString(font, Component.literal("${label}: ${value}"), textX, y, color, false)
            y += 14
        }
        line("Pokémon", speciesName, 0xFFF4D481.toInt())
        line("Nivel", entry.level.toString())
        val traits = listOfNotNull(if (entry.shiny) "Shiny" else null, if (entry.alpha) "Alpha" else null, if (entry.legendary) "Legendario" else null)
            .ifEmpty { listOf("Normal") }.joinToString(" · ")
        line("Rasgos", traits, if (traits == "Normal") 0xFFB8C0C8.toInt() else 0xFFF4D481.toInt())
        line("Género", localizedGender(entry.gender))
        componentLine(graphics, "Naturaleza", localizedNature(entry.nature), textX, y).also { y += 14 }
        componentLine(graphics, "Habilidad", localizedAbility(entry.ability), textX, y).also { y += 14 }
        line("Vendedor", entry.sellerName)
        line("Precio", "${entry.price} ${currencyDisplayName(entry.currency)}")
        y += 3
        graphics.drawString(font, Component.literal("IVs"), textX, y, 0xFFF4D481.toInt(), false)
        y += 13
        val ivLabels = listOf("PS", "At.", "Def.", "At.E", "Def.E", "Vel.")
        entry.ivs.zip(ivLabels).chunked(3).forEach { row ->
            graphics.drawString(font, Component.literal(row.joinToString("   ") { (v, l) -> "${l} ${v}" }), textX, y, 0xFFE2E8F0.toInt(), false)
            y += 12
        }
        y += 2
        graphics.drawString(font, Component.literal("Movimientos"), textX, y, 0xFFF4D481.toInt(), false)
        y += 13
        val moves = if (entry.moves.isEmpty()) listOf(Component.literal("Sin movimientos")) else entry.moves.take(4).map(::localizedMove)
        moves.forEach { move ->
            val rendered = Component.literal("• ").append(move)
            val fitted = font.split(rendered, columnW).firstOrNull() ?: rendered.visualOrderText
            graphics.drawString(font, fitted, textX, y, 0xFFE2E8F0.toInt(), false)
            y += 11
        }
        drawIvRadar(graphics, layout.rightCenterX, layout.radarY, 40, entry.ivs)
    }

    private fun renderCompactDetails(graphics: GuiGraphics, layout: DetailsLayout, speciesName: String) {
        val x = layout.contentLeft
        var y = layout.contentTop
        val safeW = (layout.width - 32).coerceAtLeast(120)
        graphics.drawString(font, Component.literal("${speciesName} · Nv. ${entry.level}"), x, y, 0xFFF4D481.toInt(), false)
        y += 14
        val traits = listOfNotNull(if (entry.shiny) "Shiny" else null, if (entry.alpha) "Alpha" else null, if (entry.legendary) "Legendario" else null).joinToString(" · ")
        if (traits.isNotEmpty()) {
            graphics.drawString(font, Component.literal(traits), x, y, 0xFFF4D481.toInt(), false)
            y += 13
        }
        graphics.drawString(font, Component.literal("Género: ${localizedGender(entry.gender)}"), x, y, 0xFFE2E8F0.toInt(), false); y += 13
        componentLine(graphics, "Naturaleza", localizedNature(entry.nature), x, y); y += 13
        componentLine(graphics, "Habilidad", localizedAbility(entry.ability), x, y); y += 13
        graphics.drawString(font, Component.literal("Precio: ${entry.price} ${currencyDisplayName(entry.currency)}"), x, y, 0xFFE2E8F0.toInt(), false); y += 16
        val ivLabels = listOf("PS", "At", "Def", "AtE", "DfE", "Vel")
        val ivText = entry.ivs.zip(ivLabels).joinToString("  ") { (v, l) -> "${l}:${v}" }
        font.split(Component.literal(ivText), safeW).firstOrNull()?.let { graphics.drawString(font, it, x, y, 0xFFF4D481.toInt(), false) }
        y += 15
        graphics.drawString(font, Component.literal("Movimientos"), x, y, 0xFFF4D481.toInt(), false); y += 12
        if (entry.moves.isEmpty()) {
            graphics.drawString(font, Component.literal("Sin movimientos"), x, y, 0xFFA1A6AB.toInt(), false)
        } else {
            entry.moves.take(2).forEach { move ->
                font.split(Component.literal("• ").append(localizedMove(move)), safeW).firstOrNull()?.let {
                    graphics.drawString(font, it, x, y, 0xFFE2E8F0.toInt(), false)
                }
                y += 11
            }
            if (entry.moves.size > 2) graphics.drawString(font, Component.literal("+${entry.moves.size - 2} más"), x, y, 0xFFA1A6AB.toInt(), false)
        }
    }

    private fun renderPokemonModel(graphics: GuiGraphics, layout: DetailsLayout, partialTick: Float) {
        val modelScale = if (layout.compact) 34f else 55.2f
        val modelX = if (layout.compact) layout.left + layout.width - 55 else layout.rightCenterX
        val stack = graphics.pose()
        stack.pushPose()
        try {
            stack.translate(modelX.toDouble(), layout.modelY.toDouble(), 100.0)
            pose.currentAspects = buildSet {
                if (entry.shiny) add("shiny")
                if (entry.alpha) add("alpha")
            }
            drawProfilePokemon(ResourceLocation.parse(entry.species), stack, Quaternionf().rotationXYZ(0.08f, 0.45f, 0f), state = pose, partialTicks = partialTick, scale = modelScale)
        } finally {
            stack.popPose()
        }
    }

    private fun drawIvRadar(graphics: GuiGraphics, cx: Int, cy: Int, radius: Int, ivs: List<Int>) {
        val labels = listOf("PS", "At.", "Def.", "At.E", "Def.E", "Vel.")
        fun point(index: Int, r: Double): Pair<Int, Int> {
            val angle = -PI / 2.0 + index * (PI / 3.0)
            return (cx + cos(angle) * r).toInt() to (cy + sin(angle) * r).toInt()
        }
        // Concentric guides make low/high IV distributions readable without adding more text clutter.
        listOf(0.33, 0.66, 1.0).forEach { scale ->
            val pts = (0 until 6).map { point(it, radius * scale) }
            for (i in pts.indices) {
                val a = pts[i]; val b = pts[(i + 1) % pts.size]
                drawLine(graphics, a.first, a.second, b.first, b.second, 0xFF4B535C.toInt())
            }
        }
        for (i in 0 until 6) {
            val edge = point(i, radius.toDouble())
            drawLine(graphics, cx, cy, edge.first, edge.second, 0xFF3D444C.toInt())
        }
        if (ivs.size == 6) {
            val pts = ivs.mapIndexed { index, value -> point(index, radius * value.coerceIn(0, 31) / 31.0) }
            for (i in pts.indices) {
                val a = pts[i]; val b = pts[(i + 1) % pts.size]
                drawLine(graphics, a.first, a.second, b.first, b.second, 0xFFF4D481.toInt())
                graphics.fill(a.first - 1, a.second - 1, a.first + 2, a.second + 2, 0xFFF4D481.toInt())
            }
        }
        labels.forEachIndexed { index, label ->
            val p = point(index, radius + 13.0)
            graphics.drawCenteredString(font, Component.literal(label), p.first, p.second - 4, 0xFFB8C0C8.toInt())
        }
        graphics.drawCenteredString(font, Component.literal("IVs"), cx, cy - radius - 30, 0xFFF4D481.toInt())
    }

    private fun drawLine(graphics: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        var x = x0; var y = y0
        val dx = kotlin.math.abs(x1 - x0)
        val sx = if (x0 < x1) 1 else -1
        val dy = -kotlin.math.abs(y1 - y0)
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
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

    private fun componentLine(graphics: GuiGraphics, label: String, value: Component, x: Int, y: Int) {
        graphics.drawString(font, Component.literal("$label: ").append(value), x, y, 0xFFE2E8F0.toInt(), false)
    }

    /**
     * Cobblemon owns these translations. Keeping the value as a Component means Minecraft resolves
     * it with the client's selected language instead of Zian GTS maintaining hundreds of names.
     */
    private fun localizedNature(value: String): Component =
        cobblemonTranslation("cobblemon.nature.", value)

    private fun localizedAbility(value: String): Component =
        cobblemonTranslation("cobblemon.ability.", value)

    private fun localizedMove(value: String): Component =
        cobblemonTranslation("cobblemon.move.", value)

    private fun cobblemonTranslation(prefix: String, value: String): Component {
        val id = value.substringAfter(':').lowercase().replace("_", "").replace("-", "")
        if (id.isBlank()) return Component.literal(friendly(value))
        val key = prefix + id
        val translated = Component.translatable(key)
        // Minecraft will render the key itself when a dependency does not provide it. Keep a
        // readable fallback for unusual addon-provided values while using Cobblemon dynamically.
        return if (net.minecraft.client.resources.language.I18n.exists(key)) translated
        else Component.literal(friendly(value))
    }

    private fun friendly(value: String): String =
        value.substringAfter(':').replace('_', ' ').split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    override fun isPauseScreen() = false
}
