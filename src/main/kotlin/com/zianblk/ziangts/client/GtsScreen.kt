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
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Quaternionf
import java.util.UUID
import java.util.IdentityHashMap

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
    private val buttonIcons = IdentityHashMap<Button, ItemStack>()
    private val filterKeys = arrayOf("all", "shiny", "alpha", "legendary", "legendary_shiny", "mine")
    private val sortKeys = arrayOf("newest", "oldest", "price_low", "price_high", "level_low", "level_high")
    // Leave a dedicated row for the section captions below the toolbar.
    private val entryTopOffset = 64

    /** Profile models use their native proportions; large bodies need a lower
     * scale so they remain inside the preview card instead of being clipped. */
    private fun previewScale(species: String): Float = when (species.substringAfter(':')) {
        "snorlax", "wailord", "rayquaza", "kyogre", "groudon", "eternatus", "lugia",
        "ho_oh", "steelix", "gyarados", "torterra", "giratina", "dialga", "palkia",
        "arceus", "reshiram", "zekrom", "zacian", "zamazenta" -> 30f
        "charizard", "dragonite", "lapras", "blastoise", "venusaur", "milotic",
        "tyranitar", "metagross", "ursaluna" -> 36f
        else -> 46f
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
        // rebuildWidgets() removes the vanilla widgets, but our custom icon
        // registry is independent of Screen's child list. Clear it as well,
        // otherwise old buttons are painted over the newly created ones after
        // changing page, filter, selection or purchase confirmation.
        buttonIcons.clear()
        panelWidth = minOf(width - 16, 560)
        panelHeight = minOf(height - 16, 300)
        listWidth = if (panelWidth < 420) 96 else 130
        left = (width - panelWidth) / 2
        top = (height - panelHeight) / 2
        button(left + 8, top + 24, listWidth, Component.translatable("gui.ziangts.filter.${filterKeys[page.filter]}"), Items.CHEST) {
            request(filter = (page.filter + 1) % filterKeys.size, number = 1)
        }
        button(left + listWidth + 16, top + 24, panelWidth - listWidth - 88, Component.translatable("gui.ziangts.sort.${sortKeys[page.sort]}"), Items.CLOCK) {
            request(sort = (page.sort + 1) % sortKeys.size, number = 1)
        }
        button(left + panelWidth - 64, top + 24, 56, Component.translatable("gui.ziangts.refresh"), Items.COMPASS) { request() }
        page.entries.forEachIndexed { index, entry ->
            val label = (if (entry.shiny) "★ " else "") + entry.name
            button(left + 8, top + entryTopOffset + index * 23, listWidth, Component.literal(label)) {
                selected = index
                confirmation = null
                pose = FloatingState()
                rebuildWidgets()
            }
        }
        val bottom = top + panelHeight - 26
        button(left + 8, bottom, 30, Component.literal("<")) { request(number = page.page - 1) }.active = page.page > 1
        button(left + listWidth - 22, bottom, 30, Component.literal(">")) { request(number = page.page + 1) }.active = page.page < page.pages
        button(left + listWidth + 16, bottom, 80, Component.translatable("gui.ziangts.claim"), Items.CHEST) { request(action = 3) }
        val entry = page.entries.getOrNull(selected)
        val key = when {
            entry?.mine == true -> "remove_listing"
            entry != null && confirmation == entry.id -> "purchase_confirm"
            else -> "purchase"
        }
        button(left + listWidth + 100, bottom, panelWidth - listWidth - 108, Component.translatable("gui.ziangts.$key"),
            if (entry?.mine == true) Items.BARRIER else Items.EMERALD) {
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

    private fun button(x: Int, y: Int, w: Int, text: Component, icon: net.minecraft.world.item.Item? = null,
                       action: () -> Unit): Button {
        val widget = addRenderableWidget(Button.builder(text) { action() }.bounds(x, y, w, 20).build())
        buttonIcons[widget] = icon?.let { ItemStack(it) } ?: ItemStack.EMPTY
        return widget
    }

    private fun drawAvecoinsButtons(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        buttonIcons.forEach { (button, icon) ->
            if (!button.visible) return@forEach
            val hovered = button.isHoveredOrFocused
            val background = when {
                !button.active -> 0xFF25292D.toInt()
                hovered -> 0xFF3A4046.toInt()
                else -> 0xFF343A40.toInt()
            }
            val border = if (hovered && button.active) 0xFFE5E7E9.toInt() else 0xFF5D646B.toInt()
            graphics.fill(button.x, button.y, button.x + button.width, button.y + button.height, background)
            graphics.renderOutline(button.x, button.y, button.width, button.height, border)
            val hasIcon = !icon.isEmpty
            if (hasIcon) graphics.renderItem(icon, button.x + 4, button.y + 2)
            val sidePadding = if (hasIcon) 25 else 12
            val available = (button.width - sidePadding).coerceAtLeast(8)
            val label = font.plainSubstrByWidth(button.message.string, available)
            val textX = button.x + (if (hasIcon) 23 else 6) + (available - font.width(label)) / 2
            val textColor = if (button.active) 0xFFE5E7E9.toInt() else 0xFFA1A6AB.toInt()
            graphics.drawString(font, Component.literal(label), textX, button.y + 6, textColor, false)
        }
    }

    private fun request(action: Int = 0, id: UUID = UUID(0, 0), number: Int = page.page,
                        filter: Int = page.filter, sort: Int = page.sort) {
        PacketDistributor.sendToServer(MarketRequest(action, id, number.coerceAtLeast(1), filter, sort))
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Render only NeoForge's native background/blur. Screen.render() also
        // paints vanilla widgets, which would leave a second label beneath
        // our AVECOINS-styled controls.
        renderBackground(graphics, mouseX, mouseY, partialTick)
        // Every GTS surface is opaque, so the native blur cannot bleed through
        // text, stats or the Pokémon preview.
        // AVECOINS 2.3 GuiTheme palette: charcoal surfaces, neutral borders
        // and muted dividers.  The values are reproduced locally so the GTS
        // remains independent of the proprietary client classes.
        graphics.fill(0, 0, width, height, 0xFF101214.toInt())
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xFF171A1D.toInt())
        graphics.fill(left + 4, top + 4, left + panelWidth - 4, top + 44, 0xFF24282C.toInt())
        graphics.fill(left + 4, top + 48, left + listWidth + 4, top + panelHeight - 30, 0xFF202428.toInt())
        graphics.fill(left + listWidth + 12, top + 48, left + panelWidth - 4, top + panelHeight - 30, 0xFF202428.toInt())
        graphics.renderOutline(left, top, panelWidth, panelHeight, 0xFF5D646B.toInt())
        graphics.renderOutline(left + 4, top + 48, listWidth, panelHeight - 78, 0xFF5D646B.toInt())
        graphics.renderOutline(left + listWidth + 12, top + 48, panelWidth - listWidth - 16, panelHeight - 78, 0xFF5D646B.toInt())
        graphics.vLine(left + listWidth + 8, top + 48, top + panelHeight - 30, 0xFF3A3F44.toInt())
        // Paint the styled controls exactly once; vanilla widget rendering is
        // intentionally omitted to avoid duplicated/overlapping labels.
        drawAvecoinsButtons(graphics, mouseX, mouseY)
        // Text and the Pokémon preview are deliberately the final layer below
        // the tooltip.
        var tooltip: Component? = null
        val heading = if (page.notice.isBlank()) title else Component.translatable(page.notice)
        graphics.drawCenteredString(font, font.plainSubstrByWidth(heading.string, panelWidth - 16),
            left + panelWidth / 2, top + 9, 0xF4D481)
        graphics.drawString(font, Component.translatable("gui.ziangts.listings"), left + 10, top + 49, 0x9FB3CB, false)
        graphics.drawString(font, Component.translatable("gui.ziangts.details"), left + listWidth + 18, top + 49, 0x9FB3CB, false)
        if (mouseX in left..(left + panelWidth) && mouseY in (top + 8)..(top + 20)) tooltip = heading
        graphics.drawCenteredString(font, "${page.page}/${page.pages}", left + 8 + listWidth / 2, top + panelHeight - 20, 0xFFFFFF)
        val entry = page.entries.getOrNull(selected)
        if (entry == null) graphics.drawString(font, Component.translatable("gui.ziangts.empty"), left + listWidth + 20, top + 56, 0xFFFFFF)
        else {
            val x = left + listWidth + 20
            val detailRight = left + panelWidth - 12
            val previewSize = 96
            val previewX = detailRight - previewSize
            val previewTop = top + 62
            var y = top + 64
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
            line(Component.translatable("gui.ziangts.alpha", Component.translatable(if (entry.aspects.any { it.equals("alpha", true) }) "gui.yes" else "gui.no")))
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
                    // Cobblemon profile models are anchored near their feet;
                    // move the origin above the mathematical card centre so
                    // both short and tall models occupy the same card.
                    stack.translate((previewX + previewSize / 2).toDouble(), (previewTop + 27).toDouble(), 100.0)
                    pose.currentAspects = entry.aspects.toSet()
                    drawProfilePokemon(ResourceLocation.parse(entry.species), stack, Quaternionf().rotationXYZ(0.1f, 0.5f, 0f),
                        state = pose, partialTicks = partialTick, scale = previewScale(entry.species))
                } finally { stack.popPose() }
            }
        }
        tooltip?.let { graphics.renderTooltip(font, it, mouseX, mouseY) }
    }

    override fun isPauseScreen() = false
}
