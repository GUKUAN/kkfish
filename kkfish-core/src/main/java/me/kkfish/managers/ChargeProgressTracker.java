package me.kkfish.managers;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;
import me.kkfish.misc.minigame.MinigameManager;
import me.kkfish.player.PlayerContext;
import me.kkfish.player.PlayerContextStore;
import me.kkfish.player.RuntimeData;
import me.kkfish.utils.ActionBarUtil;
import me.kkfish.utils.SchedulerUtil;
import me.kkfish.scheduler.SchedulerTask;

/**
 * 负责蓄力钓鱼的进度追踪与蓄力条显示。
 * 从 Fish.java 拆分而来。
 */
public class ChargeProgressTracker {

    private final kkfish plugin;
    private final Config config;
    private final MessageManager messageManager;
    private final PlayerContextStore playerContextStore;
    private final MinigameManager minigameManager;
    private final Fish fish;

    public ChargeProgressTracker(kkfish plugin, Config config, MessageManager messageManager,
                                 PlayerContextStore playerContextStore, MinigameManager minigameManager, Fish fish) {
        this.plugin = plugin;
        this.config = config;
        this.messageManager = messageManager;
        this.playerContextStore = playerContextStore;
        this.minigameManager = minigameManager;
        this.fish = fish;
    }

    private PlayerContext getContext(Player player) {
        if (player == null || playerContextStore == null) return null;
        return playerContextStore.getContext(player.getUniqueId());
    }

    public void startCharging(Player player) {
        if (player == null) {
            return;
        }

        PlayerContext ctx = getContext(player);
        if (ctx == null) {
            return;
        }
        RuntimeData runtime = ctx.getRuntime();

        if (fish.isPlayerOnCooldown(player)) {
            long remainingCooldown = fish.getRemainingCooldown(player);

            if (runtime.canSendMessage()) {
                player.sendMessage(messageManager.getMessage("cast_cooldown", "请等待 %.1f 秒后再钓鱼！", (remainingCooldown / 1000.0)));
                runtime.setMessageCooldown(System.currentTimeMillis() + 1000);
            }
            return;
        }

        config.debugLog(messageManager.getMessageWithoutPrefix("debug_fishing_start", "玩家%s尝试开始蓄力钓鱼", player.getName()));

        if (runtime.isCharging()) {
            config.debugLog(messageManager.getMessageWithoutPrefix("debug_fishing_charging", "玩家%s已经在蓄力中，操作被拒绝", player.getName()));
            return;
        }

        if (ctx.getSession().getFishingSession() != null) {
            config.debugLog(messageManager.getMessageWithoutPrefix("debug_fishing_active", "玩家%s已有活跃钓鱼会话，操作被拒绝", player.getName()));
            return;
        }

        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem == null || mainHandItem.getType() == Material.AIR) {
            config.debugLog(messageManager.getMessageWithoutPrefix("debug_fishing_no_item", "玩家%s没有手持物品，操作被拒绝", player.getName()));
            return;
        }

        String rodName = "default_rod";
        if (minigameManager != null) {
            rodName = minigameManager.getRodNameByPlayer(player);
        }

        config.debugLog(messageManager.getMessageWithoutPrefix("debug_fishing_rod_identified", "玩家%s手持物品被识别为鱼竿，允许蓄力", player.getName()));
        config.debugLog(messageManager.getMessageWithoutPrefix("debug_fishing_rod_used", "玩家%s使用鱼竿: %s", player.getName(), rodName));

        runtime.setChargeStartTime(System.currentTimeMillis());
        plugin.getSoundManager().playPrepareSound(player.getLocation());
        startChargeProgressTask(player, rodName);
        config.debugLog(messageManager.getMessageWithoutPrefix("debug_fishing_charge_start", "玩家%s蓄力开始，最大蓄力时间调整为: %f", player.getName(), config.getMainConfig().getInt("fishing-settings.max-charge-time", 3000) / config.getRodChargeSpeed(rodName)));
    }

    private void startChargeProgressTask(Player player, String rodName) {
        int maxChargeTime = config.getMainConfig().getInt("fishing-settings.max-charge-time", 3000);

        double chargeSpeedMultiplier = config.getRodChargeSpeed(rodName);
        if (chargeSpeedMultiplier != 1.0) {
            maxChargeTime = (int) (maxChargeTime / chargeSpeedMultiplier);
        }

        final int adjustedMaxChargeTime = maxChargeTime;
        final UUID playerId = player.getUniqueId();

        PlayerContext ctx = getContext(player);
        if (ctx == null) {
            return;
        }
        final RuntimeData runtime = ctx.getRuntime();

        ChargeProgressTask task = new ChargeProgressTask(player, adjustedMaxChargeTime);

        config.debugLog("创建新的BukkitRunnable来持续执行蓄力进度更新");
        BukkitRunnable progressTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!runtime.isCharging()) {
                    config.debugLog("玩家不再蓄力，取消任务 for player: " + player.getName());
                    // 安全取消：仅当任务已被调度器注册时才调用 cancel()
                    try {
                        if (getTaskId() != -1) {
                            this.cancel();
                        }
                    } catch (IllegalStateException ignored) {
                        // 任务未调度，无需取消
                    }
                    return;
                }

                task.run();
            }
        };

        runtime.setActiveProgressTask(progressTask);
        runtime.setActiveChargeTask(task);

        config.debugLog("添加蓄力进度任务到activeChargeTask: " + playerId);
        config.debugLog("添加进度更新任务到activeProgressTask: " + playerId);

        try {
            runtime.setActiveProgressScheduler(SchedulerUtil.runEntityTaskTimer(plugin, player, progressTask, 0, 1));
            config.debugLog("SchedulerUtil.runEntityTaskTimer调用成功");
        } catch (Exception e) {
            config.debugLog("调度失败: " + e.getMessage());
            progressTask.run();
            runContinuousTask(player, progressTask, 1);
        }
    }

    private void runContinuousTask(Player player, BukkitRunnable task, long delayTicks) {
        try {
            config.debugLog("调度持续执行任务，延迟: " + delayTicks + " ticks");
            task.run();
            BukkitRunnable nextTask = new BukkitRunnable() {
                @Override
                public void run() {
                    runContinuousTask(player, task, delayTicks);
                }
            };
            SchedulerUtil.runEntityTaskDelayed(plugin, player, nextTask, delayTicks);
            config.debugLog("持续任务调度成功");
        } catch (Exception e) {
            config.debugLog("调度持续任务失败: " + e.getMessage());
            try {
                task.run();
            } catch (Exception ex) {
                config.debugLog("直接执行任务失败: " + ex.getMessage());
            }
        }
    }

    public void stopCharging(Player player) {
        stopCharging(player, false);
    }

    public void stopCharging(Player player, boolean isOver100Percent) {
        if (player == null) return;
        PlayerContext ctx = getContext(player);
        if (ctx == null) return;
        RuntimeData runtime = ctx.getRuntime();
        if (!runtime.isCharging()) {
            return;
        }

        long chargeTime = runtime.getChargeDuration();
        int maxChargeTime = config.getMainConfig().getInt("fishing-settings.max-charge-time", 3000);
        double chargePercentage = Math.min(chargeTime * 100.0 / maxChargeTime, 100.0);

        runtime.setChargeStartTime(0);
        runtime.setActiveChargeTask(null);
        SchedulerTask progressScheduler = runtime.getActiveProgressScheduler();
        if (progressScheduler != null) {
            progressScheduler.cancel();
            runtime.setActiveProgressScheduler(null);
            config.debugLog("成功取消进度更新任务 for player: " + player.getName());
        }
        runtime.setActiveProgressTask(null);

        MessageManager messageManager = plugin.getMessageManager();
        if (isOver100Percent) {
            player.sendTitle(messageManager.getMessageWithoutPrefix(player, "title_charge_over", "§dOvercharged!"),
                messageManager.getMessageWithoutPrefix(player, "title_charge_over_subtitle", "§bPower lost!"), 10, 40, 10);
            plugin.getSoundManager().playFastCastSound(player.getLocation());
        } else if (chargePercentage >= 90) {
            player.sendTitle(messageManager.getMessageWithoutPrefix(player, "title_charge_perfect", "§dPerfect Charge!"),
                messageManager.getMessageWithoutPrefix(player, "title_charge_perfect_subtitle", "§bPower doubled!"), 10, 40, 10);
            plugin.getSoundManager().playPerfectCastSound(player.getLocation());
        } else if (chargePercentage >= 60) {
            player.sendTitle(messageManager.getMessageWithoutPrefix(player, "title_charge_good", "§dGood Charge"),
                messageManager.getMessageWithoutPrefix(player, "title_charge_good_subtitle", "§bReady to go"), 10, 40, 10);
            plugin.getSoundManager().playGoodCastSound(player.getLocation());
        } else if (chargePercentage >= 30) {
            player.sendTitle(messageManager.getMessageWithoutPrefix(player, "title_charge_medium", "§dAverage Charge"),
                messageManager.getMessageWithoutPrefix(player, "title_charge_medium_subtitle", "§bCould be better"), 10, 40, 10);
            plugin.getSoundManager().playNormalCastSound(player.getLocation());
        } else {
            player.sendTitle(messageManager.getMessageWithoutPrefix(player, "title_charge_low", "§dInsufficient Charge"),
                messageManager.getMessageWithoutPrefix(player, "title_charge_low_subtitle", "§bHold on a bit longer"), 10, 40, 10);
            plugin.getSoundManager().playFastCastSound(player.getLocation());
        }

        // 通过 Fish 协调器调用抛钩逻辑
        fish.throwFishHook(player, chargePercentage);
    }

    /**
     * 蓄力进度条显示任务。
     */
    class ChargeProgressTask extends BukkitRunnable {
        private final Player player;
        private final int maxChargeTime;

        public ChargeProgressTask(Player player, int maxChargeTime) {
            this.player = player;
            this.maxChargeTime = maxChargeTime;
        }

        @Override
        public void run() {
            plugin.getCustomConfig().debugLog("ChargeProgressTask.run() 被调用 for player: " + player.getName());

            PlayerContext ctx = getContext(player);
            if (ctx == null || !ctx.getRuntime().isCharging()) {
                plugin.getCustomConfig().debugLog("玩家不再蓄力，结束任务 for player: " + player.getName());
                return;
            }

            long chargeTime = ctx.getRuntime().getChargeDuration();
            if (chargeTime >= maxChargeTime) {
                plugin.getCustomConfig().debugLog("蓄力时间超过最大值，停止蓄力 for player: " + player.getName());
                stopCharging(player, true);
                return;
            }

            long chargeTicks = chargeTime / 50;
            if (chargeTicks > 0 && chargeTime / 200 != (chargeTime - 50) / 200) {
                plugin.getSoundManager().playChargeTickSound(player.getLocation());
            }

            double progress = Math.min(chargeTime * 100.0 / maxChargeTime, 100.0);

            plugin.getCustomConfig().debugLog("蓄力进度: " + progress + "% for player: " + player.getName());

            int barLength = 20;
            int filledLength = (int) (barLength * (progress / 100.0));
            StringBuilder barBuilder = new StringBuilder();

            barBuilder.append(ChatColor.GRAY);
            barBuilder.append('[');

            ChatColor fillColor;
            if (progress >= 90) {
                fillColor = ChatColor.GOLD;
            } else if (progress >= 60) {
                fillColor = ChatColor.GREEN;
            } else if (progress >= 30) {
                fillColor = ChatColor.YELLOW;
            } else {
                fillColor = ChatColor.RED;
            }

            barBuilder.append(fillColor);
            for (int i = 0; i < filledLength; i++) {
                barBuilder.append('|');
            }

            barBuilder.append(ChatColor.GRAY);
            for (int i = filledLength; i < barLength; i++) {
                barBuilder.append('|');
            }
            barBuilder.append(ChatColor.GRAY);
            barBuilder.append(']');

            barBuilder.append(ChatColor.WHITE);
            barBuilder.append(' ');
            barBuilder.append((int) progress);
            barBuilder.append('%');

            plugin.getCustomConfig().debugLog("蓄力条显示: " + barBuilder.toString());

            plugin.getCustomConfig().debugLog("尝试发送ActionBar消息 for player: " + player.getName());
            ActionBarUtil.sendActionBar(player, barBuilder.toString());
            plugin.getCustomConfig().debugLog("ActionBar消息发送完成 for player: " + player.getName());
        }
    }
}
