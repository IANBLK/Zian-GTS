package com.zianblk.ziangts.server

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import net.neoforged.fml.ModList

/** Explicit denies override OP. An unavailable installed provider fails closed. */
object GtsPermissions {
    internal fun defaultAccess(node: String, operator: Boolean): Boolean =
        !node.startsWith("ziangts.admin.") || operator

    fun allowed(source: CommandSourceStack, node: String): Boolean {
        val fallback = defaultAccess(node, source.hasPermission(2))
        val player = source.entity as? ServerPlayer ?: return fallback
        if (!ModList.get().isLoaded("luckperms")) return fallback
        return try { LuckPermsBridge.allowed(player, node, fallback) }
        catch (error: Exception) { false }
        catch (error: LinkageError) { false }
    }

    fun require(player: ServerPlayer, node: String) {
        val source = player.createCommandSourceStack()
        if (!allowed(source, "ziangts.use") || !allowed(source, node))
            throw GtsException("command.ziangts.no_permission")
    }
}

/** Loaded only when LuckPerms is installed; its API is not bundled in the mod. */
private object LuckPermsBridge {
    fun allowed(player: ServerPlayer, node: String, fallback: Boolean): Boolean {
        val user = net.luckperms.api.LuckPermsProvider.get().userManager.getUser(player.uuid) ?: return false
        return user.cachedData.permissionData.checkPermission(node).asBooleanOrElse(fallback)
    }
}
