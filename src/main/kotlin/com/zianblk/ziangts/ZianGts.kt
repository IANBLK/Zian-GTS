package com.zianblk.ziangts

import com.zianblk.ziangts.config.GtsSettings
import com.zianblk.ziangts.server.GtsCommands
import com.zianblk.ziangts.v2.runtime.ZianGtsV2Runtime
import com.zianblk.ziangts.v2.runtime.V2TestCommands
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import org.slf4j.LoggerFactory

@Mod(ZianGts.MOD_ID)
class ZianGts(container: ModContainer, bus: net.neoforged.bus.api.IEventBus) {
    init {
        bus.addListener(com.zianblk.ziangts.network.GtsNetwork::register)
        container.registerConfig(ModConfig.Type.SERVER, GtsSettings.spec)
        NeoForge.EVENT_BUS.addListener { event: RegisterCommandsEvent ->
            GtsCommands.register(event.dispatcher)
            V2TestCommands.register(event.dispatcher)
        }
        NeoForge.EVENT_BUS.addListener(com.zianblk.ziangts.server.GtsJournal::started)
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.zianblk.ziangts.server.GtsJournal::stopping)
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, com.zianblk.ziangts.server.GtsJournal::stopped)
        NeoForge.EVENT_BUS.addListener { event: ServerStartedEvent -> ZianGtsV2Runtime.start(event.server) }
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST) { event: ServerStoppingEvent ->
            ZianGtsV2Runtime.stop(event.server)
        }
        LOGGER.info("Inicializando Zian GTS")
    }

    companion object {
        const val MOD_ID = "ziangts"
        val LOGGER = LoggerFactory.getLogger("Zian GTS")
    }
}
