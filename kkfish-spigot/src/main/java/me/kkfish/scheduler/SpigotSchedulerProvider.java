package me.kkfish.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Spigot/Paper scheduler implementation using Bukkit.getScheduler().
 */
public class SpigotSchedulerProvider extends SchedulerProvider {

    public SpigotSchedulerProvider(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public void runTask(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public SchedulerTask runTaskLater(Runnable task, long delayTicks) {
        try {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, delayTicks));
            return new SchedulerTask(() -> bukkitTask.cancel());
        } catch (Exception e) {
            e.printStackTrace();
            return SchedulerTask.none();
        }
    }

    @Override
    public SchedulerTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        try {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1, delayTicks), periodTicks);
            return new SchedulerTask(() -> bukkitTask.cancel());
        } catch (Exception e) {
            e.printStackTrace();
            return SchedulerTask.none();
        }
    }

    @Override
    public void runAsync(Runnable task) {
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        } catch (Exception e) {
            e.printStackTrace();
            // fallback to sync
            runTask(task);
        }
    }

    @Override
    public SchedulerTask runAsyncLater(Runnable task, long delayTicks) {
        try {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, Math.max(1, delayTicks));
            return new SchedulerTask(() -> bukkitTask.cancel());
        } catch (Exception e) {
            e.printStackTrace();
            return runTaskLater(task, delayTicks);
        }
    }

    @Override
    public SchedulerTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        try {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, Math.max(1, delayTicks), periodTicks);
            return new SchedulerTask(() -> bukkitTask.cancel());
        } catch (Exception e) {
            e.printStackTrace();
            return runTaskTimer(task, delayTicks, periodTicks);
        }
    }

    @Override
    public SchedulerTask runRegionTask(Player player, Runnable task) {
        // Spigot has no region scheduling — run globally
        runTask(task);
        return new SchedulerTask(); // no-op cancel
    }

    @Override
    public SchedulerTask runRegionTaskLater(Player player, Runnable task, long delayTicks) {
        return runTaskLater(task, delayTicks);
    }

    @Override
    public SchedulerTask runRegionTaskTimer(Player player, Runnable task, long delayTicks, long periodTicks) {
        return runTaskTimer(task, delayTicks, periodTicks);
    }

    @Override
    public void runGlobalTask(Runnable task) {
        runTask(task);
    }

    @Override
    public SchedulerTask runGlobalTaskLater(Runnable task, long delayTicks) {
        return runTaskLater(task, delayTicks);
    }
}
