package me.kkfish.utils;

import me.kkfish.kkfish;
import me.kkfish.scheduler.SchedulerProvider;
import me.kkfish.scheduler.SchedulerTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Delegating wrapper around {@link SchedulerProvider}.
 * All methods forward to the platform-specific provider discovered at load time.
 * The plugin parameter is kept for backward compatibility but is unused internally.
 */
public class SchedulerUtil {

    private static SchedulerProvider provider() {
        return kkfish.getInstance().getScheduler();
    }

    /** @deprecated Use {@link #runSync(Runnable)} — plugin param no longer needed */
    @Deprecated
    public static SchedulerTask runSync(JavaPlugin plugin, Runnable task) {
        provider().runTask(task);
        return new SchedulerTask();
    }

    public static SchedulerTask runSyncDelayed(JavaPlugin plugin, Runnable task, long delayTicks) {
        return provider().runTaskLater(task, Math.max(1, delayTicks));
    }

    public static SchedulerTask runSyncTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return provider().runTaskTimer(task, Math.max(1, delayTicks), periodTicks);
    }

    public static SchedulerTask runAsync(JavaPlugin plugin, Runnable task) {
        provider().runAsync(task);
        return new SchedulerTask();
    }

    public static SchedulerTask runAsyncDelayed(JavaPlugin plugin, Runnable task, long delayTicks) {
        return provider().runAsyncLater(task, Math.max(1, delayTicks));
    }

    public static SchedulerTask runAsyncTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return provider().runAsyncTimer(task, Math.max(1, delayTicks), periodTicks);
    }

    public static <T> CompletableFuture<T> supplyAsync(JavaPlugin plugin, Callable<T> callable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runAsync(plugin, () -> {
            try {
                T result = callable.call();
                runSync(plugin, () -> future.complete(result));
            } catch (Exception e) {
                runSync(plugin, () -> future.completeExceptionally(e));
            }
        });
        return future;
    }

    public static <T> void supplyAsyncThenConsume(JavaPlugin plugin, Callable<T> callable, Consumer<T> consumer) {
        supplyAsync(plugin, callable).thenAcceptAsync(result -> runSync(plugin, () -> consumer.accept(result)));
    }

    // ---- Entity / region-scoped ----

    public static SchedulerTask runEntityTask(JavaPlugin plugin, Player player, Runnable task) {
        return provider().runRegionTask(player, task);
    }

    public static SchedulerTask runEntityTaskDelayed(JavaPlugin plugin, Player player, Runnable task, long delayTicks) {
        return provider().runRegionTaskLater(player, task, Math.max(1, delayTicks));
    }

    public static SchedulerTask runEntityTaskTimer(JavaPlugin plugin, Player player, Runnable task, long delayTicks, long periodTicks) {
        return provider().runRegionTaskTimer(player, task, Math.max(1, delayTicks), periodTicks);
    }

    // ---- Generic schedule ----

    public static SchedulerTask scheduleTask(JavaPlugin plugin, Runnable runnable, long delay, long period) {
        if (period > 0) {
            return runSyncTimer(plugin, runnable, delay, period);
        } else if (delay > 0) {
            return runSyncDelayed(plugin, runnable, delay);
        } else {
            runSync(plugin, runnable);
            return new SchedulerTask();
        }
    }

    public static void cancelTask(SchedulerTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
