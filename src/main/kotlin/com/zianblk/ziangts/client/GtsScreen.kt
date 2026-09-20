package com.zianblk.ziangts.client

import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.zianblk.ziangts.ZianGts
import com.zianblk.ziangts.network.GtsNetwork
import com.zianblk.ziangts.network.MarketPage
import com.zianblk.ziangts.network.MarketRequest
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Quaternionf
import java.util.UUID

@EventBusSubscriber(modid = ZianGts.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object GtsClientEvents {
    // Kotlin for Forge registers Kotlin objects by INSTANCE, not by Java class.
    @SubscribeEvent
    fun setup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            GtsNetwork.clientReceiver = { page ->
                val client = Minecraft.getInstance()
                val screen = client.screen
                if (screen is GtsScreen) screen.update(page)
                else if (page.openScreen) client.setScreen(GtsScreen(page))
            }
        }
    }
}

class GtsScreen(private var page: MarketPage) : Screen(Component.translatable("gui.ziangts.title")) {
    private var selected = 0
    private var confirmation: String? = null
    private var pose = FloatingState()
    private var left = 0
    private var top = 0
    private var panelWidth = 0
    private var panelHeight = 0
    private var listWidth = 130
    private val filterKeys = arrayOf("all", "shiny", "alpha", "legendary", "legendary_shiny", "mine")
    private val sortKeys = arrayOf("newest", "oldest", "price_low", "price_high", "level_low", "level_high")

    /** Profile models use their native proportions; large bodies need a lower
     * scale so they remain inside the preview card instead of being clipped. */
    private fun previewScale(species: String): Float = when (species.substringAfter(':')) {
        "snorlax", "wailord", "rayquaza", "kyogre", "groudon", "eternatus", "lugia",
        "ho_oh", "steelix", "gyarados", "torterra" -> 27f
        "charizard", "dragonite", "lapras", "blastoise", "venusaur", "milotic",
        "tyranitar", "metagross", "ursaluna" -> 32f
        else -> 38f
    }

    fun update(next: MarketPage) {
        val id = page.entries.getOrNull(selected)?.id
        page = next
        selected = next.entries.indexOfFirst { it.id == id }.coerceAtLeast(0)
        confirmation = null
        pose = FloatingState()
        rebuildWidgets()
    }

    override fun init() {
        panelWidth = minOf(width - 16, 560)
        panelHeight = minOf(height - 16, 300)
        listWidth = if (panelWidth < 420) 96 else 130
        left = (width - panelWidth) / 2
        top = (height - panelHeight) / 2
        button(left + 8, top + 24, listWidth, Component.translatable("gui.ziangts.filter.${filterKeys[page.filter]}")) {
            request(filter = (page.filter + 1) % filterKeys.size, number = 1)
        }
        button(left + listWidth + 16, top + 24, panelWidth - listWidth - 88, Component.translatable("gui.ziangts.sort.${sortKeys[page.sort]}")) {
            request(sort = (page.sort + 1) % sortKeys.size, number = 1)
        }
        button(left + panelWidth - 64, top + 24, 56, Component.translatable("gui.ziangts.refresh")) { request() }
        page.entries.forEachIndexed { index, entry ->
            val label = (if (entry.shiny) "★ " else "") + entry.name
            button(left + 8, top + 50 + index * 22, listWidth, Component.literal(label)) {
                selected = index
                confirmation = null
                pose = FloatingState()
                rebuildWidgets()
            }
        }
        val bottom = top + panelHeight - 26
        button(left + 8, bottom, 30, Component.literal("<")) { request(number = page.page - 1) }.active = page.page > 1
        button(left + listWidth - 22, bottom, 30, Component.literal(">")) { request(number = page.page + 1) }.active = page.page < page.pages
        button(left + listWidth + 16, bottom, 80, Component.translatable("gui.ziangts.claim")) { request(action = 3) }
        val entry = page.entries.getOrNull(selected)
        val key = when {
            entry?.mine == true -> "remove_listing"
            entry != null && confirmation == entry.id -> "purchase_confirm"
            else -> "purchase"
        }
        button(left + listWidth + 100, bottom, panelWidth - listWidth - 108, Component.translatable("gui.ziangts.$key")) {
            if (entry != null) {
                if (entry.mine) request(action = 2, id = UUID.fromString(entry.id))
                else if (confirmation == entry.id) {
                    confirmation = null
                    request(action = 1, id = UUID.fromString(entry.id))
                } else {
                    confirmation = entry.id
                    rebuildWidgets()
                }
            }
        }.active = entry != null && (!entry.expired || entry.mine)
    }

    private fun button(x: Int, y: Int, w: Int, text: Component, action: () -> Unit): Button =
        addRenderableWidget(Button.builder(text) { action() }.bounds(x, y, w, 20).build())

    private fun request(action: Int = 0, id: UUID = UUID(0, 0), number: Int = page.page,
                        filter: Int = page.filter, sort: Int = page.sort) {
        PacketDistributor.sendToServer(MarketRequest(action, id, number.coerceAtLeast(1), filter, sort))
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // NeoForge/Minecraft may render a blurred world behind Screen.  Every
        // GTS surface is intentionally opaque so that blur cannot bleed through
        // text, stats or the Pokémon preview.
        graphics.fill(0, 0, width, height, 0xFF0B1220.toInt())
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF182232.toInt())
        graphics.fill(left + 4, top + 44, left + listWidth + 4, top + panelHeight - 30, 0xFF101927.toInt())
        graphics.fill(left + listWidth + 12, top + 44, left + panelWidth - 4, top + panelHeight - 30, 0xFF1D2A3C.toInt())
        // Widgets (and any NeoForge background pass they trigger) must be
        // composed before the market content. Text and the Pokémon preview are
        // deliberately the final opaque layer below the tooltip.
        super.render(graphics, mouseX, mouseY, partialTick)
        var tooltip: Component? = null
        val heading = if (page.notice.isBlank()) title else Component.translatable(page.notice)
        graphics.drawString(font, font.plainSubstrByWidth(heading.string, panelWidth - 16), left + 8, top + 9, 0xF4D481, false)
        if (mouseX in left..(left + panelWidth) && mouseY in (top + 8)..(top + 20)) tooltip = heading
        graphics.drawCenteredString(font, "${page.page}/${page.pages}", left + 8 + listWidth / 2, top + panelHeight - 20, 0xFFFFFF)
        val entry = page.entries.getOrNull(selected)
        if (entry == null) graphics.drawString(font, Component.translatable("gui.ziangts.empty"), left + listWidth + 20, top + 56, 0xFFFFFF)
        else {
            val x = left + listWidth + 20
            val detailRight = left + panelWidth - 12
            val previewSize = 128
            val previewX = detailRight - previewSize
            val previewTop = top + 50
            graphics.fill(previewX, previewTop, detailRight, previewTop + previewSize, 0xFF111B2A.toInt())
            graphics.renderOutline(previewX, previewTop, previewSize, previewSize, 0xFF53677F.toInt())
            graphics.drawString(font, "Vista previa", previewX + 7, previewTop + 7, 0x9FB3CB, false)
            var y = top + 51
            fun line(component: Component, color: Int = 0xE2E8F0) {
                val lineRight = if (y < previewTop + previewSize) previewX - 8 else detailRight
                val lineWidth = (lineRight - x).coerceAtLeast(20)
                graphics.drawString(font, font.plainSubstrByWidth(component.string, lineWidth), x, y, color, false)
                // Only the glyphs themselves are hoverable.  The old full-row
                // hitbox covered the preview, so hovering the Pokémon showed
                // the tooltip for whichever stat happened to be at that Y.
                val textWidth = minOf(font.width(component), lineWidth)
                if (mouseX in x..(x + textWidth) && mouseY in y..(y + 10)) tooltip = component
                y += 11
            }
            line(Component.literal(entry.name + if (entry.expired) " ⌛" else ""), 0xF4D481)
            line(Component.translatable("gui.ziangts.seller", entry.seller))
            line(Component.translatable("gui.ziangts.price", "${entry.price} ${entry.currency}").append(" · ").append(Component.translatable("gui.ziangts.payment.${entry.economyProvider}")))
            line(Component.translatable("gui.ziangts.level", entry.level))
            line(Component.translatable("gui.ziangts.gender", Component.translatable("gui.ziangts.gender.${entry.gender.lowercase()}")))
            line(Component.translatable("gui.ziangts.shiny", Component.translatable(if (entry.shiny) "gui.yes" else "gui.no")))
            // Only Nature and Ability are omitted from this detail panel.
            line(Component.translatable("gui.ziangts.stats"), 0xF4D481)
            val names = arrayOf("hp", "attack", "defence", "special_attack", "special_defence", "speed")
            names.forEachIndexed { index, stat ->
                line(Component.translatable("cobblemon.stat.$stat.name").append(
                    " ${entry.stats[index]}  IV ${entry.ivs[index]}"))
            }
            if (panelWidth >= 420) {
                val stack = graphics.pose()
                stack.pushPose()
                try {
                    stack.translate((previewX + previewSize / 2).toDouble(), (previewTop + 67).toDouble(), 100.0)
                    pose.currentAspects = entry.aspects.toSet()
                    drawProfilePokemon(ResourceLocation.parse(entry.species), stack, Quaternionf().rotationXYZ(0.1f, 0.5f, 0f),
                        state = pose, partialTicks = partialTick, scale = previewScale(entry.species))
                } finally { stack.popPose() }
                graphics.drawCenteredString(font, entry.name, previewX + previewSize / 2,
                    previewTop + previewSize - 14, 0xF4D481)
            }
        }
        tooltip?.let { graphics.renderTooltip(font, it, mouseX, mouseY) }
    }

    override fun isPauseScreen() = false
}
