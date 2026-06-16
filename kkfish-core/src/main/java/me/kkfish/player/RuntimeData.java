package me.kkfish.player;

import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.scheduler.BukkitRunnable;

import me.kkfish.scheduler.SchedulerTask;

/**
 * 玩家运行时数据（短命状态/任务引用）。
 *
 * <p>持有冷却时间、临时标记、调度任务引用等运行时状态。
 * 这些数据不需要持久化，在上下文销毁时直接清理。</p>
 *
 * <h3>迁移来源</h3>
 * <ul>
 *   <li>{@code Fish.playerCooldowns} → {@link #cooldown}</li>
 *   <li>{@code Fish.playerMessageCooldowns} → {@link #messageCooldown}</li>
 *   <li>{@code kkfish.playerFishingMode} → {@link #vanillaFishingMode}</li>
 *   <li>{@code Fish.fishBitten} → {@link #fishBitten}</li>
 *   <li>{@code Fish.chargeStartTime} → {@link #chargeStartTime}</li>
 *   <li>{@code Fish.activeChargeTasks} → {@link #activeChargeTask}</li>
 *   <li>{@code Fish.activeProgressTasks} → {@link #activeProgressTask}</li>
 *   <li>{@code Fish.biteCheckTasks} → {@link #biteCheckTask}</li>
 *   <li>{@code Fish.activeProgressSchedulers} → {@link #activeProgressScheduler}</li>
 *   <li>{@code Fish.biteCheckSchedulers} → {@link #biteCheckScheduler}</li>
 *   <li>{@code Fish.biteHintDataMap} → {@link #biteHintData}</li>
 *   <li>{@code Fish.floatingEffectTasks} → {@link #floatingEffectTask}</li>
 *   <li>{@code Fish.trajectoryTasks} → {@link #trajectoryTask}</li>
 * </ul>
 */
public class RuntimeData {

    /** 钓鱼冷却到期时间戳（毫秒），0 表示无冷却。 */
    private volatile long cooldown;

    /** 消息冷却到期时间戳（毫秒），0 表示无冷却。 */
    private volatile long messageCooldown;

    /**
     * 是否处于原版钓鱼模式（跳过自定义钓鱼流程）。
     *
     * <p>迁移自 {@code kkfish.playerFishingMode}。</p>
     */
    private volatile boolean vanillaFishingMode;

    /** 鱼是否已上钩。 */
    private volatile boolean fishBitten;

    /** 蓄力开始时间戳（毫秒），0 表示未在蓄力。 */
    private volatile long chargeStartTime;

    /** 活跃蓄力进度任务（ChargeProgressTask extends BukkitRunnable）。 */
    private final AtomicReference<BukkitRunnable> activeChargeTask = new AtomicReference<>();

    /** 活跃蓄力进度更新任务。 */
    private final AtomicReference<BukkitRunnable> activeProgressTask = new AtomicReference<>();

    /** 咬钩检查任务。 */
    private final AtomicReference<BukkitRunnable> biteCheckTask = new AtomicReference<>();

    /** 蓄力进度调度任务。 */
    private final AtomicReference<SchedulerTask> activeProgressScheduler = new AtomicReference<>();

    /** 咬钩检查调度任务。 */
    private final AtomicReference<SchedulerTask> biteCheckScheduler = new AtomicReference<>();

    /** 咬钩提示数据（null 表示无活跃提示）。 */
    private volatile BiteHintData biteHintData;

    /** 浮动效果调度任务。 */
    private final AtomicReference<SchedulerTask> floatingEffectTask = new AtomicReference<>();

    /** 轨迹追踪调度任务。 */
    private final AtomicReference<SchedulerTask> trajectoryTask = new AtomicReference<>();

    public RuntimeData() {
    }

    // ===== 冷却 =====

    public long getCooldown() {
        return cooldown;
    }

    public void setCooldown(long cooldown) {
        this.cooldown = cooldown;
    }

    public boolean isOnCooldown() {
        return cooldown > 0 && System.currentTimeMillis() < cooldown;
    }

    public long getRemainingCooldown() {
        if (cooldown <= 0) return 0;
        long remaining = cooldown - System.currentTimeMillis();
        return remaining > 0 ? remaining : 0;
    }

    public long getMessageCooldown() {
        return messageCooldown;
    }

    public void setMessageCooldown(long messageCooldown) {
        this.messageCooldown = messageCooldown;
    }

    public boolean canSendMessage() {
        return messageCooldown <= 0 || System.currentTimeMillis() >= messageCooldown;
    }

    // ===== 钓鱼模式 =====

    public boolean isVanillaFishingMode() {
        return vanillaFishingMode;
    }

    public void setVanillaFishingMode(boolean vanillaFishingMode) {
        this.vanillaFishingMode = vanillaFishingMode;
    }

    // ===== 蓄力/咬钩状态 =====

    public boolean isFishBitten() {
        return fishBitten;
    }

    public void setFishBitten(boolean fishBitten) {
        this.fishBitten = fishBitten;
    }

    public long getChargeStartTime() {
        return chargeStartTime;
    }

    public void setChargeStartTime(long chargeStartTime) {
        this.chargeStartTime = chargeStartTime;
    }

    public boolean isCharging() {
        return chargeStartTime > 0;
    }

    public long getChargeDuration() {
        if (chargeStartTime <= 0) return 0;
        return System.currentTimeMillis() - chargeStartTime;
    }

    // ===== 任务引用 =====

    public BukkitRunnable getActiveChargeTask() {
        return activeChargeTask.get();
    }

    public void setActiveChargeTask(BukkitRunnable task) {
        activeChargeTask.set(task);
    }

    public BukkitRunnable getActiveProgressTask() {
        return activeProgressTask.get();
    }

    public void setActiveProgressTask(BukkitRunnable task) {
        activeProgressTask.set(task);
    }

    public BukkitRunnable getBiteCheckTask() {
        return biteCheckTask.get();
    }

    public void setBiteCheckTask(BukkitRunnable task) {
        biteCheckTask.set(task);
    }

    public SchedulerTask getActiveProgressScheduler() {
        return activeProgressScheduler.get();
    }

    public void setActiveProgressScheduler(SchedulerTask task) {
        activeProgressScheduler.set(task);
    }

    public SchedulerTask getBiteCheckScheduler() {
        return biteCheckScheduler.get();
    }

    public void setBiteCheckScheduler(SchedulerTask task) {
        biteCheckScheduler.set(task);
    }

    public BiteHintData getBiteHintData() {
        return biteHintData;
    }

    public void setBiteHintData(BiteHintData biteHintData) {
        this.biteHintData = biteHintData;
    }

    public SchedulerTask getFloatingEffectTask() {
        return floatingEffectTask.get();
    }

    public void setFloatingEffectTask(SchedulerTask task) {
        floatingEffectTask.set(task);
    }

    public SchedulerTask getTrajectoryTask() {
        return trajectoryTask.get();
    }

    public void setTrajectoryTask(SchedulerTask task) {
        trajectoryTask.set(task);
    }

    /**
     * 取消所有调度任务并清空引用（用于上下文销毁或会话重置）。
     */
    public void cancelAllTasks() {
        cancelScheduler(activeProgressScheduler);
        cancelScheduler(biteCheckScheduler);
        cancelScheduler(floatingEffectTask);
        cancelScheduler(trajectoryTask);
        cancelRunnable(activeChargeTask);
        cancelRunnable(activeProgressTask);
        cancelRunnable(biteCheckTask);
    }

    /**
     * 清空所有运行时数据（用于上下文销毁）。
     */
    public void clear() {
        cancelAllTasks();
        cooldown = 0;
        messageCooldown = 0;
        vanillaFishingMode = false;
        fishBitten = false;
        chargeStartTime = 0;
        biteHintData = null;
    }

    private void cancelScheduler(AtomicReference<SchedulerTask> ref) {
        SchedulerTask task = ref.getAndSet(null);
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    private void cancelRunnable(AtomicReference<BukkitRunnable> ref) {
        BukkitRunnable task = ref.getAndSet(null);
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 咬钩提示数据（迁移自 {@code Fish.BiteHintData} 内部类）。
     */
    public static class BiteHintData {
        private final double chargePercentage;
        private final String baitName;
        private final double rareFishChance;
        private final long expireTime;
        private final AtomicReference<BukkitRunnable> expireTask = new AtomicReference<>();

        public BiteHintData(double chargePercentage, String baitName, double rareFishChance, long expireTime, BukkitRunnable expireTask) {
            this.chargePercentage = chargePercentage;
            this.baitName = baitName;
            this.rareFishChance = rareFishChance;
            this.expireTime = expireTime;
            this.expireTask.set(expireTask);
        }

        public double getChargePercentage() {
            return chargePercentage;
        }

        public String getBaitName() {
            return baitName;
        }

        public double getRareFishChance() {
            return rareFishChance;
        }

        public long getExpireTime() {
            return expireTime;
        }

        public BukkitRunnable getExpireTask() {
            return expireTask.get();
        }

        public void setExpireTask(BukkitRunnable task) {
            expireTask.set(task);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expireTime;
        }

        public void cancelExpireTask() {
            BukkitRunnable task = expireTask.getAndSet(null);
            if (task != null) {
                try {
                    task.cancel();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
