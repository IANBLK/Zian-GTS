package com.zianblk.ziangts

import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import com.zianblk.ziangts.server.GtsCommands
import org.slf4j.LoggerFactory
import net.neoforged.fml.ModContainer
import net.neoforged.fml.config.ModConfig
import com.zianblk.ziangts.config.GtsSettings

@Mod(ZianGts.MOD_ID)
class ZianGts(container: ModContainer, bus: net.neoforged.bus.api.IEventBus) {
    init {
        bus.addListener(com.zianblk.ziangts.network.GtsNetwork::register)
        container.registerConfig(ModConfig.Type.SERVER, GtsSettings.spec)
        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent -> GtsCommands.register(event.dispatcher) }
        NeoForge.EVENT_BUS.addListener(com.zianblk.ziangts.server.GtsJournal::started)
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.zianblk.ziangts.server.GtsJournal::stopping)
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.zianblk.ziangts.server.GtsJournal::stopped)
        LOGGER.info("Inicializando Zian GTS")
    }

    companion object {
        const val MOD_ID = "ziangts"
        val LOGGER = LoggerFactory.getLogger("Zian GTS")
    }
}
