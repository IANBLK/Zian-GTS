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
    @JvmStatic
    @SubscribeEvent
    fun setup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            GtsNetwork.clientReceiver = { page ->
                val client = Minecraft.getInstance()
                val screen = client.screen
                if (screen is GtsScreen) screen.update(page)
                else client.setScreen(GtsScreen(page))
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
        graphics.fill(0, 0, width, height, 0xB0101520.toInt())
        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF0182232.toInt())
        graphics.drawString(font, title, left + 8, top + 9, 0xF4D481, false)
        graphics.drawCenteredString(font, "${page.page}/${page.pages}", left + 8 + listWidth / 2, top + panelHeight - 20, 0xFFFFFF)
        val entry = page.entries.getOrNull(selected)
        if (entry == null) graphics.drawString(font, Component.translatable("gui.ziangts.empty"), left + listWidth + 20, top + 56, 0xFFFFFF)
        else {
            val x = left + listWidth + 20
            var y = top + 51
            fun line(component: Component, color: Int = 0xE2E8F0) {
                graphics.drawString(font, font.substrByWidth(component, panelWidth - listWidth - 28), x, y, color, false)
                y += 11
            }
            line(Component.literal(entry.name + if (entry.expired) " ⌛" else ""), 0xF4D481)
            line(Component.translatable("gui.ziangts.seller", entry.seller))
            line(Component.translatable("gui.ziangts.price", "${entry.price} ${entry.currency}"))
            line(Component.translatable("gui.ziangts.level", entry.level))
            line(Component.translatable("gui.ziangts.gender", Component.translatable("gui.ziangts.gender.${entry.gender.lowercase()}")))
            line(Component.translatable("gui.ziangts.shiny", Component.translatable(if (entry.shiny) "gui.yes" else "gui.no")))
            // Only Nature and Ability are omitted from this detail panel.
            line(Component.translatable("gui.ziangts.stats"), 0xF4D481)
            val names = arrayOf("hp", "attack", "defence", "special_attack", "special_defence", "speed")
            names.forEachIndexed { index, stat ->
                line(Component.translatable("cobblemon.stat.$stat.name").append(
                    " ${entry.stats[index]}  IV ${entry.ivs[index]}  EV ${entry.evs[index]}"))
            }
            if (panelWidth >= 500) {
                val stack = graphics.pose()
                stack.pushPose()
                try {
                    stack.translate((left + panelWidth - 42).toDouble(), (top + 116).toDouble(), 100.0)
                    pose.currentAspects = entry.aspects.toSet()
                    drawProfilePokemon(ResourceLocation.parse(entry.species), stack, Quaternionf().rotationXYZ(0.1f, 0.5f, 0f),
                        state = pose, partialTicks = partialTick, scale = 22f)
                } finally { stack.popPose() }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun isPauseScreen() = false
}
