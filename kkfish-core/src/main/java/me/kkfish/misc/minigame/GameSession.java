package me.kkfish.misc.minigame;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import me.kkfish.kkfish;
import me.kkfish.managers.Config;
import me.kkfish.fishing.WaterType;
import me.kkfish.scheduler.SchedulerTask;
import me.kkfish.utils.SchedulerUtil;

/**
 * 小游戏会话：协调器角色，持有玩家、钩位置、水域等上下文，
 * 委托 {@link FishSelector}、{@link FishMovementSimulator}、{@link MinigameRenderer}、{@link FishValueCalculator}
 * 完成具体工作。
 *
 * <p>本类只负责：
 * <ul>
 *   <li>构造各子组件并传递上下文</li>
 *   <li>主循环 run()：更新绿条 → 更新鱼移动 → 更新进度 → 渲染 → 判定胜负</li>
 *   <li>生命周期：start/cancel/isCancelled</li>
 *   <li>对外暴露结果数据（targetFish/fishSize/fishLevel/value/item）</li>
 * </ul></p>
 */
public class GameSession extends BukkitRunnable {

    public final Player player;
    public final Location hookLocation;
    public final WaterType waterType;
    public final String targetFish;
    public final double fishSize;
    public final String fishLevel;

    private double difficulty;
    private SchedulerTask task;
    private final Random random = new Random();
    private final String cachedRodName;

    // 绿条状态（由本类直接管理）
    private double greenBarPos = 0.5;
    private double greenBarVel = 0;
    private double greenBarWidth = 0.3;

    // 进度
    private double progress;
    private int invincibleTicks;
    public boolean isSuccess = false;

    private final kkfish plugin;
    private final Config config;

    // 委托组件
    private final FishSelector fishSelector;
    private final FishMovementSimulator movementSimulator;
    private final MinigameRenderer renderer;
    private final FishValueCalculator valueCalculator;

    public GameSession(kkfish plugin, Player player, Location hookLocation, WaterType waterType,
                       double chargePercentage, String rodName, String baitName, double rareFishChance) {
        this.plugin = plugin;
        this.config = plugin.getCustomConfig();
        this.player = player;
        this.hookLocation = hookLocation;
        this.waterType = waterType;
        this.difficulty = 1.0 - (chargePercentage / 100.0 * 0.3);
        this.cachedRodName = rodName;

        this.invincibleTicks = 60;

        // 1. 鱼选择器（应用鱼饵效果 + rareFishChance）
        this.fishSelector = new FishSelector(plugin, player, waterType, baitName, rareFishChance, random);

        // 2. 难度调整：鱼竿 + 水域
        double rodDifficulty = config.getRodDifficulty(rodName);
        this.difficulty *= rodDifficulty;

        if (waterType == WaterType.LAVA) {
            this.difficulty *= config.getLavaDifficultyMultiplier();
        } else if (waterType == WaterType.VOID) {
            this.difficulty *= config.getVoidDifficultyMultiplier();
        }

        // 3. 选择目标鱼
        this.targetFish = fishSelector.selectRandomFish();

        // 4. 读取鱼的尺寸/振幅配置
        org.bukkit.configuration.file.FileConfiguration fishConfig = config.getFishConfig();
        double fishSizeMin = fishConfig.getDouble("fish." + targetFish + ".min-size", 1.0);
        double fishSizeMax = fishConfig.getDouble("fish." + targetFish + ".max-size", 3.0);
        double movementAmplitude = fishConfig.getDouble("fish." + targetFish + ".movement-amplitude", 1.0);

        // 5. 计算最终尺寸（应用 sizeBonus）
        double sizeBonus = fishSelector.getSizeBonus();
        double baseSize = fishSizeMin + random.nextDouble() * (fishSizeMax - fishSizeMin);
        this.fishSize = Math.min(baseSize * sizeBonus, fishSizeMax);

        // 6. 选择等级
        this.fishLevel = fishSelector.selectRandomFishLevel(targetFish);

        // 7. 初始化鱼移动模拟器
        double initialFishPos = Math.max(0.05, Math.min(0.95, greenBarPos + (random.nextDouble() - 0.5) * 0.1));
        this.movementSimulator = new FishMovementSimulator(plugin, targetFish, movementAmplitude, random, initialFishPos);

        // 8. 初始化渲染器
        this.renderer = new MinigameRenderer(plugin, player, waterType);

        // 9. 初始化价值计算器
        this.valueCalculator = new FishValueCalculator(plugin, player, targetFish, fishSize, fishSizeMax, random);

        // 10. 初始进度
        int initialProgress = config.getMainConfig().getInt("fishing-settings.initial-progress", 10);
        this.progress = initialProgress / 100.0;
    }

    public void start() {
        this.task = SchedulerUtil.runEntityTaskTimer(plugin, player, this, 0, 1);
    }

    public void onPlayerInteraction() {
        greenBarVel += 0.035;
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            if (task != null) {
                task.cancel();
            }
            endGame(false);
            return;
        }
        try {
            updateGreenBar();
            movementSimulator.update(greenBarPos, greenBarWidth);
            updateProgress();
            renderer.render(greenBarPos, greenBarWidth, movementSimulator.getFishPos(), progress);

            if (progress <= 0) {
                if (task != null) {
                    task.cancel();
                }
                endGame(false);
            } else if (progress >= 1) {
                if (task != null) {
                    task.cancel();
                }
                endGame(true);
            }
        } catch (Exception e) {
            if (task != null) {
                task.cancel();
            }
            endGame(false);
        }
    }

    /**
     * 更新绿条位置（重力 + 摩擦 + 边界反弹）。
     */
    private void updateGreenBar() {
        double gravity = 0.007;
        greenBarVel -= gravity;

        double maxSpeed = 0.05;
        if (Math.abs(greenBarVel) > maxSpeed) {
            greenBarVel = Math.signum(greenBarVel) * maxSpeed;
        }

        double frictionFactor;
        if (greenBarPos < 0.3) {
            frictionFactor = 0.83;
        } else if (greenBarPos < 0.7) {
            frictionFactor = 0.88;
        } else {
            frictionFactor = 0.93;
        }

        greenBarVel *= frictionFactor;

        if (Math.abs(greenBarVel) < 0.001) {
            greenBarVel = 0;
        }

        greenBarPos += greenBarVel;

        if (greenBarPos < 0) {
            greenBarPos = 0;
            greenBarVel = Math.abs(greenBarVel) * 0.3;
        } else if (greenBarPos > 1) {
            greenBarPos = 1;
            greenBarVel = -Math.abs(greenBarVel) * 0.3;
        }
    }

    /**
     * 更新进度条（鱼在绿条内增加，否则减少；无敌期间不衰减）。
     */
    private void updateProgress() {
        double baseWidth = 0.15;
        int floatAreaSize = config.getRodFloatAreaSize(cachedRodName);
        greenBarWidth = baseWidth + (floatAreaSize - 3) * 0.03;
        greenBarWidth = Math.max(0.08, Math.min(0.4, greenBarWidth));

        boolean enableRarityImpact = config.getMainConfig().getBoolean("fishing-settings.progress-bar.rarity-impact.enabled", true);
        double raritySlowdownFactor = 1.0;

        if (enableRarityImpact) {
            int fishRarity = config.getFishRarity(targetFish);
            double slowdownPerLevel = config.getMainConfig().getDouble("fishing-settings.progress-bar.rarity-impact.slowdown-per-rarity-level", 0.15);
            double minSpeedRatio = config.getMainConfig().getDouble("fishing-settings.progress-bar.rarity-impact.min-increase-speed-ratio", 0.45);

            raritySlowdownFactor = 1.0 - (fishRarity - 1) * slowdownPerLevel;
            raritySlowdownFactor = Math.max(minSpeedRatio, raritySlowdownFactor);
        }

        boolean isFishInGreenBar = Math.abs(greenBarPos - movementSimulator.getFishPos()) < greenBarWidth / 2;

        if (invincibleTicks > 0) {
            invincibleTicks--;
            if (isFishInGreenBar) {
                double increaseSpeed = config.getMainConfig().getDouble("fishing-settings.progress-bar.increase-speed", 0.015);
                progress += increaseSpeed * (1.0 / difficulty) * raritySlowdownFactor;
                if (progress > 1) progress = 1;
            } else {
                double decreaseSpeed = config.getMainConfig().getDouble("fishing-settings.progress-bar.decrease-speed", 0.01);
                progress -= decreaseSpeed * difficulty;
                if (progress < 0) progress = 0;
            }
            return;
        }

        if (isFishInGreenBar) {
            double increaseSpeed = config.getMainConfig().getDouble("fishing-settings.progress-bar.increase-speed", 0.015);
            progress += increaseSpeed * (1.0 / difficulty) * raritySlowdownFactor;
            if (progress > 1) progress = 1;
        } else {
            double decreaseSpeed = config.getMainConfig().getDouble("fishing-settings.progress-bar.decrease-speed", 0.01);
            progress -= decreaseSpeed * difficulty;
            if (progress < 0) progress = 0;
        }
    }

    private void endGame(boolean success) {
        this.isSuccess = success;
        MinigameManager minigameManager = plugin.getMinigameManager();
        minigameManager.endGame(player);
    }

    /**
     * 计算并返回最终鱼价值（带缓存）。
     */
    public double getActualFishValue() {
        return valueCalculator.calculate(fishLevel);
    }

    public ItemStack createFishItem() {
        return plugin.getFish().createFishItem(targetFish, false, this.player, this.fishSize, this.fishLevel, valueCalculator.getCachedValue());
    }

    public boolean isCancelled() {
        return task == null || task.isCancelled();
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
