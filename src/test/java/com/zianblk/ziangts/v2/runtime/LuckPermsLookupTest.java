package com.zianblk.ziangts.v2.runtime;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.cacheddata.CachedDataManager;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.util.Tristate;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LuckPermsLookupTest {
    @Test void reflectiveApiPreservesAllTristatesAndRequestedPlayerAndNode() throws Exception {
        UUID player = UUID.randomUUID();
        String node = "ziangts.admin.recovery";
        for (Tristate state : Tristate.values()) {
            CachedPermissionData permissions = proxy(CachedPermissionData.class, (method, args) -> {
                assertEquals("checkPermission", method); assertEquals(node, args[0]); return state;
            });
            CachedDataManager cached = proxy(CachedDataManager.class, (method, args) -> permissions);
            User user = proxy(User.class, (method, args) -> cached);
            UserManager users = proxy(UserManager.class, (method, args) -> {
                assertEquals("getUser", method); assertEquals(player, args[0]); return user;
            });
            LuckPerms api = proxy(LuckPerms.class, (method, args) -> users);
            installProvider(api);
            try {
                assertEquals(state.name(), LuckPermsLookup.lookup(player, getClass().getClassLoader(), node).name());
            } finally { removeProvider(); }
        }
    }
    @Test void unavailableProviderIsNotTreatedAsAbsent() throws Exception {
        removeProvider();
        assertThrows(ReflectiveOperationException.class, () ->
                LuckPermsLookup.lookup(UUID.randomUUID(), getClass().getClassLoader(), "ziangts.use"));
    }
    private static void installProvider(LuckPerms api) throws Exception {
        var method = LuckPermsProvider.class.getDeclaredMethod("register", LuckPerms.class);
        method.setAccessible(true);
        method.invoke(null, api);
    }
    private static void removeProvider() throws Exception {
        var method = LuckPermsProvider.class.getDeclaredMethod("unregister");
        method.setAccessible(true);
        method.invoke(null);
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> answer.invoke(method.getName(), args));
    }
    private interface Answer { Object invoke(String method, Object[] args); }
}
