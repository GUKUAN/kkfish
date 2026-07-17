package me.kkfish.managers;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;

import me.kkfish.kkfish;
import me.kkfish.fishing.HookMechanic;
import me.kkfish.fishing.WaterType;
import me.kkfish.misc.MessageManager;
import me.kkfish.misc.minigame.MinigameManager;
import me.kkfish.player.PlayerContext;
import me.kkfish.player.PlayerContextStore;
import me.kkfish.player.RuntimeData;
import me.kkfish.utils.ActionBarUtil;
import me.kkfish.utils.ActionBarUtil.MessageType;
import me.kkfish.utils.SchedulerUtil;
import me.kkfish.scheduler.SchedulerTask;

/**
 * 负责咬钩检查调度、咬钩概率计算、咬钩提示展示及鱼逃脱处理。
 * 从 Fish.java 拆分而来。
 */
public class BiteCheckScheduler {

    private final kkfish plugin;
    private final Config config;
    private final MessageManager messageManager;
    private final PlayerContextStore playerContextStore;
    private final MinigameManager minigameManager;
    private final Fish fish;
    private final Random random;

    public BiteCheckScheduler(kkfish plugin, Config config, MessageManager messageManager,
                              PlayerContextStore playerContextStore, MinigameManager minigameManager,
                              Fish fish, Random random) {
        this.plugin = plugin;
        this.config = config;
        this.messageManager = messageManager;
        this.playerContextStore = playerContextStore;
        this.minigameManager = minigameManager;
        this.fish = fish;
        this.random = random;
    }

    private PlayerContext getContext(Player player) {
        if (player == null || playerContextStore == null) return null;
        return playerContextStore.getContext(player.getUniqueId());
    }

    public void scheduleBiteCheck(Player player, double chargePercentage, String baitName) {
        FileConfiguration mainConfig = config.getMainConfig();
        final PlayerContext ctx = getContext(player);

        WaterType waterType = ctx != null ? ctx.getSession().getWaterType() : null;
        int minDelay;
        int maxDelay;

        if (waterType == WaterType.LAVA) {
            minDelay = config.getLavaBiteTimeMin();
            maxDelay = config.getLavaBiteTimeMax();
        } else if (waterType == WaterType.VOID) {
            minDelay = config.getVoidBiteTimeMin();
            maxDelay = config.getVoidBiteTimeMax();
        } else {
            // 水区咬钩时间config存的是ms，/50转成tick统一单位
            minDelay = mainConfig.getInt("fishing-settings.bite-check-delay-min", 5000) / 50;
            maxDelay = mainConfig.getInt("fishing-settings.bite-check-delay-max", 15000) / 50;
        }

        int delay = (int) (minDelay + Math.random() * (maxDelay - minDelay) * (1 - chargePercentage / 200));

        final double finalChargePercentage = chargePercentage;
        final String finalBaitName = baitName;

        Runnable biteTask = () -> {
            if (ctx != null && ctx.getSession().getFishingSession() != null) {
                checkFishBite(player, finalChargePercentage, finalBaitName);
            }
            if (ctx != null) {
                ctx.getRuntime().setBiteCheckTask(null);
            }
        };

        SchedulerTask task = SchedulerUtil.runEntityTaskDelayed(plugin, player, biteTask, delay);
        if (ctx != null) {
            ctx.getRuntime().setBiteCheckTask(task);
        }
    }

    private void checkFishBite(Player player, double chargePercentage, String baitName) {
        double[] probabilities = calculateBiteProbabilities(player, chargePercentage, baitName);
        double biteRate = probabilities[0];
        double rareFishChance = probabilities[1];

        logBiteProbabilities(player, chargePercentage, baitName, biteRate);

        if (!config.isFishEscapeBeforeMinigameEnabled() || random.nextDouble() < biteRate) {
            showBiteHint(player, chargePercentage, baitName, rareFishChance);
        } else {
            handleFishEscape(player);
        }
    }

    private double[] calculateBiteProbabilities(Player player, double chargePercentage, String baitName) {
        double baseBiteChance = config.getMainConfig().getDouble("fishing-settings.base-bite-chance", 0.2);
        double maxBiteChance = config.getMainConfig().getDouble("fishing-settings.max-bite-chance", 1.0);

        double biteRate = baseBiteChance + chargePercentage / 100 * (maxBiteChance - baseBiteChance);

        PlayerContext ctx = getContext(player);
        WaterType waterType = ctx != null ? ctx.getSession().getWaterType() : null;
        if (waterType == WaterType.LAVA) {
            biteRate *= config.getLavaBiteChanceMultiplier();
        } else if (waterType == WaterType.VOID) {
            biteRate *= config.getVoidBiteChanceMultiplier();
        }

        String rodName = minigameManager != null ? minigameManager.getRodNameByPlayer(player) : "wood";

        biteRate += config.getRodBiteRateBonus(rodName);
        double rareFishChance = config.getRodRareFishChance(rodName);

        if (baitName != null) {
            double[] baitEffects = applyBaitEffects(biteRate, rareFishChance, baitName, config);
            biteRate = baitEffects[0];
            rareFishChance = baitEffects[1];
        }

        String hookMaterial = plugin.getDB().getPlayerHookMaterial(player.getUniqueId().toString());
        double hookBiteRateBonus = config.getHookBiteRateBonus(hookMaterial);
        double hookRareFishChanceBonus = config.getHookRareFishChance(hookMaterial);

        biteRate *= (1.0 + hookBiteRateBonus);
        rareFishChance += hookRareFishChanceBonus;

        biteRate = Math.min(biteRate, maxBiteChance);

        return new double[]{biteRate, rareFishChance};
    }

    private double[] applyBaitEffects(double biteRate, double rareFishChance, String baitName, Config config) {
        List<String> effects = config.getBaitEffects(baitName);

        for (String effectType : effects) {
            double value = config.getBaitEffectValueByName(baitName, effectType);

            if (effectType.equals("bite")) {
                biteRate *= (1.0 + value);
            }
        }

        if (effects.size() <= 1 && config.getBaitEffectValue(baitName) > 0) {
            String oldEffect = config.getBaitEffect(baitName);
            double oldValue = config.getBaitEffectValue(baitName);

            if (oldEffect.equals("bite")) {
                biteRate *= (1.0 + oldValue);
            }
        }

        return new double[]{biteRate, rareFishChance};
    }

    private void logBiteProbabilities(Player player, double chargePercentage, String baitName, double biteRate) {
        if (config.isDebugMode()) {
            StringBuilder sb = new StringBuilder();
            sb.append("玩家 ")
                        .append(player.getName())
                        .append(" 的咬钩概率计算: 蓄力=")
                        .append(chargePercentage)
                        .append("%, 调整后=")
                        .append(biteRate)
                        .append(", 鱼饵=")
                        .append(baitName);
            config.debugLog(sb.toString());
        }
    }

    private void handleFishEscape(Player player) {
        ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("fish_escape", "The fish got away..."), 40, MessageType.FISHING);
        plugin.getSoundManager().playFailSound(player.getLocation());

        PlayerContext ctx = getContext(player);
        HookMechanic mechanic = ctx != null ? ctx.getSession().getHookMechanic() : null;
        if (mechanic != null) {
            mechanic.playEscapeEffect(player, player.getLocation());
        }

        fish.endSession(player);
    }

    private void showBiteHint(Player player, double chargePercentage, String baitName, double rareFishChance) {
        UUID playerId = player.getUniqueId();
        PlayerContext ctx = getContext(player);

        int hintTimeoutSeconds = plugin.getCustomConfig().getMainConfig().getInt("fishing-settings.bite-hint-timeout", 2);
        long expireTime = System.currentTimeMillis() + hintTimeoutSeconds * 1000;

        Location hookLocation = player.getLocation();
        ArmorStand hookEntity = ctx != null ? ctx.getSession().getFishingSession() : null;
        if (hookEntity != null && hookEntity.isValid()) {
            hookLocation = hookEntity.getLocation();
        }

        plugin.getSoundManager().playBiteSound(hookLocation);

        HookMechanic mechanic = ctx != null ? ctx.getSession().getHookMechanic() : null;
        if (mechanic != null) {
            mechanic.playBiteEffect(player, hookLocation);
        }

        String hintText = messageManager.getMessageWithoutPrefix("fishing_hint", "!");

        sendFloatingText(player, hookLocation, hintText, 20, 20, 20);

        ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("fish_bite", "A fish is biting! Right-click or sneak to start the minigame!"), hintTimeoutSeconds * 20, MessageType.FISHING);

        BukkitRunnable expireTask = new BukkitRunnable() {
            @Override
            public void run() {
                removeBiteHint(playerId);
            }
        };

        RuntimeData.BiteHintData data = new RuntimeData.BiteHintData(chargePercentage, baitName, rareFishChance, expireTime, expireTask);
        if (ctx != null) {
            ctx.getRuntime().setBiteHintData(data);
        }

        SchedulerUtil.runEntityTaskDelayed(plugin, player, expireTask, hintTimeoutSeconds * 20);
    }

    private void sendFloatingText(Player player, Location location, String text, int fadeInTime, int stayTime, int fadeOutTime) {
        Location spawnLocation = location.clone().add(0, 1.5, 0);
        ArmorStand floatingText = location.getWorld().spawn(spawnLocation, ArmorStand.class);
        floatingText.setVisible(false);
        floatingText.setGravity(false);
        floatingText.setMarker(true);
        floatingText.setCustomNameVisible(true);
        floatingText.setCustomName(text);

        final BukkitRunnable[] fadeInTaskRef = new BukkitRunnable[1];
        final SchedulerTask[] fadeInTaskRefTask = new SchedulerTask[1];

        fadeInTaskRef[0] = new BukkitRunnable() {
            private float opacity = 0.0f;
            private float step = 1.0f / fadeInTime;

            @Override
            public void run() {
                if (opacity < 1.0f) {
                    opacity = Math.min(1.0f, opacity + step);
                    String coloredText = text;
                    if (coloredText.contains("§")) {
                        int lastColorIndex = coloredText.lastIndexOf("§");
                        if (lastColorIndex > -1 && lastColorIndex < coloredText.length() - 1) {
                            String colorCode = coloredText.substring(lastColorIndex, lastColorIndex + 2);
                            String textContent = coloredText.substring(lastColorIndex + 2);
                            String newColorCode = colorCode;
                            switch (colorCode) {
                                case "§c":
                                    newColorCode = opacity > 0.7 ? "§c" : (opacity > 0.4 ? "§6" : "§e");
                                    break;
                                case "§a":
                                    newColorCode = opacity > 0.7 ? "§a" : (opacity > 0.4 ? "§2" : "§6");
                                    break;
                            }
                            floatingText.setCustomName(newColorCode + textContent);
                        }
                    }
                } else {
                    if (fadeInTaskRefTask[0] != null) {
                        fadeInTaskRefTask[0].cancel();
                    }
                }
            }
        };
        fadeInTaskRefTask[0] = SchedulerUtil.runEntityTaskTimer(plugin, player, fadeInTaskRef[0], 0, 1);

        BukkitRunnable fadeOutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (fadeInTaskRefTask[0] != null) {
                    fadeInTaskRefTask[0].cancel();
                }

                final BukkitRunnable[] fadeOutTaskRef = new BukkitRunnable[1];
                final SchedulerTask[] fadeOutTaskRefTask = new SchedulerTask[1];

                fadeOutTaskRef[0] = new BukkitRunnable() {
                    private float opacity = 1.0f;
                    private float step = 1.0f / fadeOutTime;

                    @Override
                    public void run() {
                        if (opacity > 0.0f) {
                            opacity = Math.max(0.0f, opacity - step);
                            String coloredText = text;
                            if (coloredText.contains("§")) {
                                int lastColorIndex = coloredText.lastIndexOf("§");
                                if (lastColorIndex > -1 && lastColorIndex < coloredText.length() - 1) {
                                    String colorCode = coloredText.substring(lastColorIndex, lastColorIndex + 2);
                                    String textContent = coloredText.substring(lastColorIndex + 2);
                                    String newColorCode = colorCode;
                                    switch (colorCode) {
                                        case "§c":
                                            newColorCode = opacity > 0.7 ? "§c" : (opacity > 0.4 ? "§6" : "§e");
                                            break;
                                        case "§a":
                                            newColorCode = opacity > 0.7 ? "§a" : (opacity > 0.4 ? "§2" : "§6");
                                            break;
                                    }
                                    floatingText.setCustomName(newColorCode + textContent);
                                }
                            }
                        } else {
                            floatingText.remove();
                            if (fadeOutTaskRefTask[0] != null) {
                                fadeOutTaskRefTask[0].cancel();
                            }
                        }
                    }
                };
                fadeOutTaskRefTask[0] = SchedulerUtil.runEntityTaskTimer(plugin, player, fadeOutTaskRef[0], 0, 1);
            }
        };
        SchedulerUtil.runEntityTaskDelayed(plugin, player, fadeOutTask, fadeInTime + stayTime);
    }

    private void sendFloatingText(Player player, Location location, String text, int duration, float scale) {
        sendFloatingText(player, location, text, 5, duration - 10, 5);
    }

    public void removeBiteHint(UUID playerId) {
        removeBiteHint(playerId, true);
    }

    public void removeBiteHint(UUID playerId, boolean sendEscapeMessage) {
        if (playerContextStore == null) return;
        PlayerContext ctx = playerContextStore.getContext(playerId);
        if (ctx == null) return;
        RuntimeData.BiteHintData data = ctx.getRuntime().getBiteHintData();
        if (data != null) {
            data.cancelExpireTask();
            ctx.getRuntime().setBiteHintData(null);

            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && sendEscapeMessage) {
                ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("fish_escape", "The fish got away..."), 40, MessageType.FISHING);
            }
            // 咬钩超时也要清理钓鱼会话，否则玩家无法再次钓鱼
            if (fish != null) {
                Player player2 = Bukkit.getPlayer(playerId);
                if (player2 != null) {
                    fish.endSession(player2);
                }
            }
        }
    }

    public boolean triggerMinigame(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerContext ctx = getContext(player);
        if (ctx == null) return false;
        RuntimeData.BiteHintData data = ctx.getRuntime().getBiteHintData();

        if (data != null && !data.isExpired()) {
            String hookedText = messageManager.getMessageWithoutPrefix("fishing_hooked", "Hooked!");

            Location hookLocation = player.getLocation();
            ArmorStand hookEntity = ctx.getSession().getFishingSession();
            if (hookEntity != null && hookEntity.isValid()) {
                hookLocation = hookEntity.getLocation();
            }

            sendFloatingText(player, hookLocation, hookedText, 20, 20, 20);

            removeBiteHint(playerId, false);

            minigameManager.startMinigame(player, data.getChargePercentage(), data.getBaitName(), data.getRareFishChance());

            return true;
        }

        return false;
    }
}
