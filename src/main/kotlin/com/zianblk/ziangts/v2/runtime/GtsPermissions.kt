package com.zianblk.ziangts.v2.runtime

import com.zianblk.ziangts.ZianGts
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.util.Tristate
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.ModList

/** Server-side gate shared by commands and packets. An unavailable installed provider denies access. */
object GtsPermissions {
    private const val PREFIX = "ziangts."
    private const val DENIED = "No tienes permiso para usar esta función del GTS"

    internal enum class Decision { TRUE, FALSE, UNDEFINED }

    internal fun evaluate(defaultAllowed: Boolean, nodes: List<String>, lookup: (String) -> Decision): Boolean {
        return try {
            for (node in nodes) {
                val result = lookup(PREFIX + node)
                if (result == Decision.FALSE || (result == Decision.UNDEFINED && node != "use" && !defaultAllowed)) {
                    return false
                }
            }
            true
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
    }

    fun allows(player: ServerPlayer, action: String, defaultAllowed: Boolean = true): Boolean {
        if (!ModList.get().isLoaded("luckperms")) return defaultAllowed
        val nodes = if (action == "use") listOf("use") else listOf("use", action)
        return evaluate(defaultAllowed, nodes) { node ->
            val api = LuckPermsProvider.get()
            val user = api.userManager.getUser(player.uuid)
                ?: throw IllegalStateException("LuckPerms user is unavailable")
            when (user.cachedData.permissionData.checkPermission(node)) {
                Tristate.TRUE -> Decision.TRUE
                Tristate.FALSE -> Decision.FALSE
                Tristate.UNDEFINED -> Decision.UNDEFINED
            }
        }
    }

    fun allows(source: CommandSourceStack, action: String, defaultAdmin: Boolean = false): Boolean {
        val player = source.player ?: return source.hasPermission(2)
        return allows(player, action, if (defaultAdmin) source.hasPermission(2) else true)
    }

    fun deny(player: ServerPlayer) {
        player.sendSystemMessage(Component.literal(DENIED))
        ZianGts.LOGGER.info("Denied GTS action for {}", player.scoreboardName)
    }

    fun denialMessage(): String = DENIED
}
