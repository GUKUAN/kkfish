package me.kkfish.misc.minigame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.entity.Player;

import me.kkfish.kkfish;
import me.kkfish.managers.Config;
import me.kkfish.fishing.WaterType;

/**
 * 小游戏鱼类选择器：负责根据水域、稀有度加成、鱼饵效果选择目标鱼和等级。
 * 从 GameSession 抽取，职责单一。
 */
public class FishSelector {

    private final Config config;
    private final Random random;
    private final Player player;
    private final String baitName;
    private final WaterType waterType;

    // 鱼饵加成（由 applyBaitEffects 计算）
    private double rareFishBonus = 1.0;
    private double sizeBonus = 1.0;
    private double biteRateBonus = 1.0;

    public FishSelector(kkfish plugin, Player player, WaterType waterType, String baitName, double rareFishChance, Random random) {
        this.config = plugin.getCustomConfig();
        this.player = player;
        this.waterType = waterType;
        this.baitName = baitName;
        this.random = random;

        applyBaitEffects();
        this.rareFishBonus *= (1.0 + rareFishChance);
    }

    /**
     * 应用鱼饵效果到加成系数。
     */
    private void applyBaitEffects() {
        if (baitName == null) {
            return;
        }

        List<String> effects = config.getBaitEffects(baitName);

        for (String effectType : effects) {
            double value = config.getBaitEffectValueByName(baitName, effectType);

            if (effectType.equals("rare")) {
                rareFishBonus = 1.0 + value;
            } else if (effectType.equals("size")) {
                sizeBonus = 1.0 + value;
            } else if (effectType.equals("bite")) {
                biteRateBonus = 1.0 + value;
            }
        }

    }

    /**
     * 根据水域鱼池和稀有度权重随机选择目标鱼。
     */
    public String selectRandomFish() {
        return selectRandomFish(config.getAvailableFishFromPool(waterType, player == null ? null : player.getLocation()));
    }

    public String selectRandomFish(List<String> fishList) {
        if (fishList.isEmpty()) {
            return null;
        }

        LinkedHashMap<String, Double> fishWeights = new LinkedHashMap<>();
        double totalWeight = 0;

        for (String fish : fishList) {
            if (!config.hasAvailableFishLevel(fish)) continue;
            int rarity = config.getFishRarity(fish);

            double weight = 1.0;
            switch (rarity) {
                case 1: weight = 10.0; break;
                case 2: weight = 5.0; break;
                case 3: weight = 2.0; break;
                case 4: weight = 1.0; break;
                case 5: weight = 0.5; break;
            }

            if (rareFishBonus > 1.0 && rarity >= 3) {
                weight *= rareFishBonus;
            }

            fishWeights.put(fish, weight);
            totalWeight += weight;
        }

        if (totalWeight <= 0 || fishWeights.isEmpty()) return null;
        double randomValue = random.nextDouble() * totalWeight;
        double currentWeight = 0;

        for (Map.Entry<String, Double> entry : fishWeights.entrySet()) {
            currentWeight += entry.getValue();
            if (randomValue < currentWeight) {
                return entry.getKey();
            }
        }

        return fishWeights.keySet().iterator().next();
    }

    /**
     * 稀有加成只改变等级表内的权重，不生成表外等级。
     */
    public String selectRandomFishLevel(String fishName) {
        return config.getRandomFishLevel(fishName, player, rareFishBonus, random);
    }

    public double getRareFishBonus() {
        return rareFishBonus;
    }

    public double getSizeBonus() {
        return sizeBonus;
    }

    public double getBiteRateBonus() {
        return biteRateBonus;
    }
}
