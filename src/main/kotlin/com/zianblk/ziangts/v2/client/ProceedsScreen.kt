package com.zianblk.ziangts.v2.client

import com.zianblk.ziangts.v2.network.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class ProceedsScreen(private val parent: Screen) : Screen(Component.literal("Mis ganancias")) {
    private var observed = -1L
    private var observedClaim = -1L
    private var entries: List<ProceedsEntryDto> = emptyList()
    private var message = "Cargando ganancias..."
    private var pending = false
    private var messageTicks = 0
    private val claimButtons = mutableListOf<Button>()

    override fun init() {
        observed = V2MarketClientState.proceedsRevision()
        observedClaim = V2MarketClientState.claimRevision()
        addRenderableWidget(Button.builder(Component.literal("Volver")) { minecraft?.setScreen(parent) }
            .bounds(width / 2 - 45, height - 34, 90, 20).build())
        V2MarketClient.requestProceeds()
    }

    override fun tick() {
        super.tick()
        val rev = V2MarketClientState.proceedsRevision()
        if (rev != observed) {
            observed = rev
            entries = V2MarketClientState.proceedsSnapshot()?.entries.orEmpty()
            if (messageTicks <= 0) {
                message = if (entries.isEmpty()) "No tienes ganancias pendientes" else ""
            }
            rebuild()
        }
        if (messageTicks > 0) messageTicks -= 1
        val claimRev = V2MarketClientState.claimRevision()
        if (claimRev != observedClaim) {
            observedClaim = claimRev
            V2MarketClientState.claimResultSnapshot()?.let {
                pending = false
                message = it.message
                messageTicks = 100
                V2MarketClient.requestProceeds()
            }
        }
    }

    private fun rebuild() {
        claimButtons.forEach { removeWidget(it) }
        claimButtons.clear()
        entries.take(8).forEachIndexed { index, entry ->
            val y = 72 + index * 30
            val button = addRenderableWidget(Button.builder(Component.literal("Cobrar")) {
                if (!pending) {
                    pending = true
                    message = "Procesando cobro..."
                    claimButtons.forEach { it.active = false }
                    V2MarketClient.claimProceeds(entry.adapter, entry.currency)
                }
            }.bounds(width / 2 + 105, y - 5, 70, 20).build())
            button.active = !pending
            claimButtons += button
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)
        super.render(graphics, mouseX, mouseY, partialTick)
        graphics.drawCenteredString(font, title, width / 2, 24, 0xFFFFFF)
        entries.take(8).forEachIndexed { index, entry ->
            val y = 72 + index * 30
            graphics.drawString(font, Component.literal(friendly(entry.currency)), width / 2 - 170, y, 0xFFF4D481.toInt(), false)
            graphics.drawString(font, Component.literal("Pendiente: ${entry.amount}"), width / 2 - 10, y, 0xFFE2E8F0.toInt(), false)
        }
        if (message.isNotBlank()) graphics.drawCenteredString(font, Component.literal(message), width / 2, 50, 0xCCCCCC)
    }

    private fun friendly(value: String): String {
        val path = value.substringAfter(':')
        val known = mapOf(
            "coppercoin" to "Moneda de Cobre", "ironcoin" to "Moneda de Hierro",
            "goldcoin" to "Moneda de Oro", "diamondcoin" to "Moneda de Diamante",
            "netheritecoin" to "Moneda de Netherita", "goldticket" to "Ticket de Oro",
            "diamondticket" to "Ticket de Diamante", "netheriteticket" to "Ticket de Netherita"
        )
        return known[path] ?: path.replace('_', ' ').replace('-', ' ').split(' ')
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }
}
