package com.zianblk.ziangts.v2.runtime;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import java.util.UUID;

/** Optional NeoForge/Youer integration. Installed but unavailable providers never grant access. */
public final class LuckPermsLookup {
    private LuckPermsLookup() {}

    public enum Decision { ABSENT, TRUE, FALSE, UNDEFINED }

    public static Decision lookup(ServerPlayer player, String node) throws ReflectiveOperationException {
        boolean modInstalled = ModList.get().isLoaded("luckperms");
        Object plugin = null;
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            plugin = Class.forName("org.bukkit.plugin.PluginManager")
                    .getMethod("getPlugin", String.class).invoke(manager, "LuckPerms");
        } catch (ClassNotFoundException ignored) {
            // Pure NeoForge has no Bukkit plugin manager.
        }
        if (!modInstalled && plugin == null) return Decision.ABSENT;
        ClassLoader loader = modInstalled ? LuckPermsLookup.class.getClassLoader() : plugin.getClass().getClassLoader();
        return lookup(player.getUUID(), loader, node);
    }

    static Decision lookup(UUID playerId, ClassLoader loader, String node) throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName("net.luckperms.api.LuckPerms", true, loader);
        Object api = Class.forName("net.luckperms.api.LuckPermsProvider", true, loader)
                .getMethod("get").invoke(null);
        Object users = apiClass.getMethod("getUserManager").invoke(api);
        Object user = Class.forName("net.luckperms.api.model.user.UserManager", true, loader)
                .getMethod("getUser", UUID.class).invoke(users, playerId);
        if (user == null) throw new IllegalStateException("LuckPerms user is unavailable");
        Object cached = Class.forName("net.luckperms.api.model.PermissionHolder", true, loader)
                .getMethod("getCachedData").invoke(user);
        Object permissions = Class.forName("net.luckperms.api.cacheddata.CachedDataManager", true, loader)
                .getMethod("getPermissionData").invoke(cached);
        Object value = Class.forName("net.luckperms.api.cacheddata.CachedPermissionData", true, loader)
                .getMethod("checkPermission", String.class).invoke(permissions, node);
        return Decision.valueOf(value.toString());
    }
}
