package me.kkfish.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-platform task handle for cancellation.
 * Replaces BukkitTask (Spigot) / ScheduledTask (Folia).
 */
public class SchedulerTask {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final Runnable cancelHook;

    public SchedulerTask() {
        this.cancelHook = null;
    }

    public SchedulerTask(Runnable cancelHook) {
        this.cancelHook = cancelHook;
    }

    public void cancel() {
        if (cancelled.compareAndSet(false, true) && cancelHook != null) {
            cancelHook.run();
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    /** Create a pre-cancelled no-op task */
    public static SchedulerTask none() {
        SchedulerTask t = new SchedulerTask();
        t.cancelled.set(true);
        return t;
    }
}
