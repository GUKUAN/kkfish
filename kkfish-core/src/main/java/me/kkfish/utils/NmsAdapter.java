package me.kkfish.utils;

import java.lang.reflect.Method;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

/**
 * NMS/CraftBukkit 版本敏感操作隔离层。
 */
public final class NmsAdapter {

    private NmsAdapter() {
    }

    private static Method teleportAsyncMethod;
    private static boolean teleportAsyncInitialized = false;

    public static void teleportEntityAsync(Entity entity, Location location) {
        if (entity == null || location == null) return;
        if (!teleportAsyncInitialized) {
            initializeTeleportAsync();
        }
        if (teleportAsyncMethod != null) {
            try {
                teleportAsyncMethod.invoke(entity, location);
                return;
            } catch (Exception ignored) {
            }
        }
        entity.teleport(location);
    }

    private static void initializeTeleportAsync() {
        try {
            teleportAsyncMethod = Entity.class.getMethod("teleportAsync", Location.class);
        } catch (NoSuchMethodException e) {
            teleportAsyncMethod = null;
        }
        teleportAsyncInitialized = true;
    }
}
