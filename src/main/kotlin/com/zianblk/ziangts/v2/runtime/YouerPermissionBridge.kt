package com.zianblk.ziangts.v2.runtime

import com.zianblk.ziangts.ZianGts

/**
 * Youer/Paper wraps Brigadier commands with Bukkit's VanillaCommandWrapper.
 * That wrapper assigns the root command the permission `minecraft.command.gtsv2`,
 * which is OP-only by default when the permission is not registered.
 *
 * On a hybrid Bukkit server we register only that root permission with default TRUE
 * so ordinary players can execute /gtsv2 and /gtsv2 open. Administrative children
 * remain protected by their Brigadier permission-level checks.
 *
 * Everything is reflective so pure NeoForge installations do not gain a hard Bukkit
 * dependency and simply skip this bridge.
 */
object YouerPermissionBridge {
    private const val ROOT_PERMISSION = "minecraft.command.gtsv2"

    fun registerPlayerAccess() {
        try {
            val bukkitClass = Class.forName("org.bukkit.Bukkit")
            val pluginManager = bukkitClass.getMethod("getPluginManager").invoke(null) ?: return
            val pluginManagerClass = Class.forName("org.bukkit.plugin.PluginManager")
            val existing = pluginManagerClass.getMethod("getPermission", String::class.java)
                .invoke(pluginManager, ROOT_PERMISSION)

            if (existing != null) {
                ZianGts.LOGGER.info("Youer/Bukkit permission {} already exists; leaving its configured default unchanged", ROOT_PERMISSION)
                return
            }

            val permissionClass = Class.forName("org.bukkit.permissions.Permission")
            val permissionDefaultClass = Class.forName("org.bukkit.permissions.PermissionDefault")
            val defaultTrue = permissionDefaultClass.getField("TRUE").get(null)
            val permission = permissionClass
                .getConstructor(String::class.java, String::class.java, permissionDefaultClass)
                .newInstance(
                    ROOT_PERMISSION,
                    "Allows players to open the Zian GTS market",
                    defaultTrue
                )

            pluginManagerClass.getMethod("addPermission", permissionClass).invoke(pluginManager, permission)
            ZianGts.LOGGER.info("Registered Youer/Bukkit permission {} with default TRUE", ROOT_PERMISSION)
        } catch (_: ClassNotFoundException) {
            // Pure NeoForge: Bukkit is not present, so no bridge is necessary.
        } catch (error: Throwable) {
            ZianGts.LOGGER.warn("Could not register Youer/Bukkit permission bridge for {}", ROOT_PERMISSION, error)
        }
    }
}
