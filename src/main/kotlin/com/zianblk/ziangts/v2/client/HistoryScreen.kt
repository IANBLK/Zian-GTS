package com.zianblk.ziangts.v2.client

import com.zianblk.ziangts.v2.network.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HistoryScreen(private val parent: Screen) : Screen(Component.literal("Historial")) {
    private var observed = -1L
    private var response: HistoryResponsePayload? = null
    private var page = 1
    private var previous: Button? = null
    private var next: Button? = null
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

    override fun init() {
        observed = V2MarketClientState.historyRevision()
        previous = addRenderableWidget(Button.builder(Component.literal("<")) {
            if (page > 1) { page -= 1; request() }
        }.bounds(width / 2 - 65, height - 34, 30, 20).build())
        next = addRenderableWidget(Button.builder(Component.literal(">")) {
            if (response?.hasNext == true) { page += 1; request() }
        }.bounds(width / 2 + 35, height - 34, 30, 20).build())
        addRenderableWidget(Button.builder(Component.literal("Volver")) { minecraft?.setScreen(parent) }
            .bounds(width / 2 - 40, height - 34, 80, 20).build())
        refreshButtons()
        request()
    }

    override fun tick() {
        super.tick()
        val rev = V2MarketClientState.historyRevision()
        if (rev != observed) {
            observed = rev
            response = V2MarketClientState.historySnapshot()
            page = response?.page ?: page
            refreshButtons()
        }
    }

    private fun request() {
        previous?.active = false
        next?.active = false
        V2MarketClient.requestHistory(page, 8)
    }

    private fun refreshButtons() {
        previous?.active = response?.hasPrevious == true
        next?.active = response?.hasNext == true
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF)
        val data = response
        if (data == null) {
            graphics.drawCenteredString(font, Component.literal("Cargando historial..."), width / 2, 48, 0xAAAAAA)
            return
        }
        graphics.drawCenteredString(font, Component.literal("Página ${data.page}/${data.totalPages}"), width / 2, 38, 0xBBBBBB)
        if (data.entries.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("Todavía no tienes transacciones completadas"), width / 2, 76, 0xAAAAAA)
            return
        }
        val viewer = Minecraft.getInstance().player?.uuid
        data.entries.forEachIndexed { index, entry ->
            val y = 62 + index * 25
            val sold = viewer == entry.sellerId
            val kind = if (sold) "Venta" else "Compra"
            val species = entry.species.substringAfter(':').replaceFirstChar(Char::uppercase)
            val line = "$kind · $species · ${entry.amount} ${friendly(entry.currency)}"
            graphics.drawString(font, Component.literal(line), width / 2 - 205, y, if (sold) 0xFFF4D481.toInt() else 0xFFE2E8F0.toInt(), false)
            graphics.drawString(font, Component.literal(formatter.format(Instant.ofEpochMilli(entry.completedAtEpochMilli))), width / 2 + 95, y, 0xFF999999.toInt(), false)
        }
    }

    private fun friendly(value: String): String {
        val path = value.substringAfter(':')
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
        return known[path.lowercase()] ?: path.replace('_', ' ').replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }
}
