package com.zianblk.ziangts

import net.neoforged.fml.common.Mod
import org.slf4j.LoggerFactory

@Mod(ZianGts.MOD_ID)
class ZianGts {
    init {
        LOGGER.info("Inicializando Zian GTS")
    }

    companion object {
        const val MOD_ID = "ziangts"
        val LOGGER = LoggerFactory.getLogger("Zian GTS")
    }
}
