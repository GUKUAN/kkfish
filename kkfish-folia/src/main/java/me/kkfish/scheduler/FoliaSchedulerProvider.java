package me.kkfish.scheduler;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

/**
 * Folia/Foila scheduler implementation using region-aware schedulers.
 * Compiles on Java 17+ with folia-api on the classpath.
 */
public class FoliaSchedulerProvider extends SchedulerProvider {

    public FoliaSchedulerProvider(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public void runTask(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public SchedulerTask runTaskLater(Runnable task, long delayTicks) {
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler()
                .runDelayed(plugin, t -> task.run(), Math.max(1, delayTicks));
        return new SchedulerTask(() -> scheduled.cancel());
    }

    @Override
    public SchedulerTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> task.run(), Math.max(1, delayTicks), periodTicks);
        return new SchedulerTask(() -> scheduled.cancel());
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    @Override
    public SchedulerTask runAsyncLater(Runnable task, long delayTicks) {
        ScheduledTask scheduled = Bukkit.getAsyncScheduler()
                .runDelayed(plugin, t -> task.run(), Math.max(1, delayTicks) * 50L, TimeUnit.MILLISECONDS);
        return new SchedulerTask(() -> scheduled.cancel());
    }

    @Override
    public SchedulerTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        ScheduledTask scheduled = Bukkit.getAsyncScheduler()
                .runAtFixedRate(plugin, t -> task.run(), Math.max(1, delayTicks) * 50L,
                        periodTicks * 50L, TimeUnit.MILLISECONDS);
        return new SchedulerTask(() -> scheduled.cancel());
    }

    @Override
    public SchedulerTask runRegionTask(Player player, Runnable task) {
        player.getScheduler().run(plugin, t -> task.run(), null);
        return new SchedulerTask(); // EntityScheduler.run() returns void
    }

    @Override
    public SchedulerTask runRegionTaskLater(Player player, Runnable task, long delayTicks) {
        ScheduledTask scheduled = player.getScheduler()
                .runDelayed(plugin, t -> task.run(), null, Math.max(1, delayTicks));
        return new SchedulerTask(() -> scheduled.cancel());
    }

    @Override
    public SchedulerTask runRegionTaskTimer(Player player, Runnable task, long delayTicks, long periodTicks) {
        ScheduledTask scheduled = player.getScheduler()
                .runAtFixedRate(plugin, t -> task.run(), null, Math.max(1, delayTicks), periodTicks);
        return new SchedulerTask(() -> scheduled.cancel());
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
