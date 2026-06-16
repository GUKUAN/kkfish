package me.kkfish.misc.minigame;

import java.util.Random;

import org.bukkit.entity.Player;

import me.kkfish.kkfish;
import me.kkfish.managers.Config;

/**
 * 小游戏鱼价值计算器：根据基础价值、尺寸、稀有度、鱼钩类型、季节波动计算最终价值。
 * 从 GameSession 抽取，职责单一。
 */
public class FishValueCalculator {

    private final kkfish plugin;
    private final Config config;
    private final Player player;
    private final String targetFish;
    private final double fishSize;
    private final double fishSizeMax;
    private final Random random;

    private Double cachedValue = null;

    public FishValueCalculator(kkfish plugin, Player player, String targetFish, double fishSize, double fishSizeMax, Random random) {
        this.plugin = plugin;
        this.config = plugin.getCustomConfig();
        this.player = player;
        this.targetFish = targetFish;
        this.fishSize = fishSize;
        this.fishSizeMax = fishSizeMax;
        this.random = random;
    }

    /**
     * 计算最终鱼价值（带缓存）。
     */
    public double calculate() {
        if (cachedValue != null) {
            return cachedValue;
        }

        double baseValue = config.getFishConfig().getDouble("fish." + targetFish + ".value", 10.0);

        double maxSize = fishSizeMax > 0 ? fishSizeMax : 60.0;
        double sizeMultiplier = fishSize / maxSize;

        double rarityMultiplier = 1.0;
        String rarityName = config.getRarityNameByLevel(null);
        // 注意：等级到稀有度名称的解析需要外部传入，这里通过 fishLevel 间接获取
        // 但本计算器构造时未持有 fishLevel，需要通过 calculate(fishLevel) 重载
        rarityMultiplier = config.getRarityValueMultiplier(rarityName);

        double valueBonus = 1.0;
        if (player != null) {
            String hookMaterial = plugin.getDB().getPlayerHookMaterial(player.getUniqueId().toString());
            String hookType = hookMaterial != null ? hookMaterial.toLowerCase() : "wood";
            switch (hookType) {
                case "iron": valueBonus = 1.1; break;
                case "gold": valueBonus = 1.2; break;
                case "diamond": valueBonus = 1.3; break;
            }
        }

        double finalValue = baseValue * sizeMultiplier * rarityMultiplier * valueBonus;

        if (plugin.isRealisticSeasonsEnabled() && config.isSeasonalPriceFluctuationEnabled()) {
            String currentSeason = plugin.getCurrentSeason();
            if (currentSeason != null) {
                double seasonalMultiplier = config.getSeasonalPriceMultiplier(currentSeason);
                finalValue *= seasonalMultiplier;

                double baseFluctuation = config.getBasePriceFluctuation();
                double randomFluctuation = 1.0 + (random.nextDouble() - 0.5) * 2 * baseFluctuation;
                finalValue *= randomFluctuation;
            }
        }

        cachedValue = Math.max(1, (double) Math.round(finalValue));
        return cachedValue;
    }

    /**
     * 计算最终鱼价值（带等级参数，用于稀有度乘数）。
     */
    public double calculate(String fishLevel) {
        if (cachedValue != null) {
            return cachedValue;
        }

        double baseValue = config.getFishConfig().getDouble("fish." + targetFish + ".value", 10.0);

        double maxSize = fishSizeMax > 0 ? fishSizeMax : 60.0;
        double sizeMultiplier = fishSize / maxSize;

        double rarityMultiplier = 1.0;
        String rarityName = config.getRarityNameByLevel(fishLevel);
        rarityMultiplier = config.getRarityValueMultiplier(rarityName);

        double valueBonus = 1.0;
        if (player != null) {
            String hookMaterial = plugin.getDB().getPlayerHookMaterial(player.getUniqueId().toString());
            String hookType = hookMaterial != null ? hookMaterial.toLowerCase() : "wood";
            switch (hookType) {
                case "iron": valueBonus = 1.1; break;
                case "gold": valueBonus = 1.2; break;
                case "diamond": valueBonus = 1.3; break;
            }
        }

        double finalValue = baseValue * sizeMultiplier * rarityMultiplier * valueBonus;

        if (plugin.isRealisticSeasonsEnabled() && config.isSeasonalPriceFluctuationEnabled()) {
            String currentSeason = plugin.getCurrentSeason();
            if (currentSeason != null) {
                double seasonalMultiplier = config.getSeasonalPriceMultiplier(currentSeason);
                finalValue *= seasonalMultiplier;

                double baseFluctuation = config.getBasePriceFluctuation();
                double randomFluctuation = 1.0 + (random.nextDouble() - 0.5) * 2 * baseFluctuation;
                finalValue *= randomFluctuation;
            }
        }

        cachedValue = Math.max(1, (double) Math.round(finalValue));
        return cachedValue;
    }

    public double getCachedValue() {
        return cachedValue != null ? cachedValue : 0;
    }
}
