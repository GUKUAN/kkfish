package me.kkfish.scheduler;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Abstract scheduler provider — platform-specific implementations
 * handle Spigot (Bukkit.getScheduler()) or Folia (RegionScheduler etc.).
 */
public abstract class SchedulerProvider {

    protected final JavaPlugin plugin;

    public SchedulerProvider(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- Immediate sync ----

    public abstract void runTask(Runnable task);

    // ---- Delayed sync ----

    public abstract SchedulerTask runTaskLater(Runnable task, long delayTicks);

    // ---- Repeating sync ----

    public abstract SchedulerTask runTaskTimer(Runnable task, long delayTicks, long periodTicks);

    // ---- Async ----

    public abstract void runAsync(Runnable task);

    public abstract SchedulerTask runAsyncLater(Runnable task, long delayTicks);

    public abstract SchedulerTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks);

    // ---- Entity / region-scoped (Folia: player.getScheduler()) ----

    public abstract SchedulerTask runRegionTask(Player player, Runnable task);

    public abstract SchedulerTask runRegionTaskLater(Player player, Runnable task, long delayTicks);

    public abstract SchedulerTask runRegionTaskTimer(Player player, Runnable task, long delayTicks, long periodTicks);

    // ---- Global / cross-region ----

    public abstract void runGlobalTask(Runnable task);

    public abstract SchedulerTask runGlobalTaskLater(Runnable task, long delayTicks);
}
