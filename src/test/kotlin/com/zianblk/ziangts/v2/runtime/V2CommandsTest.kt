package com.zianblk.ziangts.v2.runtime

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class V2CommandsTest {
    @Test fun onlyZianGtsRootIsRegisteredWithExistingSubcommands() {
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        V2Commands.register(dispatcher)
        val root = dispatcher.root.getChild("ZianGTS")
        assertNotNull(root)
        assertNull(dispatcher.root.getChild("gtsv2"))
        assertNotNull(root.command)
        for (name in listOf("open", "status", "recovery")) assertNotNull(root.getChild(name))
        assertNotNull(root.getChild("recovery").getChild("resolve").getChild("operation").getChild("confirm"))
    }
}
