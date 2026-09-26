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

class PokemonDetailsScreen(private val entry: MarketEntryDto, private val parent: Screen) : Screen(Component.literal("Detalles del Pokémon")) {
    private val pose = FloatingState()

    override fun init() {
        val layout = DetailsLayout.calculate(width, height)
        val previewW = (layout.width * 0.27).toInt().coerceIn(82, 105)
        val previewX = layout.left + 12
        val buttonW = 80
        val buttonX = previewX + (previewW - buttonW) / 2
        addRenderableWidget(Button.builder(Component.literal("Volver")) { minecraft?.setScreen(parent) }
            .bounds(buttonX, layout.buttonY - 24, buttonW, 18).build())
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
        if (layout.compact) renderCompactDetails(graphics, layout, speciesName) else renderWideDetails(graphics, layout, speciesName)
        renderPokemonModel(graphics, layout, partialTick)
        for (renderable in renderables) renderable.render(graphics, mouseX, mouseY, partialTick)
    }

    private fun renderWideDetails(graphics: GuiGraphics, layout: DetailsLayout, speciesName: String) {
        val pad = 12
        val top = layout.top + 32
        val previewW = (layout.width * 0.27).toInt().coerceIn(82, 105)
        val radarW = (layout.width * 0.38).toInt().coerceIn(126, 154)
        val previewX = layout.left + pad
        val infoX = previewX + previewW + 10
        val radarX = layout.left + layout.width - pad - radarW
        val infoW = (radarX - infoX - 8).coerceAtLeast(108)
        val topH = (layout.height * 0.56).toInt().coerceIn(122, 140)
        drawPanel(graphics, previewX, top, previewW, topH)
        drawPanel(graphics, infoX, top, infoW, topH)
        drawPanel(graphics, radarX, top, radarW, topH)

        var y = top + 10
        graphics.drawString(font, Component.literal(speciesName), infoX + 8, y, 0xFFF4D481.toInt(), false); y += 12
        graphics.drawString(font, Component.literal("Nv. " + entry.level), infoX + 8, y, 0xFFB8C0C8.toInt(), false); y += 12
        drawTraitRow(graphics, infoX + 8, y); y += 32
        componentLine(graphics, "Naturaleza", localizedNature(entry.nature), infoX + 8, y); y += 11
        componentLine(graphics, "Habilidad", localizedAbility(entry.ability), infoX + 8, y); y += 11
        graphics.drawString(font, Component.literal("Género: " + localizedGender(entry.gender)), infoX + 8, y, 0xFFE2E8F0.toInt(), false); y += 11
        graphics.drawString(font, Component.literal("Vendedor: " + entry.sellerName), infoX + 8, y, 0xFFE2E8F0.toInt(), false); y += 11
        graphics.drawString(font, Component.literal(expiryLabel()), infoX + 8, minOf(y, top + topH - 13), 0xFFF4D481.toInt(), false)

        drawIvNumbers(graphics, radarX + 8, top + 27, entry.ivs)
        val radarRadius = 25
        val radarCx = radarX + radarW - 42
        val radarCy = top + topH / 2 + 8
        drawIvRadar(graphics, radarCx, radarCy, radarRadius, entry.ivs)

        val bottomY = top + topH + 7
        val bottomH = (layout.top + layout.height - 8 - bottomY).coerceAtLeast(48)
        val controlsX = layout.left + pad
        val controlsW = 104
        val movesX = controlsX + controlsW + 12
        val movesW = layout.left + layout.width - pad - movesX
        drawPanel(graphics, movesX, bottomY, movesW, bottomH)
        graphics.drawString(font, Component.literal("Movimientos"), movesX + 8, bottomY + 5, 0xFFF4D481.toInt(), false)
        val moves = entry.moves.take(4)
        val cellGap = 8
        val innerLeft = movesX + 8
        val innerRight = movesX + movesW - 8
        val cellW = ((innerRight - innerLeft - cellGap) / 2).coerceAtLeast(30)
        val cellH = 13
        moves.forEachIndexed { index, move ->
            val mx = innerLeft + (index % 2) * (cellW + cellGap)
            val my = bottomY + 17 + (index / 2) * 16
            graphics.fill(mx, my, mx + cellW, my + cellH, 0xA0212931.toInt())
            graphics.renderOutline(mx, my, cellW, cellH, 0xFF526575.toInt())
            graphics.drawString(font, Component.literal("• ").append(localizedMove(move)), mx + 4, my + 2, 0xFFE2E8F0.toInt(), false)
        }
        if (moves.isEmpty()) graphics.drawString(font, Component.literal("Sin movimientos"), innerLeft, bottomY + 20, 0xFFA1A6AB.toInt(), false)
    }

    private fun renderCompactDetails(graphics: GuiGraphics, layout: DetailsLayout, speciesName: String) {
        val x = layout.contentLeft
        var y = layout.contentTop
        graphics.drawString(font, Component.literal(speciesName + " · Nv. " + entry.level), x, y, 0xFFF4D481.toInt(), false); y += 14
        graphics.drawString(font, Component.literal("Género: " + localizedGender(entry.gender)), x, y, 0xFFE2E8F0.toInt(), false); y += 13
        componentLine(graphics, "Naturaleza", localizedNature(entry.nature), x, y); y += 13
        componentLine(graphics, "Habilidad", localizedAbility(entry.ability), x, y); y += 13
        graphics.drawString(font, Component.literal("Vendedor: " + entry.sellerName), x, y, 0xFFE2E8F0.toInt(), false); y += 13
        graphics.drawString(font, Component.literal(expiryLabel()), x, y, 0xFFF4D481.toInt(), false); y += 16
        graphics.drawString(font, Component.literal("Movimientos"), x, y, 0xFFF4D481.toInt(), false); y += 12
        entry.moves.take(2).forEach { move -> graphics.drawString(font, Component.literal("• ").append(localizedMove(move)), x, y, 0xFFE2E8F0.toInt(), false); y += 11 }
    }

    private fun drawPanel(graphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        graphics.fill(x, y, x + w, y + h, 0xB00D151D.toInt())
        graphics.renderOutline(x, y, w, h, 0xFF526575.toInt())
    }

    private fun drawTraitRow(graphics: GuiGraphics, x: Int, y: Int) {
        val rows = listOf("Shiny: " + if (entry.shiny) "Sí" else "No", "Alpha: " + if (entry.alpha) "Sí" else "No", "Legendario: " + if (entry.legendary) "Sí" else "No")
        rows.forEachIndexed { index, label -> graphics.drawString(font, Component.literal(label), x, y + index * 11, 0xFFE2E8F0.toInt(), false) }
    }

    private fun drawIvNumbers(graphics: GuiGraphics, x: Int, y: Int, ivs: List<Int>) {
        val labels = listOf("PS", "At", "Def", "At.E", "Def.E", "Vel")
        labels.forEachIndexed { index, label ->
            val value = ivs.getOrNull(index)?.coerceIn(0, 31) ?: 0
            graphics.drawString(font, Component.literal("$label: ${value.toString().padStart(2, '0')}/31"), x, y + index * 13, 0xFFE2E8F0.toInt(), false)
        }
    }

    private fun expiryLabel(): String {
        val seconds = ((entry.expiresAtEpochMilli - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        return if (seconds >= 3600) "Expira en: " + seconds / 3600 + "h " + (seconds % 3600) / 60 + "m" else "Expira en: " + seconds / 60 + "m"
    }

    private fun renderPokemonModel(graphics: GuiGraphics, layout: DetailsLayout, partialTick: Float) {
        val modelScale = if (layout.compact) 30f else 37.5f
        val modelX = if (layout.compact) layout.left + layout.width - 55 else layout.left + 12 + (layout.width * 0.27).toInt().coerceIn(82, 105) / 2
        val stack = graphics.pose(); stack.pushPose()
        try {
            stack.translate(modelX.toDouble(), (layout.modelY + 6).toDouble(), 100.0)
            pose.currentAspects = buildSet { if (entry.shiny) add("shiny"); if (entry.alpha) add("alpha") }
            drawProfilePokemon(ResourceLocation.parse(entry.species), stack, Quaternionf().rotationXYZ(0.08f, 0.45f, 0f), state = pose, partialTicks = partialTick, scale = modelScale)
        } finally { stack.popPose() }
    }

    private fun drawIvRadar(graphics: GuiGraphics, cx: Int, cy: Int, radius: Int, ivs: List<Int>) {
        val labels = listOf("PS", "At.", "Def.", "At.E", "Def.E", "Vel.")
        fun point(index: Int, r: Double): Pair<Int, Int> { val angle = -PI / 2.0 + index * (PI / 3.0); return (cx + cos(angle) * r).toInt() to (cy + sin(angle) * r).toInt() }
        listOf(0.33, 0.66, 1.0).forEach { scale -> val pts = (0 until 6).map { point(it, radius * scale) }; for (i in pts.indices) { val a = pts[i]; val b = pts[(i + 1) % pts.size]; drawLine(graphics, a.first, a.second, b.first, b.second, 0xFF4B535C.toInt()) } }
        for (i in 0 until 6) { val edge = point(i, radius.toDouble()); drawLine(graphics, cx, cy, edge.first, edge.second, 0xFF3D444C.toInt()) }
        if (ivs.size == 6) { val pts = ivs.mapIndexed { index, value -> point(index, radius * value.coerceIn(0, 31) / 31.0) }; for (i in pts.indices) { val a = pts[i]; val b = pts[(i + 1) % pts.size]; drawLine(graphics, a.first, a.second, b.first, b.second, 0xFFF4D481.toInt()); graphics.fill(a.first - 1, a.second - 1, a.first + 2, a.second + 2, 0xFFF4D481.toInt()) } }
        labels.forEachIndexed { index, label -> val p = point(index, radius + 13.0); graphics.drawCenteredString(font, Component.literal(label), p.first, p.second - 4, 0xFFB8C0C8.toInt()) }
        graphics.drawCenteredString(font, Component.literal("IVs"), cx, cy - radius - 30, 0xFFF4D481.toInt())
    }

    private fun drawLine(graphics: GuiGraphics, x0: Int, y0: Int, x1: Int, y1: Int, color: Int) {
        var x = x0; var y = y0; val dx = kotlin.math.abs(x1 - x0); val sx = if (x0 < x1) 1 else -1; val dy = -kotlin.math.abs(y1 - y0); val sy = if (y0 < y1) 1 else -1; var err = dx + dy
        while (true) { graphics.fill(x, y, x + 1, y + 1, color); if (x == x1 && y == y1) break; val e2 = 2 * err; if (e2 >= dy) { err += dy; x += sx }; if (e2 <= dx) { err += dx; y += sy } }
    }

    private fun localizedGender(value: String): String = when (value.substringAfter(':').uppercase()) { "MALE" -> "Macho"; "FEMALE" -> "Hembra"; "GENDERLESS" -> "Sin género"; else -> friendly(value) }
    private fun componentLine(graphics: GuiGraphics, label: String, value: Component, x: Int, y: Int) { graphics.drawString(font, Component.literal("$label: ").append(value), x, y, 0xFFE2E8F0.toInt(), false) }
    private fun localizedNature(value: String): Component = cobblemonTranslation("cobblemon.nature.", value)
    private fun localizedAbility(value: String): Component = cobblemonTranslation("cobblemon.ability.", value)
    private fun localizedMove(value: String): Component = cobblemonTranslation("cobblemon.move.", value)
    private fun cobblemonTranslation(prefix: String, value: String): Component {
        val id = value.substringAfter(':').lowercase().replace("_", "").replace("-", "")
        if (id.isBlank()) return Component.literal(friendly(value))
        val key = prefix + id; val translated = Component.translatable(key)
        return if (net.minecraft.client.resources.language.I18n.exists(key)) translated else Component.literal(friendly(value))
    }
    private fun friendly(value: String): String = value.substringAfter(':').replace('_', ' ').split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    override fun isPauseScreen() = false
}
