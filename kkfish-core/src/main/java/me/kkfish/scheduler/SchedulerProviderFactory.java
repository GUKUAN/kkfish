package me.kkfish.scheduler;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Classpath-based SchedulerProvider discovery.
 *
 * Strategy: each platform module (spigot / folia) ships exactly ONE
 * SchedulerProvider subclass. At load time we try to instantiate
 * the Folia variant first, then fall back to the Spigot variant.
 */
public final class SchedulerProviderFactory {

    private static final String FOLIA_CLASS = "me.kkfish.scheduler.FoliaSchedulerProvider";
    private static final String SPIGOT_CLASS = "me.kkfish.scheduler.SpigotSchedulerProvider";

    private SchedulerProviderFactory() {}

    public static SchedulerProvider create(JavaPlugin plugin) {
        // Try Folia first
        try {
            Class<?> clazz = Class.forName(FOLIA_CLASS);
            return (SchedulerProvider) clazz.getConstructor(JavaPlugin.class).newInstance(plugin);
        } catch (Exception ignored) {
            // Folia provider not on classpath
        }

        // Try Spigot
        try {
            Class<?> clazz = Class.forName(SPIGOT_CLASS);
            return (SchedulerProvider) clazz.getConstructor(JavaPlugin.class).newInstance(plugin);
        } catch (Exception e) {
            throw new RuntimeException("No SchedulerProvider found on classpath — is kkfish-spigot or kkfish-folia module included?", e);
        }
    }
}
