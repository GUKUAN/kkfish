package me.kkfish.utils;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.lang.reflect.Method;

/**
 * NMS/CraftBukkit 版本敏感操作隔离层。
 *
 * <p>将反射调用集中到此处，缓存 {@link Method} 句柄，避免在热路径上重复反射查找。
 * 统一 WaterHookMechanic、LavaHookMechanic、VoidHookMechanic、Fish.java 中重复的
 * teleportAsync 反射代码。</p>
 */
public final class NmsAdapter {

    private NmsAdapter() {
    }

    /** 缓存的 teleportAsync 方法（可能为 null，表示当前版本不支持）。 */
    private static Method teleportAsyncMethod;
    private static boolean teleportAsyncInitialized = false;

    /**
     * 尝试异步传送实体。优先使用 {@code teleportAsync}（Paper 1.9+），
     * 不可用时回退到同步 {@code teleport}。
     *
     * <p>方法句柄在首次调用时缓存，后续调用直接复用，避免热路径反射开销。</p>
     *
     * @param entity   要传送的实体
     * @param location 目标位置
     */
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
                // teleportAsync 调用失败，回退到同步传送
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
