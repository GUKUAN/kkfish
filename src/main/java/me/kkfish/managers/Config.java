package me.kkfish.managers;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.kkfish.fishing.WaterType;
import me.kkfish.kkfish;
import me.kkfish.handlers.AuraSkills;
import me.kkfish.managers.ItemValue;
import me.kkfish.utils.XSeriesUtil;

public class Config {

    private final kkfish plugin;
    private final Logger logger;
    private FileConfiguration mainConfig;
    private FileConfiguration fishConfig;
    private FileConfiguration rodConfig;
    private FileConfiguration baitConfig;
    private FileConfiguration competeConfig;
    private FileConfiguration hookConfig;
    private FileConfiguration soundConfig;
    private FileConfiguration poolConfig;
    private File mainConfigFile;
    private File fishConfigFile;
    private File baitFile;
    private File competeConfigFile;
    private File hookConfigFile;
    private File soundConfigFile;
    private File poolConfigFile;
    private ItemValue itemValue;

    public Config(kkfish plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        initializeConfigs();
    }
    
    public void initializeItemValue() {
        if (itemValue == null) {
            itemValue = new ItemValue(this);
        }
    }
    
    public void initializeItemValueManager() {
        initializeItemValue();
    }

    public ItemValue getItemValue() {
        return itemValue;
    }
    
    public ItemValue getItemValueManager() {
        return getItemValue();
    }

    public boolean isEconomySystemEnabled() {
        return mainConfig.getBoolean("economy.economy", true);
    }

    public boolean isSellEnabled() {
        return mainConfig.getBoolean("economy.sell", true);
    }

    public boolean isSellGuiEnabled() {
        return mainConfig.getBoolean("economy.sellgui", true);
    }

    private void initializeConfigs() {
        plugin.saveDefaultConfig();
        mainConfig = (YamlConfiguration) plugin.getConfig();

        File fishFile = new File(plugin.getDataFolder(), "fish.yml");
        if (!fishFile.exists()) {
            plugin.saveResource("fish.yml", false);
        }
        fishConfig = YamlConfiguration.loadConfiguration(fishFile);



        File rodFile = new File(plugin.getDataFolder(), "rods.yml");
        if (!rodFile.exists()) {
            plugin.saveResource("rods.yml", false);
        }
        rodConfig = YamlConfiguration.loadConfiguration(rodFile);

        baitFile = new File(plugin.getDataFolder(), "baits.yml");
        if (!baitFile.exists()) {
            plugin.saveResource("baits.yml", false);
        }
        baitConfig = YamlConfiguration.loadConfiguration(baitFile);
        
        competeConfigFile = new File(plugin.getDataFolder(), "compete.yml");
        if (!competeConfigFile.exists()) {
            plugin.saveResource("compete.yml", false);
        }
        competeConfig = YamlConfiguration.loadConfiguration(competeConfigFile);
        
        hookConfigFile = new File(plugin.getDataFolder(), "hooks.yml");
        if (!hookConfigFile.exists()) {
            plugin.saveResource("hooks.yml", false);
        }
        hookConfig = YamlConfiguration.loadConfiguration(hookConfigFile);
        
        soundConfigFile = new File(plugin.getDataFolder(), "sounds.yml");
        if (!soundConfigFile.exists()) {
            plugin.saveResource("sounds.yml", false);
        }
        soundConfig = YamlConfiguration.loadConfiguration(soundConfigFile);

        poolConfigFile = new File(plugin.getDataFolder(), "pools.yml");
        if (!poolConfigFile.exists()) {
            plugin.saveResource("pools.yml", false);
        }
        poolConfig = YamlConfiguration.loadConfiguration(poolConfigFile);
    }

    public void saveConfigs() {
        try {
            mainConfig.save(new File(plugin.getDataFolder(), "config.yml"));
            fishConfig.save(new File(plugin.getDataFolder(), "fish.yml"));
    
            rodConfig.save(new File(plugin.getDataFolder(), "rods.yml"));
            baitConfig.save(new File(plugin.getDataFolder(), "baits.yml"));
            competeConfig.save(new File(plugin.getDataFolder(), "compete.yml"));
            hookConfig.save(new File(plugin.getDataFolder(), "hooks.yml"));
            soundConfig.save(new File(plugin.getDataFolder(), "sounds.yml"));
            poolConfig.save(new File(plugin.getDataFolder(), "pools.yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "无法保存配置文件", e);
        }
    }

    public void reloadConfigs() {
        plugin.reloadConfig();
        mainConfig = (YamlConfiguration) plugin.getConfig();
        fishConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "fish.yml"));

        rodConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "rods.yml"));
        hookConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "hooks.yml"));
        baitConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "baits.yml"));
        competeConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "compete.yml"));
        soundConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "sounds.yml"));
        poolConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "pools.yml"));
    }

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public FileConfiguration getFishConfig() {
        return fishConfig;
    }

    public String getRodType(ItemStack item) {
        if (item == null || !item.getType().equals(Material.FISHING_ROD)) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        
        String displayName = meta.getDisplayName();
        
        for (String rodName : rodConfig.getConfigurationSection("rods").getKeys(false)) {
            String configDisplayName = rodConfig.getString("rods." + rodName + ".display-name", rodName);
            configDisplayName = ChatColor.translateAlternateColorCodes('&', configDisplayName);
            
            if (displayName != null && displayName.equals(configDisplayName)) {
                return rodName;
            }
        }
        
        return null;
    }

    public FileConfiguration getRodConfig() {
        return rodConfig;
    }

    public FileConfiguration getBaitConfig() {
        return baitConfig;
    }
    
    public FileConfiguration getCompeteConfig() {
        return competeConfig;
    }
    
    public FileConfiguration getSoundConfig() {
        return soundConfig;
    }

    public boolean isVanillaFishingDisabled() {
        return mainConfig.getBoolean("fishing-settings.disable-vanilla-fishing", false);
    }
    
    public boolean isWorldWhitelistEnabled() {
        return mainConfig.getBoolean("world-whitelist.enabled", true);
    }
    
    public boolean isWorldAllowed(String worldName) {
        if (!isWorldWhitelistEnabled()) {
            return true;
        }
        List<String> whitelistedWorlds = mainConfig.getStringList("world-whitelist.worlds");
        return whitelistedWorlds.contains(worldName);
    }
    
    public List<String> getWhitelistedWorlds() {
        return mainConfig.getStringList("world-whitelist.worlds");
    }

    public boolean isCommandSwitchEnabled() {
        return mainConfig.getBoolean("mode-switch.allow-command-switch", true);
    }

    public boolean isVanillaModeGiveCustomFish() {
        return mainConfig.getBoolean("mode-switch.vanilla-mode.give-custom-fish", true);
    }
    
    public boolean isSeasonalFishingEnabled() {
        return mainConfig.getBoolean("seasonal.enabled", true);
    }
    
    public List<String> getAvailableFish(String season) {
        List<String> availableFish = new ArrayList<>();
        
        if (fishConfig.contains("fish")) {
            ConfigurationSection fishSection = fishConfig.getConfigurationSection("fish");
            if (fishSection != null) {
                for (String fishName : fishSection.getKeys(false)) {
                    if (fishConfig.contains("fish." + fishName + ".seasons")) {
                        List<String> allowedSeasons = fishConfig.getStringList("fish." + fishName + ".seasons");
                        if (allowedSeasons.contains("all") || allowedSeasons.contains(season)) {
                            availableFish.add(fishName);
                        }
                    } else {
                        availableFish.add(fishName);
                    }
                }
            }
        }
        
        return availableFish;
    }
    
    public List<String> getAllFishNames() {
        List<String> fishNames = new ArrayList<>();
        
        if (fishConfig.contains("fish")) {
            ConfigurationSection fishSection = fishConfig.getConfigurationSection("fish");
            if (fishSection != null) {
                fishNames.addAll(fishSection.getKeys(false));
            }
        }
        
        return fishNames;
    }

    public List<String> getFishCommands(String fishName) {
        if (fishConfig.contains("fish." + fishName + ".command")) {
            return fishConfig.getStringList("fish." + fishName + ".command");
        }
        return new ArrayList<>();
    }

    public boolean fishExists(String fishName) {
        return fishConfig.contains("fish." + fishName);
    }

    public int getFishRarity(String fishName) {
        if (fishConfig.contains("fish." + fishName + ".level")) {
            List<Map<?, ?>> levels = fishConfig.getMapList("fish." + fishName + ".level");
            for (Map<?, ?> levelMap : levels) {
                for (Object key : levelMap.keySet()) {
                    String levelName = key.toString();
                    String rarityName = getRarityNameByLevel(levelName);
                    return getRarityOrder(rarityName);
                }
            }
        }
        return fishConfig.getInt("fish." + fishName + ".rarity", 1);
    }
    
    public List<String> getFishRarityNames(String fishName) {
        List<String> rarityNames = new ArrayList<>();
        
        if (fishConfig.contains("fish." + fishName + ".level")) {
            List<Map<?, ?>> levels = fishConfig.getMapList("fish." + fishName + ".level");
            
            String mainRarity = "common";
            int maxWeight = 0;
            
            for (Map<?, ?> levelMap : levels) {
                for (Map.Entry<?, ?> entry : levelMap.entrySet()) {
                    String levelName = entry.getKey().toString();
                    
                    try {
                        int weight = Integer.parseInt(entry.getValue().toString());
                        if (weight > maxWeight) {
                            maxWeight = weight;
                            mainRarity = levelName;
                        }
                    } catch (NumberFormatException e) {
                    }
                    
                    if (!rarityNames.contains(levelName)) {
                        rarityNames.add(levelName);
                    }
                }
            }
            
            if (rarityNames.contains(mainRarity)) {
                rarityNames.remove(mainRarity);
                rarityNames.add(0, mainRarity + "(主要)");
            }
        }
        
        if (rarityNames.isEmpty()) {
            rarityNames.add("common");
        }
        
        return rarityNames;
    }

    public double getFishActivity(String fishName) {
        return fishConfig.getDouble("fish." + fishName + ".activity", 1.0);
    }

    public double getFishBiteRateMultiplier(String fishName) {
        return fishConfig.getDouble("fish." + fishName + ".bite-rate-multiplier", 1.0);
    }
    
    public double getFishRareChance(String fishName) {
        if (fishConfig.contains("fish." + fishName + ".level")) {
            List<Map<?, ?>> levels = fishConfig.getMapList("fish." + fishName + ".level");
            double rareChance = 0;
            for (Map<?, ?> levelMap : levels) {
                for (Object key : levelMap.keySet()) {
                    String levelName = key.toString();
                    if (levelName.contains("legendary") || levelName.contains("epic") || levelName.contains("rare")) {
                        rareChance += Double.parseDouble(levelMap.get(key).toString()) / 100.0;
                    }
                }
            }
            return rareChance;
        }
        return fishConfig.getDouble("fish." + fishName + ".rare-fish-chance", 0.05);
    }

    public String getRandomFishLevel(String fishName) {
        return getRandomFishLevel(fishName, null);
    }
    
    public String getRandomFishLevel(String fishName, Player player) {
        try {
            if (fishConfig.contains("fish." + fishName + ".level")) {
                Object levelObj = fishConfig.get("fish." + fishName + ".level");
                List<Map<?, ?>> levels = new ArrayList<>();
                
                if (levelObj instanceof List) {
                    List<?> rawList = (List<?>) levelObj;
                    for (Object item : rawList) {
                        if (item instanceof Map) {
                            levels.add((Map<?, ?>) item);
                        }
                    }
                    if (levels.isEmpty() && rawList.size() > 0) {
                        Object firstItem = rawList.get(0);
                        if (firstItem instanceof String) {
                            return firstItem.toString();
                        }
                    }
                } else if (levelObj instanceof Map) {
                    Map<?, ?> singleLevel = (Map<?, ?>) levelObj;
                    levels.add(singleLevel);
                } else if (levelObj instanceof String) {
                    return levelObj.toString();
                }
                
                if (levels.isEmpty()) {
                    return "common";
                }
                
                int totalWeight = 0;
                List<Map.Entry<String, Integer>> weightedEntries = new ArrayList<>();
                
                for (Map<?, ?> levelMap : levels) {
                    for (Map.Entry<?, ?> entry : levelMap.entrySet()) {
                                try {
                                    String levelName = entry.getKey().toString();
                                    int baseWeight = Integer.parseInt(entry.getValue().toString());
                                    
                                    int globalWeight = getGlobalLevelWeight(levelName);
                                    int finalWeight = baseWeight * globalWeight;
                                    
                                    if (player != null) {
                                        AuraSkills auraSkills = kkfish.getInstance().getAuraSkills();
                                        if (auraSkills != null) {
                                            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_aura_skills_exists", "AuraSkills存在，isEnabled: %s", auraSkills.isAuraSkillsEnabled()));
                                            if (auraSkills.isAuraSkillsEnabled()) {
                                                int fishingLevel = auraSkills.getFishingLevel(player);
                                                logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_player_fishing_level", "玩家 %s 的钓鱼等级: %s", player.getName(), fishingLevel));
                                                
                                                double bonusMultiplier = auraSkills.getRareFishBonus(fishingLevel);
                                                
                                                if (levelName.toLowerCase().equals("legendary")) {
                                                    finalWeight = (int)Math.max(1, finalWeight * bonusMultiplier * 1.2);
                                                } else if (levelName.toLowerCase().equals("epic")) {
                                                    finalWeight = (int)Math.max(1, finalWeight * bonusMultiplier * 1.1);
                                                } else if (levelName.toLowerCase().equals("rare")) {
                                                    finalWeight = (int)Math.max(1, finalWeight * bonusMultiplier);
                                                } else {
                                                    finalWeight = (int)Math.max(50, finalWeight * (2.0 - bonusMultiplier));
                                                }
                                                logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_fish_level_weight", "鱼 %s 的 %s 等级权重调整为: %s", fishName, levelName, finalWeight));
                                            }
                                        } else {
                                            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_aura_skills_not_exists", "AuraSkillsHandler不存在！"));
                                        }
                                    }
                                    
                                    weightedEntries.add(new AbstractMap.SimpleEntry<>(levelName, finalWeight));
                                    totalWeight += finalWeight;
                                } catch (NumberFormatException e) {
                                    logger.warning(plugin.getMessageManager().getMessageWithoutPrefix("config_fish_level_weight_error", "鱼 %s 的等级权重格式错误: %s", fishName, entry.getValue()));
                                }
                            }
                }
                
                if (totalWeight <= 0 || weightedEntries.isEmpty()) {
                    return plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.common", "普通");
                }
                
                Random random = new Random();
                int randomValue = random.nextInt(totalWeight) + 1;
                int currentWeight = 0;
                
                for (Map.Entry<String, Integer> entry : weightedEntries) {
                            currentWeight += entry.getValue();
                            if (randomValue <= currentWeight) {
                                String levelName = entry.getKey();
                                if (levelName.contains(":")) {
                                    levelName = levelName.split(":")[0];
                                }
                                return levelName;
                            }
                        }
            }
            return getRarityDisplayName("common");
        } catch (Exception e) {
            logger.warning(plugin.getMessageManager().getMessageWithoutPrefix("config_fish_level_calc_error", "计算钓鱼等级时出错: %s", e.getMessage()));
            return getRarityDisplayName("common");
        }
    }
    
    private int getGlobalLevelWeight(String levelName) {
        if (levelName.contains(":")) {
            levelName = levelName.split(":")[0];
        }
        
        levelName = levelName.toLowerCase();
        
        return mainConfig.getInt("fishing-settings.rarity." + levelName + ".weight", 50);
    }

    public boolean rodExists(String rodName) {
        if ("default_rod".equals(rodName)) {
            return rodConfig.contains("rods.default");
        }
        return rodConfig.contains("rods." + rodName);
    }

    public double getRodDifficulty(String rodName) {
        if ("default_rod".equals(rodName)) {
            return rodConfig.getDouble("rods.default.difficulty", 1.0);
        }
        return rodConfig.getDouble("rods." + rodName + ".difficulty", 1.0);
    }

    public int getRodFloatAreaSize(String rodName) {
        if ("default_rod".equals(rodName)) {
            return rodConfig.getInt("rods.default.float-area-size", 30);
        }
        return rodConfig.getInt("rods." + rodName + ".float-area-size", 20);
    }

    public boolean baitExists(String baitName) {
        return baitConfig.contains("baits." + baitName);
    }

    public String getBaitEffect(String baitName) {
        if (baitConfig.contains("baits." + baitName + ".effect")) {
            return baitConfig.getString("baits." + baitName + ".effect", "none");
        }
        List<String> effects = getBaitEffects(baitName);
        return effects.isEmpty() ? "none" : effects.get(0);
    }

    public double getBaitEffectValue(String baitName) {
        if (baitConfig.contains("baits." + baitName + ".value")) {
            return baitConfig.getDouble("baits." + baitName + ".value", 0.0);
        }
        return 0.0;
    }

    public List<String> getBaitEffects(String baitName) {
        List<String> effects = new ArrayList<>();
        if (baitConfig.contains("baits." + baitName + ".effects")) {
            return baitConfig.getStringList("baits." + baitName + ".effects");
        }
        if (baitConfig.contains("baits." + baitName + ".effect")) {
            String singleEffect = baitConfig.getString("baits." + baitName + ".effect", "none");
            if (!singleEffect.equals("none")) {
                effects.add(singleEffect);
            }
        }
        return effects;
    }
    
    public boolean isAutoEquipBaitEnabled() {
        return baitConfig.getBoolean("bait-crafting.auto-equip-bait", true);
    }

    public double getBaitEffectValueByName(String baitName, String effectType) {
        if (baitConfig.contains("baits." + baitName + ".effect-values")) {
            Object valueObj = baitConfig.get("baits." + baitName + ".effect-values." + effectType);
            if (valueObj instanceof Number) {
                return ((Number) valueObj).doubleValue();
            }
        }
        return 0.0;
    }
    
    public List<String> getAllBaitNames() {
        List<String> baitNames = new ArrayList<>();
        if (baitConfig.contains("baits")) {
            baitNames.addAll(baitConfig.getConfigurationSection("baits").getKeys(false));
        }
        return baitNames;
    }

    public List<String> getBaitPermissions(String baitName) {
        if (baitConfig.contains("baits." + baitName + ".permissions")) {
            return baitConfig.getStringList("baits." + baitName + ".permissions");
        }
        return new ArrayList<>();
    }

    public boolean hasBaitPermission(Player player, String baitName) {
        List<String> permissions = getBaitPermissions(baitName);
        if (permissions.isEmpty()) {
            return true;
        }
        
        for (String perm : permissions) {
            if (player.hasPermission(perm) || player.isOp()) {
                return true;
            }
        }
        
        if (player.hasPermission("kkfish.baits.use.*")) {
            return true;
        }
        
        return false;
    }
    
    public void generateBaitPermissions() {
        List<String> allBaitNames = getAllBaitNames();
        
        if (allBaitNames.isEmpty()) {
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("log.no_baits_config", "No bait configurations found, no permission groups needed~"));
            return;
        }
        
        plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("log.generate_baits_start", "Starting automatic generation of bait permission groups..."));
        
        for (String baitName : allBaitNames) {
            List<String> perms = new ArrayList<>();
            
            perms.add("kkfish.baits.use." + baitName);
            
            if (baitName.contains("钻石") || baitName.contains("diamond")) {
                perms.add("kkfish.baits.diamond");
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("log.generate_bait_diamond", "Automatically added permission group for diamond bait '%s'").replace("%s", baitName));
            } else if (baitName.contains("魔法") || baitName.contains("magic")) {
                perms.add("kkfish.baits.magic");
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("log.generate_bait_magic", "Automatically added permission group for magic bait '%s'").replace("%s", baitName));
            }
            
            baitConfig.set("baits." + baitName + ".permissions", perms);
        }
        
        if (!baitConfig.contains("global-permissions")) {
            baitConfig.set("global-permissions.description", "以下是所有可用的全局权限节点");
            baitConfig.set("global-permissions.use-all", "kkfish.baits.use.* - 允许使用所有鱼饵");
            baitConfig.set("global-permissions.diamond-group", "kkfish.baits.diamond - 允许使用所有钻石鱼饵");
            baitConfig.set("global-permissions.magic-group", "kkfish.baits.magic - 允许使用所有魔法鱼饵");
        }
        
        try {
            baitConfig.save(baitFile);
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("log.generate_baits_complete", "Bait permission group generation complete, saved to baits.yml!"));
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("log.save_baits_failed", "Error saving bait permission configuration: ") + e.getMessage());
        }
    }

    public List<String> getAvailableFish(String biome, String weather, long time) {
        return getAvailableFish(biome, weather, time, null);
    }
    
    public List<String> getAvailableFish(String biome, String weather, long time, String season) {
        List<String> availableFish = new ArrayList<>();
        if (fishConfig.contains("fish")) {
            for (String fishName : fishConfig.getConfigurationSection("fish").getKeys(false)) {
                boolean canSpawn = true;
                
                if (fishConfig.contains("fish." + fishName + ".biomes")) {
                    List<String> biomes = fishConfig.getStringList("fish." + fishName + ".biomes");
                    if (!biomes.isEmpty() && !biomes.contains(biome)) {
                        canSpawn = false;
                    }
                }
                
                if (canSpawn && fishConfig.contains("fish." + fishName + ".weather")) {
                    List<String> weathers = fishConfig.getStringList("fish." + fishName + ".weather");
                    if (!weathers.isEmpty() && !weathers.contains(weather)) {
                        canSpawn = false;
                    }
                }
                
                if (canSpawn && fishConfig.contains("fish." + fishName + ".time")) {
                    List<String> timeRanges = fishConfig.getStringList("fish." + fishName + ".time");
                    boolean timeMatch = false;
                    for (String range : timeRanges) {
                        if (range.equalsIgnoreCase("ANY") || isTimeInRange(time, range)) {
                            timeMatch = true;
                            break;
                        }
                    }
                    if (!timeRanges.isEmpty() && !timeMatch) {
                        canSpawn = false;
                    }
                }
                
                if (canSpawn && isSeasonalFishingEnabled() && season != null && fishConfig.contains("fish." + fishName + ".seasons")) {
                    List<String> seasons = fishConfig.getStringList("fish." + fishName + ".seasons");
                    if (!seasons.isEmpty() && !seasons.contains(season)) {
                        canSpawn = false;
                    }
                }
                
                if (canSpawn) {
                    availableFish.add(fishName);
                }
            }
        }
        return availableFish;
    }

    private boolean isTimeInRange(long time, String range) {
        time = time % 24000;
        
        switch (range.toUpperCase()) {
            case "DAY":
                return time >= 0 && time < 12000;
            case "NIGHT":
                return time >= 12000 && time < 24000;
            case "DAWN":
                return time >= 23000 || time < 1000;
            case "DUSK":
                return time >= 12000 && time < 13000;
            default:
                return true;
        }
    }


    
    public boolean isVanillaExpEnabled() {
        return mainConfig.getBoolean("fishing-settings.enable-vanilla-exp", true);
    }

    public double getVanillaExpMultiplier() {
        return mainConfig.getDouble("fishing-settings.vanilla-exp-multiplier", 1.0);
    }

    public boolean isFishEscapeBeforeMinigameEnabled() {
        return mainConfig.getBoolean("fishing-settings.escape", false);
    }

    public int getFishExp(String fishName) {
        int baseExp = fishConfig.getInt("fish." + fishName + ".exp", 10);
        double multiplier = getVanillaExpMultiplier();
        return (int) Math.round(baseExp * multiplier);
    }
    
    public double getFishValue(String fishName) {
        if (!fishExists(fishName)) {
            return 0.0;
        }
        if (fishConfig.contains("fish." + fishName + ".value")) {
            return fishConfig.getDouble("fish." + fishName + ".value", 0.0);
        }
        int rarity = getFishRarity(fishName);
        return rarity * 10.0;
    }
    
    public List<String> getFishEffects(String fishName) {
        return fishConfig.getStringList("fish." + fishName + ".effects");
    }
    
    public int getFishSaturation(String fishName) {
        if (!fishExists(fishName)) {
            return 0;
        }
        return fishConfig.getInt("fish." + fishName + ".saturation", 2);
    }
    
    public boolean isFishAnnouncementEnabled(String fishName) {
        if (!fishExists(fishName)) {
            return false;
        }
        return fishConfig.getBoolean("fish." + fishName + ".announcement", false);
    }
    
    public String getFishTemplate(String templateName) {
        if (mainConfig.contains("item-templates.fish-templates." + templateName + ".content")) {
            String content = mainConfig.getString("item-templates.fish-templates." + templateName + ".content", "");
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', content);
        }
        return "%name%\n%description%\n%size%\n%value%\n%rarity%";
    }
    
    public String getFishTemplateName(String fishName) {
        if (!fishExists(fishName)) {
            return "default";
        }
        return fishConfig.getString("fish." + fishName + ".template", "default");
    }
    
    public String getRodTemplate(String templateName) {
        if (mainConfig.contains("item-templates.rod-templates." + templateName + ".content")) {
            String content = mainConfig.getString("item-templates.rod-templates." + templateName + ".content", "");
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', content);
        }
        return "%name%\n%description%\n%durability%\n%catch-rate%\n%features%";
    }
    
    public String getRodTemplateName(String rodName) {
        if (!rodConfig.contains("rods." + rodName)) {
            return "default";
        }
        return rodConfig.getString("rods." + rodName + ".template", "default");
    }
    
    public String getHookTemplate(String templateName) {
        if (mainConfig.contains("item-templates.hook-templates." + templateName + ".content")) {
            String content = mainConfig.getString("item-templates.hook-templates." + templateName + ".content", "");
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', content);
        }
        return "%name%\n%description%\n%durability%\n%features%";
    }
    
    public String getHookTemplateName(String hookName) {
        if (!hookConfig.contains("hooks." + hookName)) {
            return "default";
        }
        return hookConfig.getString("hooks." + hookName + ".template", "default");
    }
    
    public String getBaitTemplate(String templateName) {
        if (mainConfig.contains("item-templates.bait-templates." + templateName + ".content")) {
            String content = mainConfig.getString("item-templates.bait-templates." + templateName + ".content", "");
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', content);
        }
        return "%name%\n%description%\n%effects%";
    }
    
    public String getBaitTemplateName(String baitName) {
        if (!baitExists(baitName)) {
            return "default";
        }
        return baitConfig.getString("baits." + baitName + ".template", "default");
    }

    public boolean isSeasonalPriceFluctuationEnabled() {
        return mainConfig.getBoolean("seasonal.price-fluctuation.enabled", true);
    }
    
    public double getSeasonalPriceMultiplier(String season) {
        if (season == null) {
            return 1.0;
        }
        String path = "seasonal.price-fluctuation." + season.toLowerCase();
        return 1.0 + mainConfig.getDouble(path, 0.0);
    }
    
    public double getBasePriceFluctuation() {
        return mainConfig.getDouble("seasonal.price-fluctuation.base", 0.05);
    }
    
    public boolean isDebugMode() {
        return mainConfig.getBoolean("debug.enabled", mainConfig.getBoolean("debug", false));
    }
    
    public boolean isUpdateCheckEnabled() {
        return mainConfig.getBoolean("update-check.enabled", true);
    }
    
    public String getTimezone() {
        return mainConfig.getString("timezone.value", mainConfig.getString("timezone", ""));
    }
    
    public boolean isVisualEffectsEnabled() {
        return mainConfig.getBoolean("visual-effects.enabled", true);
    }
    
    public boolean isCustomNBTSupportEnabled() {
        return true;
    }
    
    public boolean isEconomyEnabled() {
        return mainConfig.getBoolean("economy.enabled", true);
    }
    
    public boolean isVaultEnabled() {
        return isEconomyEnabled();
    }
    
    public boolean isPriceEnabled() {
        return isEconomyEnabled();
    }
    
    public boolean isHookWaterSplashParticleEnabled() {
        return isVisualEffectsEnabled();
    }
    
    public String getHookWaterSplashParticleType() {
        return "dust";
    }
    
    public int getHookWaterSplashParticleRed() {
        return 255;
    }
    
    public int getHookWaterSplashParticleGreen() {
        return 255;
    }
    
    public int getHookWaterSplashParticleBlue() {
        return 255;
    }
    
    public float getHookWaterSplashParticleSize() {
        return 1.0f;
    }
    
    public int getHookWaterSplashParticleCount() {
        return 10;
    }
    
    public double getHookWaterSplashParticleSpreadX() {
        return 0.5;
    }
    
    public double getHookWaterSplashParticleSpreadY() {
        return 0.5;
    }
    
    public double getHookWaterSplashParticleSpreadZ() {
        return 0.5;
    }
    
    public double getHookWaterSplashParticleExtra() {
        return 1.0;
    }
    
    public boolean isFishEmergeParticleEnabled() {
        return isVisualEffectsEnabled();
    }
    
    public String getFishEmergeParticleType() {
        return "REDSTONE";
    }
    
    public int getFishEmergeParticleRed() {
        return 255;
    }
    
    public int getFishEmergeParticleGreen() {
        return 255;
    }
    
    public int getFishEmergeParticleBlue() {
        return 255;
    }
    
    public float getFishEmergeParticleSize() {
        return 3.0f;
    }
    
    public int getFishEmergeParticleCount() {
        return 38;
    }
    
    public double getFishEmergeParticleSpreadX() {
        return 1.0;
    }
    
    public double getFishEmergeParticleSpreadY() {
        return 0.2;
    }
    
    public double getFishEmergeParticleSpreadZ() {
        return 1.0;
    }
    
    public double getFishEmergeParticleExtra() {
        return 0.05;
    }
    
    public double getFishEmergeParticleOffsetX() {
        return 0.0;
    }
    
    public double getFishEmergeParticleOffsetY() {
        return 1.0;
    }
    
    public double getFishEmergeParticleOffsetZ() {
        return 0.0;
    }
    
    public boolean isFishAnimationEnabled() {
        return mainConfig.getBoolean("visual-effects.fish-animation.enabled", true);
    }
    
    public int getFishAnimationMaxTicks() {
        return mainConfig.getInt("visual-effects.fish-animation.max-ticks", 40);
    }
    
    public double getFishAnimationPeakHeight() {
        return mainConfig.getDouble("visual-effects.fish-animation.peak-height", 2.5);
    }
    
    public double getFishAnimationMaxSpeed() {
        return mainConfig.getDouble("visual-effects.fish-animation.max-speed", 0.5);
    }
    
    public double getFishAnimationUpwardForceFactor() {
        return mainConfig.getDouble("visual-effects.fish-animation.upward-force-factor", 0.25);
    }
    
    public double getFishAnimationPeakProgress() {
        return mainConfig.getDouble("visual-effects.fish-animation.peak-progress", 0.25);
    }

    public double getFishAnimationMinYOffset() {
        return mainConfig.getDouble("visual-effects.fish-animation.min-y-offset", 0.8);
    }
    
    public int getFishJumpToHeadBaseDuration() {
        return mainConfig.getInt("visual-effects.fish-animation.jump-to-head.base-duration", 20);
    }
    
    public double getFishJumpToHeadDistanceMultiplier() {
        return mainConfig.getDouble("visual-effects.fish-animation.jump-to-head.distance-multiplier", 5.0);
    }
    
    public int getFishJumpToHeadMaxDuration() {
        return mainConfig.getInt("visual-effects.fish-animation.jump-to-head.max-duration", 60);
    }
    
    public double getFishJumpToHeadInitialJumpHeight() {
        return mainConfig.getDouble("visual-effects.fish-animation.jump-to-head.initial-jump-height", 2.0);
    }
    
    public double getFishJumpToHeadCurveHeight() {
        return mainConfig.getDouble("visual-effects.fish-animation.jump-to-head.curve-height", 3.0);
    }
    
    public double getFishJumpToHeadEasingFactor() {
        return mainConfig.getDouble("visual-effects.fish-animation.jump-to-head.easing-factor", 2.0);
    }
    
    public void setDebugMode(boolean debugMode) {
        mainConfig.set("debug", debugMode);
        saveConfigs();
        if (debugMode) {
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_debug_enabled", "调试模式已开启！将显示详细的钓鱼过程日志~ "));
        } else {
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_debug_disabled", "调试模式已关闭~ "));
        }
    }
    
    public void debugLog(String message) {
        if (isDebugMode()) {
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_debug_prefix", "[调试] %s", message));
        }
    }
    
    public int getRodDurability(String rodName) {
        if (!rodExists(rodName)) {
            return 0;
        }
        if ("default_rod".equals(rodName)) {
            return rodConfig.getInt("rods.default.durability", 65);
        }
        return rodConfig.getInt("rods." + rodName + ".durability", 0);
    }
    

    
    public double getRodChargeSpeed(String rodName) {
        if (!rodExists(rodName)) {
            return 1.0;
        }
        if ("default_rod".equals(rodName)) {
            return rodConfig.getDouble("rods.default.charge-speed", 1.0);
        }
        return rodConfig.getDouble("rods." + rodName + ".charge-speed", 1.0);
    }
    
    public int getRodEnchantability(String rodName) {
        if (!rodExists(rodName)) {
            return 15;
        }
        if ("default_rod".equals(rodName)) {
            return rodConfig.getInt("rods.default.enchantability", 15);
        }
        return rodConfig.getInt("rods." + rodName + ".enchantability", 15);
    }
    
    public double getRodBiteRateBonus(String rodName) {
        if (!rodExists(rodName)) {
            return 0.0;
        }
        if ("default_rod".equals(rodName)) {
            return rodConfig.getDouble("rods.default.bite-chance-bonus", 0.0);
        }
        return rodConfig.getDouble("rods." + rodName + ".bite-rate-bonus", 0.0);
    }

    public double getRodRareFishChance(String rodName) {
        if (!rodExists(rodName)) {
            return 0.0;
        }
        if ("default_rod".equals(rodName)) {
            return rodConfig.getDouble("rods.default.rare-fish-chance", 0.0);
        }
        return rodConfig.getDouble("rods." + rodName + ".rare-fish-chance", 0.0);
    }

    public int getRodCustomModelData(String rodName) {
        if (!rodExists(rodName)) {
            return 0;
        }
        if ("default_rod".equals(rodName)) {
            return rodConfig.getInt("rods.default.custom-model-data", 0);
        }
        return rodConfig.getInt("rods." + rodName + ".custom-model-data", 0);
    }
    
    public int getBaitCustomModelData(String baitName) {
        if (!baitExists(baitName)) {
            return 0;
        }
        return baitConfig.getInt("baits." + baitName + ".custom-model-data", 0);
    }
    
    public int getHookCustomModelData(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return 0;
        }
        return hookConfig.getInt(configPath + ".custom-model-data", 0);
    }
    
    public List<String> getRodEffects(String rodName) {
        if (!rodExists(rodName)) {
            return new ArrayList<>();
        }
        if ("default_rod".equals(rodName)) {
            return rodConfig.getStringList("rods.default.effects");
        }
        return rodConfig.getStringList("rods." + rodName + ".effects");
    }
    

    public Map<String, Object> getHookConfigs() {
        Map<String, Object> allHooks = new LinkedHashMap<>();
        
        if (hookConfig == null) {
            debugLog(plugin.getMessageManager().getMessageWithoutPrefix("config_hook_config_null", "警告: hookConfig为null，无法读取鱼钩配置"));
            return allHooks;
        }
        
        if (hookConfig.contains("hooks")) {
            ConfigurationSection pagesSection = hookConfig.getConfigurationSection("hooks");
            if (pagesSection != null) {
                for (String pageKey : pagesSection.getKeys(false)) {
                    try {
                        Integer.parseInt(pageKey);
                        ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageKey);
                        if (pageSection != null) {
                            for (String hookName : pageSection.getKeys(false)) {
                                allHooks.put(hookName, pageSection.getValues(false).get(hookName));
                            }
                        } else {
                            debugLog(plugin.getMessageManager().getMessageWithoutPrefix("config_hook_page_empty", "警告: 页面 %s 的配置节点为空", pageKey));
                        }
                    } catch (NumberFormatException e) {
                        allHooks.put(pageKey, pagesSection.getValues(false).get(pageKey));
                    }
                }
            } else {
                debugLog(plugin.getMessageManager().getMessageWithoutPrefix("config_hooks_empty", "警告: hooks配置节点存在但为空"));
            }
        } else {
            debugLog(plugin.getMessageManager().getMessageWithoutPrefix("config_hooks_not_exist", "警告: hooks配置节点不存在，检查hooks.yml格式是否正确"));
            
            Set<String> rootKeys = hookConfig.getKeys(false);
            for (String key : rootKeys) {
                if (!key.equals("items-per-page") && 
                    !key.equals("rows") && 
                    !key.equals("pagination-enabled") &&
                    !key.equals("settings") &&
                    !key.equals("gui") &&
                    !key.equals("categories")) {
                    allHooks.put(key, hookConfig.get(key));
                }
            }
        }
        
        debugLog("成功读取到" + allHooks.size() + "个鱼钩配置");
        
        return allHooks;
    }
    
    public Map<String, Object> getHookConfigsByPage(int page) {
        Map<String, Object> pageHooks = new LinkedHashMap<>();
        if (hookConfig.contains("hooks." + page)) {
            ConfigurationSection pageSection = hookConfig.getConfigurationSection("hooks." + page);
            if (pageSection != null) {
                pageHooks.putAll(pageSection.getValues(false));
            }
        }
        return pageHooks;
    }
    
    public int getTotalHookPages() {
        int maxPage = 0;
        if (hookConfig.contains("hooks")) {
            ConfigurationSection pagesSection = hookConfig.getConfigurationSection("hooks");
            for (String pageKey : pagesSection.getKeys(false)) {
                try {
                    int pageNum = Integer.parseInt(pageKey);
                    if (pageNum > maxPage) {
                        maxPage = pageNum;
                    }
                } catch (NumberFormatException e) {
                }
            }
        }
        return maxPage;
    }
    
    public Map<String, Object> getHookConfig(String hookName) {
        if (!hookExists(hookName)) {
            return null;
        }
        ConfigurationSection pagesSection = hookConfig.getConfigurationSection("hooks");
        for (String pageKey : pagesSection.getKeys(false)) {
            if (pagesSection.contains(pageKey + "." + hookName)) {
                return pagesSection.getConfigurationSection(pageKey + "." + hookName).getValues(false);
            }
        }
        return null;
    }
    
    public boolean hookExists(String hookName) {
        if (hookConfig.contains("hooks")) {
            ConfigurationSection pagesSection = hookConfig.getConfigurationSection("hooks");
            for (String pageKey : pagesSection.getKeys(false)) {
                ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageKey);
                if (pageSection != null && pageSection.contains(hookName)) {
                    return true;
                }
            }
        }
        
        return hookConfig.contains(hookName);
    }
    
    private String getHookConfigPath(String hookName) {
        if (hookConfig.contains("hooks")) {
            ConfigurationSection pagesSection = hookConfig.getConfigurationSection("hooks");
            for (String pageKey : pagesSection.getKeys(false)) {
                ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageKey);
                if (pageSection != null && pageSection.contains(hookName)) {
                    return "hooks." + pageKey + "." + hookName;
                }
            }
        }
        
        if (hookConfig.contains(hookName)) {
            return hookName;
        }
        
        return null;
    }

    public Material getHookMaterial(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return XSeriesUtil.getMaterial("OAK_LOG");
        }
        String materialName = hookConfig.getString(configPath + ".material", "OAK_LOG");
        try {
            return XSeriesUtil.parseMaterial(materialName);
        } catch (Exception e) {
            logger.warning(plugin.getMessageManager().getMessageWithoutPrefix("config_invalid_hook_material", "无效的鱼钩材质: %s, 使用默认材质", materialName));
            return XSeriesUtil.getMaterial("OAK_LOG");
        }
    }
    
    public String getHookDisplayNameKey(String hookName) {
        return "gui_hook_material_" + hookName;
    }
    
    public String getHookDisplayName(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return hookName; // 默认返回鱼钩名称
        }
        return hookConfig.getString(configPath + ".display-name", hookName);
    }
    
    public String getHookDescriptionKey(String hookName) {
        return "gui_hook_desc_" + hookName;
    }
    
    public String getHookDescription(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return "";
        }
        return hookConfig.getString(configPath + ".description", "");
    }
    
    public String getHookRarity(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return plugin.getMessageManager().getMessageWithoutPrefix("hook_rarity_basic", "Basic");
        }
        return hookConfig.getString(configPath + ".rarity", plugin.getMessageManager().getMessageWithoutPrefix("hook_rarity_basic", "Basic"));
    }
    
    public List<String> getHookPermissions(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return new ArrayList<>();
        }
        return hookConfig.getStringList(configPath + ".permissions");
    }
    
    public double getHookBiteRateBonus(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return 0.0;
        }
        return hookConfig.getDouble(configPath + ".effects.bite-rate-bonus", 0.0);
    }
    
    public double getHookRareFishChance(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return 0.0;
        }
        return hookConfig.getDouble(configPath + ".effects.rare-fish-chance", 0.0);
    }
    
    public boolean isHookVisibleInGui(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return false;
        }
        return hookConfig.getBoolean(configPath + ".show-in-gui", true);
    }
    
    public double getHookVaultPrice(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return 0.0;
        }
        double price = hookConfig.getDouble(configPath + ".price.vault", 0.0);
        return price == -1 ? Double.NaN : price;
    }
    
    @Deprecated
    public double getHookMoneyPrice(String hookName) {
        return getHookVaultPrice(hookName);
    }
    
    public int getHookPointsPrice(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return 0;
        }
        int price = hookConfig.getInt(configPath + ".price.points", 0);
        return price == -1 ? Integer.MIN_VALUE : price;
    }
    
    @Deprecated
    public int getHookPlayerPointsPrice(String hookName) {
        return getHookPointsPrice(hookName);
    }
    
    public boolean isHookNeedPurchase(String hookName) {
        double vaultPrice = getHookVaultPrice(hookName);
        int pointsPrice = getHookPointsPrice(hookName);
        return (!Double.isNaN(vaultPrice) && vaultPrice > 0) || (pointsPrice != Integer.MIN_VALUE && pointsPrice > 0);
    }
    
    public boolean canPurchaseWithVault(String hookName) {
        double price = getHookVaultPrice(hookName);
        return !Double.isNaN(price) && price > 0;
    }
    
    public boolean canPurchaseWithPoints(String hookName) {
        int price = getHookPointsPrice(hookName);
        return price != Integer.MIN_VALUE && price > 0;
    }

    public boolean hasHookMaterialPermission(Player player, String materialType) {
        if (player == null || materialType == null || materialType.isEmpty()) {
            return false;
        }
        
        String permission = "kkfish.hook." + materialType.toLowerCase();
        
        if (player.hasPermission(permission) || player.hasPermission("kkfish.hook.*")) {
            if (isDebugMode()) {
                logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_player_get_hook_perm", "玩家 %s 通过权限 %s 获得了鱼钩 %s", player.getName(), (player.hasPermission(permission) ? permission : "kkfish.hook.*"), materialType));
            }
            return true;
        }
        
        if (plugin.getDB() != null) {
            boolean hasPurchased = plugin.getDB().hasPlayerPurchasedHook(player.getUniqueId().toString(), materialType);
            if (hasPurchased) {
                if (isDebugMode()) {
                    logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_player_get_hook_buy", "玩家 %s 通过购买获得了鱼钩 %s", player.getName(), materialType));
                }
                return true;
            }
        }
        
        List<String> permissions = getHookPermissions(materialType);
        if (!permissions.isEmpty()) {
            for (String perm : permissions) {
                if (perm != null && !perm.isEmpty() && player.hasPermission(perm)) {
                    if (isDebugMode()) {
                        logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_player_get_hook_special", "玩家 %s 通过特殊权限 %s 获得了鱼钩 %s", player.getName(), perm, materialType));
                    }
                    return true;
                }
            }
            return false;
        }
        
        if (materialType.equalsIgnoreCase("wood")) {
            return true;
        }
        
        return player.isOp();
    }
    
    public void checkAndAddMissingConfigs() {
        checkAndAddMainConfigDefaults();
        
        checkAndAddFishConfigDefaults();
        
        checkAndAddRodConfigDefaults();
        
        checkAndAddBaitConfigDefaults();
        
        checkAndAddSoundConfigDefaults();
        
        saveConfigs();
    }
    
    private void checkAndAddMainConfigDefaults() {
        if (!mainConfig.contains("fishing-settings")) {
                mainConfig.set("fishing-settings.max-charge-time", 1000);
                mainConfig.set("fishing-settings.cast-cooldown", 5000);
                mainConfig.set("fishing-settings.base-bite-chance", 0.2);
                mainConfig.set("fishing-settings.max-bite-chance", 1.0);
                mainConfig.set("fishing-settings.bite-check-delay-min", 5000);
                mainConfig.set("fishing-settings.bite-check-delay-max", 15000);
                mainConfig.set("fishing-settings.initial-progress", 10);
                mainConfig.set("fishing-settings.disable-vanilla-fishing", true);
            
            mainConfig.set("fishing-settings.progress-bar.increase-speed", 0.0075);
            mainConfig.set("fishing-settings.progress-bar.decrease-speed", 0.01);
            mainConfig.set("fishing-settings.progress-bar.rarity-impact.enabled", true);
            mainConfig.set("fishing-settings.progress-bar.rarity-impact.slowdown-per-rarity-level", 0.15);
            mainConfig.set("fishing-settings.progress-bar.rarity-impact.min-increase-speed-ratio", 0.45);
            
            mainConfig.set("fishing-settings.rarity.multipliers.legendary", 2.0);
            mainConfig.set("fishing-settings.rarity.multipliers.epic", 1.5);
            mainConfig.set("fishing-settings.rarity.multipliers.rare", 1.2);
            mainConfig.set("fishing-settings.rarity.multipliers.common", 1);
            
            mainConfig.set("fishing-settings.enable-vanilla-exp", true);
            mainConfig.set("fishing-settings.vanilla-exp-multiplier", 1.0);
            
            mainConfig.set("fishing-settings.rarity.weights.legendary", 2);
            mainConfig.set("fishing-settings.rarity.weights.epic", 8);
            mainConfig.set("fishing-settings.rarity.weights.rare", 15);
            mainConfig.set("fishing-settings.rarity.weights.common", 50);
            
            mainConfig.set("fishing-settings.durability.max-single-loss", 5);
            mainConfig.set("fishing-settings.durability.base-loss", 1);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "fishing-settings"));
        }
        
        if (!mainConfig.contains("fishing-settings.disable-vanilla-fishing")) {
            mainConfig.set("fishing-settings.disable-vanilla-fishing", false);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "fishing-settings.disable-vanilla-fishing"));
        }
        
        if (!mainConfig.contains("fishing-settings.progress-bar.rarity-impact")) {
            mainConfig.set("fishing-settings.progress-bar.rarity-impact.enabled", true);
            mainConfig.set("fishing-settings.progress-bar.rarity-impact.slowdown-per-rarity-level", 0.15);
            mainConfig.set("fishing-settings.progress-bar.rarity-impact.min-increase-speed-ratio", 0.45);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "fishing-settings.progress-bar.rarity-impact"));
        }

        
        if (!mainConfig.contains("visual-effects")) {
            mainConfig.set("visual-effects.fish-animation.enabled", true);
            mainConfig.set("visual-effects.fish-animation.max-ticks", 40);
            mainConfig.set("visual-effects.fish-animation.peak-height", 2.5);
            mainConfig.set("visual-effects.fish-animation.max-speed", 0.5);
            mainConfig.set("visual-effects.fish-animation.upward-force-factor", 0.25);
            mainConfig.set("visual-effects.fish-animation.peak-progress", 0.25);
            mainConfig.set("visual-effects.fish-animation.min-y-offset", 0.8);
            
            mainConfig.set("visual-effects.fish-animation.jump-to-head.base-duration", 20);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.distance-multiplier", 5);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.max-duration", 60);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.initial-jump-height", 2.0);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.curve-height", 3.0);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.easing-factor", 2.0);
            
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "visual-effects"));
        }
        
        if (!mainConfig.contains("visual-effects.fish-animation")) {
            mainConfig.set("visual-effects.fish-animation.enabled", true);
            mainConfig.set("visual-effects.fish-animation.max-ticks", 40);
            mainConfig.set("visual-effects.fish-animation.peak-height", 2.5);
            mainConfig.set("visual-effects.fish-animation.max-speed", 0.5);
            mainConfig.set("visual-effects.fish-animation.upward-force-factor", 0.25);
            mainConfig.set("visual-effects.fish-animation.peak-progress", 0.25);
            mainConfig.set("visual-effects.fish-animation.min-y-offset", 0.8);
            
            mainConfig.set("visual-effects.fish-animation.jump-to-head.base-duration", 20);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.distance-multiplier", 5);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.max-duration", 60);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.initial-jump-height", 2.0);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.curve-height", 3.0);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.easing-factor", 2.0);
        }
        
        ensureFishAnimationSubConfigs();
        
        if (!mainConfig.contains("economy")) {
            mainConfig.set("economy.enabled", true);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "economy"));
        } else if (!mainConfig.contains("economy.enabled")) {
            mainConfig.set("economy.enabled", true);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "economy.enabled"));
        }
    }
    
    private void ensureFishAnimationSubConfigs() {
        if (!mainConfig.contains("visual-effects.fish-animation.enabled")) {
            mainConfig.set("visual-effects.fish-animation.enabled", true);
        }
        if (!mainConfig.contains("visual-effects.fish-animation.max-ticks")) {
            mainConfig.set("visual-effects.fish-animation.max-ticks", 40);
        }
        if (!mainConfig.contains("visual-effects.fish-animation.peak-height")) {
            mainConfig.set("visual-effects.fish-animation.peak-height", 2.5);
        }
        if (!mainConfig.contains("visual-effects.fish-animation.max-speed")) {
            mainConfig.set("visual-effects.fish-animation.max-speed", 0.5);
        }
        if (!mainConfig.contains("visual-effects.fish-animation.upward-force-factor")) {
            mainConfig.set("visual-effects.fish-animation.upward-force-factor", 0.25);
        }
        if (!mainConfig.contains("visual-effects.fish-animation.peak-progress")) {
            mainConfig.set("visual-effects.fish-animation.peak-progress", 0.25);
        }
        if (!mainConfig.contains("visual-effects.fish-animation.min-y-offset")) {
            mainConfig.set("visual-effects.fish-animation.min-y-offset", 0.8);
        }
        
        if (!mainConfig.contains("visual-effects.fish-animation.jump-to-head")) {
            mainConfig.set("visual-effects.fish-animation.jump-to-head.base-duration", 20);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.distance-multiplier", 5);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.max-duration", 60);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.initial-jump-height", 2.0);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.curve-height", 3.0);
            mainConfig.set("visual-effects.fish-animation.jump-to-head.easing-factor", 2.0);
        } else {
            if (!mainConfig.contains("visual-effects.fish-animation.jump-to-head.base-duration")) {
                mainConfig.set("visual-effects.fish-animation.jump-to-head.base-duration", 20);
            }
            if (!mainConfig.contains("visual-effects.fish-animation.jump-to-head.distance-multiplier")) {
                mainConfig.set("visual-effects.fish-animation.jump-to-head.distance-multiplier", 5);
            }
            if (!mainConfig.contains("visual-effects.fish-animation.jump-to-head.max-duration")) {
                mainConfig.set("visual-effects.fish-animation.jump-to-head.max-duration", 60);
            }
            if (!mainConfig.contains("visual-effects.fish-animation.jump-to-head.initial-jump-height")) {
                mainConfig.set("visual-effects.fish-animation.jump-to-head.initial-jump-height", 2.0);
            }
            if (!mainConfig.contains("visual-effects.fish-animation.jump-to-head.curve-height")) {
                mainConfig.set("visual-effects.fish-animation.jump-to-head.curve-height", 3.0);
            }
            if (!mainConfig.contains("visual-effects.fish-animation.jump-to-head.easing-factor")) {
                mainConfig.set("visual-effects.fish-animation.jump-to-head.easing-factor", 2.0);
            }
        }
        
        if (!mainConfig.contains("debug")) {
            mainConfig.set("debug.enabled", false);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "debug"));
        }
        
        if (!mainConfig.contains("language")) {
            String serverLocale = "zh_cn";
            try {
                serverLocale = "zh_cn";
            } catch (Exception e) {
                logger.warning(plugin.getMessageManager().getMessageWithoutPrefix("config_get_server_lang_failed", "获取服务器语言设置失败: %s", e.getMessage()));
            }
            
            List<String> supportedLanguages = Arrays.asList("zh_cn", "en_us");
            String defaultLanguage = "zh_cn";
            
            for (String lang : supportedLanguages) {
                if (serverLocale.startsWith(lang.substring(0, 2))) {
                    defaultLanguage = lang;
                    break;
                }
            }
            
            mainConfig.set("language.current", defaultLanguage);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s，自动检测并设置默认语言为: %s", "language", defaultLanguage));
        }
        
        if (!mainConfig.contains("update-check")) {
            mainConfig.set("update-check.enabled", true);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "update-check"));
        }
        
        if (!mainConfig.contains("database")) {
            mainConfig.set("database.type", "sqlite");
            mainConfig.set("database.sqlite.file", "data.db");
            mainConfig.set("database.mysql.host", "localhost");
            mainConfig.set("database.mysql.port", 3306);
            mainConfig.set("database.mysql.database", "kkfish");
            mainConfig.set("database.mysql.username", "root");
            mainConfig.set("database.mysql.password", "password");
            mainConfig.set("database.mysql.table-prefix", "kkfish_");
            mainConfig.set("database.mysql.use-ssl", false);
            mainConfig.set("database.mysql.timeout", 30000);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "database"));
        }
        
        if (!mainConfig.contains("seasonal")) {
            mainConfig.set("seasonal.enabled", true);
            mainConfig.set("seasonal.price-fluctuation.enabled", true);
            mainConfig.set("seasonal.price-fluctuation.spring", 0.1);
            mainConfig.set("seasonal.price-fluctuation.summer", 0.2);
            mainConfig.set("seasonal.price-fluctuation.autumn", -0.1);
            mainConfig.set("seasonal.price-fluctuation.winter", -0.2);
            mainConfig.set("seasonal.price-fluctuation.base", 0.05);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "seasonal"));
        }
        
        if (!mainConfig.contains("seasonal.price-fluctuation")) {
            mainConfig.set("seasonal.price-fluctuation.enabled", true);
            mainConfig.set("seasonal.price-fluctuation.spring", 0.1);
            mainConfig.set("seasonal.price-fluctuation.summer", 0.2);
            mainConfig.set("seasonal.price-fluctuation.autumn", -0.1);
            mainConfig.set("seasonal.price-fluctuation.winter", -0.2);
            mainConfig.set("seasonal.price-fluctuation.base", 0.05);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "seasonal.price-fluctuation"));
        }
        
        if (!mainConfig.contains("lava-fishing")) {
            mainConfig.set("lava-fishing.enabled", true);
            mainConfig.set("lava-fishing.trigger-mode", "AUTO");
            mainConfig.set("lava-fishing.bite-time-min", 100);
            mainConfig.set("lava-fishing.bite-time-max", 400);
            mainConfig.set("lava-fishing.bite-chance-multiplier", 0.8);
            mainConfig.set("lava-fishing.minigame.difficulty-multiplier", 1.2);
            mainConfig.set("lava-fishing.effects.ambient-particle", "FLAME");
            mainConfig.set("lava-fishing.effects.ambient-particle-count", 2);
            mainConfig.set("lava-fishing.effects.bite-particle", "FLAME");
            mainConfig.set("lava-fishing.effects.bite-particle-count", 20);
            mainConfig.set("lava-fishing.effects.bite-sound", "ENTITY_GENERIC_EXTINGUISH_FIRE");
            mainConfig.set("lava-fishing.effects.bite-sound-volume", 0.25);
            mainConfig.set("lava-fishing.effects.bite-sound-pitch", 1.0);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "lava-fishing"));
        }
        
        if (!mainConfig.contains("void-fishing")) {
            mainConfig.set("void-fishing.enabled", true);
            mainConfig.set("void-fishing.trigger-mode", "AUTO");
            mainConfig.set("void-fishing.end-detection.enabled", true);
            mainConfig.set("void-fishing.end-detection.countdown-ticks", 2);
            mainConfig.set("void-fishing.end-detection.require-below-player", true);
            mainConfig.set("void-fishing.bite-time-min", 120);
            mainConfig.set("void-fishing.bite-time-max", 500);
            mainConfig.set("void-fishing.bite-chance-multiplier", 0.7);
            mainConfig.set("void-fishing.minigame.difficulty-multiplier", 1.5);
            mainConfig.set("void-fishing.effects.ambient-particle", "END_ROD");
            mainConfig.set("void-fishing.effects.ambient-particle-count", 1);
            mainConfig.set("void-fishing.effects.bite-particle", "END_ROD");
            mainConfig.set("void-fishing.effects.bite-particle-count", 20);
            mainConfig.set("void-fishing.effects.bite-sound", "ITEM_TRIDENT_THUNDER");
            mainConfig.set("void-fishing.effects.bite-sound-volume", 0.25);
            mainConfig.set("void-fishing.effects.bite-sound-pitch", 1.0);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "void-fishing"));
        }
        
        if (!mainConfig.contains("styles")) {
            mainConfig.set("styles.default.progress-char", "&9=");
            mainConfig.set("styles.default.progress-bar-empty-char", "&7-");
            mainConfig.set("styles.default.green-bar-char", "&a|");
            mainConfig.set("styles.default.green-bar-edge-char", "&2|");
            mainConfig.set("styles.default.background-char", "&7|");
            mainConfig.set("styles.default.fish-indicator-char", "&9|||");
            mainConfig.set("styles.lava.progress-char", "&6=");
            mainConfig.set("styles.lava.progress-bar-empty-char", "&8-");
            mainConfig.set("styles.lava.green-bar-char", "&c▌");
            mainConfig.set("styles.lava.green-bar-edge-char", "&4▌");
            mainConfig.set("styles.lava.background-char", "&8░");
            mainConfig.set("styles.lava.fish-indicator-char", "&e|||");
            mainConfig.set("styles.void.progress-char", "&5═");
            mainConfig.set("styles.void.progress-bar-empty-char", "&8─");
            mainConfig.set("styles.void.green-bar-char", "&d▐");
            mainConfig.set("styles.void.green-bar-edge-char", "&5▐");
            mainConfig.set("styles.void.background-char", "&8░");
            mainConfig.set("styles.void.fish-indicator-char", "&b✦");
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "styles"));
        }

        if (!mainConfig.contains("item-templates")) {
            mainConfig.set("item-templates.fish-templates.default.content", "%separator%\n%name%\n%separator%\n%description%\n\n%size%\n%value%\n%rarity%\n\n%effects%\n%separator%\n%tip%");
            mainConfig.set("item-templates.fish-templates.simple.content", "%name%\n%description%\n%size%\n%value%");
            
            mainConfig.set("item-templates.rod-templates.default.content", "&6[===== 鱼竿属性 =====]\n&b│ 难度系数: %difficulty%\n&a│ 浮标区域: %float_area%\n&c│ 耐久度: %durability%\n&d│ 充能速度: %charge_speed%\n&d│ 咬钩几率加成: %bite_rate_bonus%\n&6[====================]\n \n%tip%");
            mainConfig.set("item-templates.rod-templates.compact.content", "%name%\n%description%\n难度系数: %difficulty%\n浮标区域: %float_area%\n耐久度: %durability%");
            
            mainConfig.set("item-templates.hook-templates.default.content", "&7等级: %level%\n%rarity_color%稀有度: %rarity_name%\n%description%\n&7--- 性能指标 ---%performance_metrics%\n--- 状态信息 ---%status_info%\n%equipment_info%");
            mainConfig.set("item-templates.hook-templates.simple.content", "%name%\n%description%\n等级: %level%\n稀有度: %rarity_name%");
            
            mainConfig.set("item-templates.bait-templates.default.content", "&a[===== 鱼饵属性 =====]\n&b名称: %name%\n&a效果: %effects%\n&c描述: %description%\n&a[====================]");
            mainConfig.set("item-templates.bait-templates.compact.content", "%name%\n%description%\n%effects%");
            
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "item-templates"));
        }
        
        if (!mainConfig.contains("broadcast")) {
            mainConfig.set("broadcast.enabled", true);
            mainConfig.set("broadcast.range", "global");
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_main", "添加缺失的主配置: %s", "broadcast"));
        }

    }
    
    private void checkAndAddFishConfigDefaults() {
        if (!fishConfig.contains("fish")) {
            fishConfig.set("fish.example-fish.display-name", "&e示例鱼");
            fishConfig.set("fish.example-fish.material", "COD");
            fishConfig.set("fish.example-fish.rarity", 1);
            fishConfig.set("fish.example-fish.value", 10.0);
            fishConfig.set("fish.example-fish.exp", 5);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_fish", "添加缺失的鱼类配置: %s", "example-fish"));
        }
    }
    
    private void checkAndAddRodConfigDefaults() {
        if (!rodConfig.contains("rods")) {
            rodConfig.set("rods.example-rod.display-name", "&f示例鱼竿");
            rodConfig.set("rods.example-rod.material", "FISHING_ROD");
            rodConfig.set("rods.example-rod.difficulty", 1.0);
            rodConfig.set("rods.example-rod.float-area-size", 3);
            rodConfig.set("rods.example-rod.durability", 50);
            rodConfig.set("rods.example-rod.charge-speed", 1.0);
            rodConfig.set("rods.example-rod.bite-rate-bonus", 0.0);
            rodConfig.set("rods.example-rod.custom-model-data", 0);
            rodConfig.set("rods.example-rod.effects", new ArrayList<>());
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_rod", "添加缺失的鱼竿配置: %s", "example-rod"));
        }
    }
    
    private void checkAndAddBaitConfigDefaults() {
        if (!baitConfig.contains("baits")) {
            baitConfig.set("baits.example-bait.display-name", "&a示例鱼饵");
            baitConfig.set("baits.example-bait.material", "MAGMA_CREAM");
            baitConfig.set("baits.example-bait.effect", "none");
            baitConfig.set("baits.example-bait.effect-value", 0.0);
            baitConfig.set("baits.example-bait.effects", new ArrayList<>());
            baitConfig.set("baits.example-bait.effect-values", new java.util.HashMap<String, Object>());
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_bait", "添加缺失的鱼饵配置: %s", "example-bait"));
        }
    }
    
    private void checkAndAddSoundConfigDefaults() {
        if (!soundConfig.contains("settings")) {
            soundConfig.set("settings.enabled", true);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_sound", "添加缺失的音效配置: %s", "settings"));
        }
        
        if (!soundConfig.contains("cast")) {
            soundConfig.set("cast.enabled", true);
            soundConfig.set("cast.name", "ENTITY_FISHING_BOBBER_THROW");
            soundConfig.set("cast.volume", 1.0);
            soundConfig.set("cast.pitch", 1.0);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_sound", "添加缺失的音效配置: %s", "cast"));
        }
        
        if (!soundConfig.contains("bite_hint")) {
            soundConfig.set("bite_hint.enabled", true);
            soundConfig.set("bite_hint.name", "ENTITY_FISHING_BOBBER_SPLASH");
            soundConfig.set("bite_hint.volume", 0.8);
            soundConfig.set("bite_hint.pitch", 1.2);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_sound", "添加缺失的音效配置: %s", "bite_hint"));
        }
        
        if (!soundConfig.contains("success")) {
            soundConfig.set("success.enabled", true);
            soundConfig.set("success.name", "ENTITY_PLAYER_LEVELUP");
            soundConfig.set("success.volume", 1.0);
            soundConfig.set("success.pitch", 1.3);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_sound", "添加缺失的音效配置: %s", "success"));
        }
        
        if (!soundConfig.contains("fail")) {
            soundConfig.set("fail.enabled", true);
            soundConfig.set("fail.name", "ENTITY_VILLAGER_NO");
            soundConfig.set("fail.volume", 0.8);
            soundConfig.set("fail.pitch", 0.9);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_sound", "添加缺失的音效配置: %s", "fail"));
        }
        
        if (!soundConfig.contains("minigame")) {
            soundConfig.set("minigame.enabled", true);
            soundConfig.set("minigame.name", "UI_BUTTON_CLICK");
            soundConfig.set("minigame.volume", 0.5);
            soundConfig.set("minigame.pitch", 1.0);
            logger.info(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_sound", "添加缺失的音效配置: %s", "minigame"));
        }
    }

    public FileConfiguration getPoolConfig() {
        return poolConfig;
    }

    public List<String> getPoolFish(WaterType waterType) {
        String key = waterType.name().toLowerCase();
        if (poolConfig.contains(key)) {
            return poolConfig.getStringList(key);
        }
        return new ArrayList<>();
    }

    public List<String> getAvailableFishFromPool(WaterType waterType, String biome, String weather, long time, String season) {
        List<String> poolFish = getPoolFish(waterType);
        if (poolFish.isEmpty()) {
            return getAvailableFish(biome, weather, time, season);
        }

        List<String> availableFish = new ArrayList<>();
        for (String fishName : poolFish) {
            if (fishExists(fishName)) {
                boolean canSpawn = true;

                if (fishConfig.contains("fish." + fishName + ".biomes")) {
                    List<String> biomes = fishConfig.getStringList("fish." + fishName + ".biomes");
                    if (!biomes.isEmpty() && !biomes.contains(biome)) {
                        canSpawn = false;
                    }
                }

                if (canSpawn && fishConfig.contains("fish." + fishName + ".weather")) {
                    List<String> weathers = fishConfig.getStringList("fish." + fishName + ".weather");
                    if (!weathers.isEmpty() && !weathers.contains(weather)) {
                        canSpawn = false;
                    }
                }

                if (canSpawn && fishConfig.contains("fish." + fishName + ".time")) {
                    List<String> timeRanges = fishConfig.getStringList("fish." + fishName + ".time");
                    boolean timeMatch = false;
                    for (String range : timeRanges) {
                        if (range.equalsIgnoreCase("ANY") || isTimeInRange(time, range)) {
                            timeMatch = true;
                            break;
                        }
                    }
                    if (!timeRanges.isEmpty() && !timeMatch) {
                        canSpawn = false;
                    }
                }

                if (canSpawn && isSeasonalFishingEnabled() && season != null && fishConfig.contains("fish." + fishName + ".seasons")) {
                    List<String> seasons = fishConfig.getStringList("fish." + fishName + ".seasons");
                    if (!seasons.isEmpty() && !seasons.contains(season)) {
                        canSpawn = false;
                    }
                }

                if (canSpawn) {
                    availableFish.add(fishName);
                }
            }
        }

        if (availableFish.isEmpty()) {
            return getAvailableFish(biome, weather, time, season);
        }

        return availableFish;
    }

    public boolean isLavaFishingEnabled() {
        return mainConfig.getBoolean("lava-fishing.enabled", true);
    }

    public String getLavaTriggerMode() {
        return mainConfig.getString("lava-fishing.trigger-mode", "AUTO");
    }

    public int getLavaBiteTimeMin() {
        return mainConfig.getInt("lava-fishing.bite-time-min", 100);
    }

    public int getLavaBiteTimeMax() {
        return mainConfig.getInt("lava-fishing.bite-time-max", 400);
    }

    public double getLavaBiteChanceMultiplier() {
        return mainConfig.getDouble("lava-fishing.bite-chance-multiplier", 0.8);
    }

    public double getLavaDifficultyMultiplier() {
        return mainConfig.getDouble("lava-fishing.minigame.difficulty-multiplier", 1.2);
    }

    public String getLavaAmbientParticle() {
        return mainConfig.getString("lava-fishing.effects.ambient-particle", "FLAME");
    }

    public int getLavaAmbientParticleCount() {
        return mainConfig.getInt("lava-fishing.effects.ambient-particle-count", 2);
    }

    public String getLavaBiteParticle() {
        return mainConfig.getString("lava-fishing.effects.bite-particle", "FLAME");
    }

    public int getLavaBiteParticleCount() {
        return mainConfig.getInt("lava-fishing.effects.bite-particle-count", 20);
    }

    public String getLavaBiteSound() {
        return mainConfig.getString("lava-fishing.effects.bite-sound", "ENTITY_GENERIC_EXTINGUISH_FIRE");
    }

    public float getLavaBiteSoundVolume() {
        return (float) mainConfig.getDouble("lava-fishing.effects.bite-sound-volume", 0.25);
    }

    public float getLavaBiteSoundPitch() {
        return (float) mainConfig.getDouble("lava-fishing.effects.bite-sound-pitch", 1.0);
    }

    public boolean isVoidFishingEnabled() {
        return mainConfig.getBoolean("void-fishing.enabled", true);
    }

    public String getVoidTriggerMode() {
        return mainConfig.getString("void-fishing.trigger-mode", "AUTO");
    }

    public boolean isEndDetectionEnabled() {
        return mainConfig.getBoolean("void-fishing.end-detection.enabled", true);
    }

    public int getEndCountdownTicks() {
        return mainConfig.getInt("void-fishing.end-detection.countdown-ticks", 2);
    }

    public boolean isEndRequireBelowPlayer() {
        return mainConfig.getBoolean("void-fishing.end-detection.require-below-player", true);
    }

    public int getVoidBiteTimeMin() {
        return mainConfig.getInt("void-fishing.bite-time-min", 120);
    }

    public int getVoidBiteTimeMax() {
        return mainConfig.getInt("void-fishing.bite-time-max", 500);
    }

    public double getVoidBiteChanceMultiplier() {
        return mainConfig.getDouble("void-fishing.bite-chance-multiplier", 0.7);
    }

    public double getVoidDifficultyMultiplier() {
        return mainConfig.getDouble("void-fishing.minigame.difficulty-multiplier", 1.5);
    }

    public String getVoidAmbientParticle() {
        return mainConfig.getString("void-fishing.effects.ambient-particle", "END_ROD");
    }

    public int getVoidAmbientParticleCount() {
        return mainConfig.getInt("void-fishing.effects.ambient-particle-count", 1);
    }

    public String getVoidBiteParticle() {
        return mainConfig.getString("void-fishing.effects.bite-particle", "END_ROD");
    }

    public int getVoidBiteParticleCount() {
        return mainConfig.getInt("void-fishing.effects.bite-particle-count", 20);
    }

    public String getVoidBiteSound() {
        return mainConfig.getString("void-fishing.effects.bite-sound", "ITEM_TRIDENT_THUNDER");
    }

    public float getVoidBiteSoundVolume() {
        return (float) mainConfig.getDouble("void-fishing.effects.bite-sound-volume", 0.25);
    }

    public float getVoidBiteSoundPitch() {
        return (float) mainConfig.getDouble("void-fishing.effects.bite-sound-pitch", 1.0);
    }

    public double getRarityValueMultiplier(String rarityName) {
        return mainConfig.getDouble("fishing-settings.rarity." + rarityName + ".value-multiplier", 1.0);
    }

    public double getRarityAuraSkillsXpMultiplier(String rarityName) {
        return mainConfig.getDouble("fishing-settings.rarity." + rarityName + ".aura-skills-xp-multiplier", 1.0);
    }

    public String getRarityDisplayName(String rarityName) {
        return mainConfig.getString("fishing-settings.rarity." + rarityName + ".display-name", rarityName);
    }

    public String getRarityNameByLevel(String levelName) {
        if (levelName == null) return "common";
        levelName = levelName.toLowerCase();
        if (levelName.contains("legendary")) return "legendary";
        if (levelName.contains("epic")) return "epic";
        if (levelName.contains("rare")) return "rare";
        if (levelName.contains("uncommon")) return "uncommon";
        return "common";
    }

    public int getRarityOrder(String rarityName) {
        switch (rarityName) {
            case "legendary": return 5;
            case "epic": return 4;
            case "rare": return 3;
            case "uncommon": return 2;
            default: return 1;
        }
    }
}