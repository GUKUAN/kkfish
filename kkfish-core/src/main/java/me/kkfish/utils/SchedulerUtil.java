package me.kkfish.utils;

import com.cjcrafter.foliascheduler.ServerImplementation;
import com.cjcrafter.foliascheduler.TaskImplementation;
import me.kkfish.kkfish;
import me.kkfish.scheduler.SchedulerTask;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * 调度器工具 — 包装 foliascheduler，统一 Spigot/Paper/Folia 兼容。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SchedulerUtil {

    private static ServerImplementation scheduler() {
        return kkfish.getInstance().getFoliaScheduler();
    }

    public static void runSync(JavaPlugin plugin, Runnable task) {
        scheduler().global().run((java.util.function.Consumer) t -> task.run());
    }

    public static SchedulerTask runSyncDelayed(JavaPlugin plugin, Runnable task, long delayTicks) {
        TaskImplementation t = scheduler().global().runDelayed(ct -> { task.run(); return null; }, Math.max(1, delayTicks));
        return new SchedulerTask(() -> t.cancel());
    }

    public static SchedulerTask runSyncTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        TaskImplementation t = scheduler().global().runAtFixedRate(ct -> { task.run(); return null; }, Math.max(1, delayTicks), periodTicks);
        return new SchedulerTask(() -> t.cancel());
    }

    public static void runAsync(JavaPlugin plugin, Runnable task) {
        scheduler().async().runNow((java.util.function.Consumer) t -> task.run());
    }

    public static SchedulerTask runAsyncDelayed(JavaPlugin plugin, Runnable task, long delayTicks) {
        TaskImplementation t = scheduler().async().runDelayed(ct -> { task.run(); return null; }, Math.max(1, delayTicks));
        return new SchedulerTask(() -> t.cancel());
    }

    public static SchedulerTask runAsyncTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        TaskImplementation t = scheduler().async().runAtFixedRate(ct -> { task.run(); return null; }, Math.max(1, delayTicks), periodTicks);
        return new SchedulerTask(() -> t.cancel());
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

    public static <T> void supplyAsyncThenConsume(JavaPlugin plugin, Callable<T> callable, java.util.function.Consumer<T> consumer) {
        supplyAsync(plugin, callable).thenAcceptAsync(result -> runSync(plugin, () -> consumer.accept(result)));
    }

    public static void runEntityTask(JavaPlugin plugin, Player player, Runnable task) {
        scheduler().entity(player).run((java.util.function.Consumer) t -> task.run());
    }

    public static SchedulerTask runEntityTaskDelayed(JavaPlugin plugin, Player player, Runnable task, long delayTicks) {
        TaskImplementation t = scheduler().entity(player).runDelayed(ct -> { task.run(); return null; }, Math.max(1, delayTicks));
        return new SchedulerTask(() -> t.cancel());
    }

    public static SchedulerTask runEntityTaskTimer(JavaPlugin plugin, Player player, Runnable task, long delayTicks, long periodTicks) {
        TaskImplementation t = scheduler().entity(player).runAtFixedRate(ct -> { task.run(); return null; }, Math.max(1, delayTicks), periodTicks);
        return new SchedulerTask(() -> t.cancel());
    }

    public static SchedulerTask scheduleTask(JavaPlugin plugin, Runnable runnable, long delay, long period) {
        if (period > 0) return runSyncTimer(plugin, runnable, delay, period);
        if (delay > 0) return runSyncDelayed(plugin, runnable, delay);
        runSync(plugin, runnable);
        return new SchedulerTask();
    }

    public static void cancelTask(SchedulerTask task) {
        if (task != null) task.cancel();
    }
}
