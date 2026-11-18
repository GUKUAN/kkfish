package me.kkfish.managers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.kkfish.competition.CompetitionUtils;
import me.kkfish.fishing.WaterType;
import me.kkfish.kkfish;
import me.kkfish.handlers.AuraSkills;
import me.kkfish.managers.ItemValue;
/**
 * TODO: 拆分 —— 2000+行，管理8种配置文件+稀有度计算+鱼池逻辑+季节价格+权限检查
 *       计划拆为：Config(编排加载) + FishCfg + RodCfg + HookCfg + BaitCfg + CompeteCfg + PoolCfg
 *       分步进行：1)抽取HookCfg(最多getter) 2)抽取FishCfg 3)剩余逐步迁移
 */
import me.kkfish.utils.ConfigUpgrade;
import me.kkfish.utils.XSeriesUtil;
import me.kkfish.utils.NBTUtil;
import me.kkfish.integrations.CustomItemHook;

public class Config {

    private static final String CONFIG_VERSION = "1.7.10";

    private final kkfish plugin;
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
    private final Map<WaterType, List<String>> validPoolFishCache = new EnumMap<>(WaterType.class);
    private final Map<String, String> hookPathCache = new ConcurrentHashMap<>();
    private ItemValue itemValue;

    // 支持的语言组，对应 resources/config_packs/<lang>/ 目录
    // 扩展新语言步骤：1)此列表追加 2)detectServerLang()加映射 3)建config_packs/<lang>/目录
    private static final List<String> SUPPORTED_LANGS = Arrays.asList("zh_cn", "en_us");

    // 首次启动检测结果暂存，待mainConfig加载后写入auto-detected字段
    private String pendingAutoDetected = null;

    public Config(kkfish plugin) {
        this.plugin = plugin;
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

    public boolean isPlayerPointsEconomyEnabled() {
        return mainConfig.getBoolean("economy.playerpoints", true);
    }

    public String getPrimaryEconomy() {
        return mainConfig.getString("economy.primary", "vault");
    }

    public boolean isEconomyFallbackEnabled() {
        return mainConfig.getBoolean("economy.fallback", true);
    }

    public boolean isSellEnabled() {
        return mainConfig.getBoolean("economy.sell", true);
    }

    public boolean isSellGuiEnabled() {
        return mainConfig.getBoolean("economy.sellgui", true);
    }

    private void initializeConfigs() {
        // 首次启动检测：在释放配置前判断服务器语言环境，按语言释放对应 pack
        detectAndApplyLanguage();

        mainConfigFile = new File(plugin.getDataFolder(), "config.yml");
        mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile);

        // 写入首次启动检测结果标记
        if (pendingAutoDetected != null) {
            mainConfig.set("auto-detected", pendingAutoDetected);
            try {
                mainConfig.save(mainConfigFile);
            } catch (IOException e) {
                kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix(
                    "log.config_save_failed", "§c无法保存配置文件") + e.getMessage());
            }
            pendingAutoDetected = null;
        }

        File fishFile = new File(plugin.getDataFolder(), "fish.yml");
        fishConfig = YamlConfiguration.loadConfiguration(fishFile);



        File rodFile = new File(plugin.getDataFolder(), "rods.yml");
        rodConfig = YamlConfiguration.loadConfiguration(rodFile);

        baitFile = new File(plugin.getDataFolder(), "baits.yml");
        baitConfig = YamlConfiguration.loadConfiguration(baitFile);
        
        competeConfigFile = new File(plugin.getDataFolder(), "compete.yml");
        competeConfig = YamlConfiguration.loadConfiguration(competeConfigFile);
        
        hookConfigFile = new File(plugin.getDataFolder(), "hooks.yml");
        hookConfig = YamlConfiguration.loadConfiguration(hookConfigFile);
        
        soundConfigFile = new File(plugin.getDataFolder(), "sounds.yml");
        soundConfig = YamlConfiguration.loadConfiguration(soundConfigFile);

        poolConfigFile = new File(plugin.getDataFolder(), "pools.yml");
        poolConfig = YamlConfiguration.loadConfiguration(poolConfigFile);
        reloadPoolCache();
        rebuildHookPathCache();
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
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.config_save_failed", "§c无法保存配置文件")); e.printStackTrace();
        }
    }

    public void reloadConfigs() {
        mainConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        fishConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "fish.yml"));

        rodConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "rods.yml"));
        hookConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "hooks.yml"));
        baitConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "baits.yml"));
        competeConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "compete.yml"));
        soundConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "sounds.yml"));
        poolConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "pools.yml"));
        reloadPoolCache();
        rebuildHookPathCache();
    }

    // ==================== 语言组自动检测与应用 ====================

    /**
     * 根据JVM环境推断服务器语言
     * 默认en_us，locale或时区命中中国则返回zh_cn
     * 扩展新语言时在此添加映射规则
     */
    private String detectServerLang() {
        String country = Locale.getDefault().getCountry();
        String lang = Locale.getDefault().getLanguage();

        // 时区信号：中国大陆/港澳台
        String tz = TimeZone.getDefault().getID();
        boolean chinaTz = tz.equals("Asia/Shanghai") || tz.equals("Asia/Chongqing")
                       || tz.equals("Asia/Harbin") || tz.equals("Asia/Urumqi")
                       || tz.equals("Asia/Hong_Kong") || tz.equals("Asia/Taipei")
                       || tz.equals("Asia/Macau") || tz.equals("CTT");

        // locale信号：国家码或语言码
        boolean chinaLocale = "CN".equals(country) || "HK".equals(country)
                           || "TW".equals(country) || "MO".equals(country)
                           || "zh".equals(lang);

        if (chinaTz || chinaLocale) {
            return "zh_cn";
        }
        return "en_us";
    }

    /**
     * 首次启动检测：在释放默认配置前调用
     * 已检测过(config.yml含auto-detected字段)则跳过
     */
    private void detectAndApplyLanguage() {
        File mainFile = new File(plugin.getDataFolder(), "config.yml");
        if (mainFile.exists()) {
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(mainFile);
            if (existing.contains("auto-detected")) {
                String detected = existing.getString("auto-detected", "none");
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix(
                    "auto_detect_skip", "§7跳过自动检测（已检测过：%s）", detected));
                return;
            }
        }

        String langCode = detectServerLang();
        if ("en_us".equals(langCode)) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix(
                "auto_detect_default", "§a检测到非中国服务器环境，正在应用英文配置组。"));
        } else {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix(
                "auto_detect_china", "§a检测到中国服务器环境，正在应用中文配置组..."));
        }
        try {
            // 首次启动模式：仅对不存在的文件释放
            copyConfigPack(langCode, false);
        } catch (IOException e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix(
                "log.config_pack_copy_failed", "复制配置组失败: %s", e.getMessage()));
        }

        this.pendingAutoDetected = langCode;
    }

    /**
     * 从jar内config_packs/<langCode>/释放配置到插件数据目录
     * @param langCode 语言代码，必须在SUPPORTED_LANGS中
     * @param force true=强制覆盖（用于resellang），false=仅对不存在的文件释放（首次启动）
     */
    private void copyConfigPack(String langCode, boolean force) throws IOException {
        if (!SUPPORTED_LANGS.contains(langCode)) {
            throw new IllegalArgumentException("Unsupported language: " + langCode);
        }

        String[] mainFiles = {"config.yml", "fish.yml", "rods.yml", "baits.yml",
                              "hooks.yml", "pools.yml", "compete.yml", "sounds.yml"};
        String[] guiFiles = {"main_menu.yml", "fish_dex.yml", "fish_record.yml",
                             "help_gui.yml", "hook_material.yml", "competition_category.yml",
                             "reward_preview.yml", "rod_shop.yml", "sell_gui.yml"};

        String packPrefix = "config_packs/" + langCode + "/";

        // 释放主配置和物品配置
        // saveResource会保持jar内目录结构，先释放到临时位置再复制覆盖
        for (String f : mainFiles) {
            File target = new File(plugin.getDataFolder(), f);
            if (!force && target.exists()) {
                continue;
            }
            plugin.saveResource(packPrefix + f, true);
            File source = new File(plugin.getDataFolder(), packPrefix + f);
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // GUI配置
        File guiDir = new File(plugin.getDataFolder(), "gui");
        if (!guiDir.exists()) {
            guiDir.mkdirs();
        }
        for (String f : guiFiles) {
            File target = new File(guiDir, f);
            if (!force && target.exists()) {
                continue;
            }
            plugin.saveResource(packPrefix + "gui/" + f, true);
            File source = new File(plugin.getDataFolder(), packPrefix + "gui/" + f);
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        // 清理临时释放的config_packs目录
        deleteDir(new File(plugin.getDataFolder(), "config_packs"));
    }

    /**
     * 强制应用指定语言组（/kf resellang调用）
     * 会先备份现有配置（如果backup.enabled=true）
     * @return true表示成功
     */
    public boolean forceApplyLanguagePack(String langCode) {
        if (!SUPPORTED_LANGS.contains(langCode)) {
            return false;
        }
        try {
            // 备份
            if (mainConfig.getBoolean("backup.enabled", true)) {
                backupConfigs();
            }

            copyConfigPack(langCode, true);
            reloadConfigs();

            mainConfig.set("auto-detected", langCode);
            saveConfigs();

            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix(
                "log.resellang_applied", "已强制应用语言组: %s", langCode));
            return true;
        } catch (Exception e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix(
                "log.resellang_apply_failed", "强制应用语言组失败: %s", e.getMessage()));
            return false;
        }
    }

    /**
     * 备份现有配置到backup_<时间戳>/目录
     */
    private void backupConfigs() throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backupDir = new File(plugin.getDataFolder(), "backup_" + ts);
        backupDir.mkdirs();

        String[] mainFiles = {"config.yml", "fish.yml", "rods.yml", "baits.yml",
                              "hooks.yml", "pools.yml", "compete.yml", "sounds.yml"};
        for (String f : mainFiles) {
            File src = new File(plugin.getDataFolder(), f);
            if (src.exists()) {
                Files.copy(src.toPath(), new File(backupDir, f).toPath());
            }
        }

        File guiDir = new File(plugin.getDataFolder(), "gui");
        if (guiDir.exists()) {
            File backupGui = new File(backupDir, "gui");
            backupGui.mkdirs();
            File[] guiFiles = guiDir.listFiles();
            if (guiFiles != null) {
                for (File gf : guiFiles) {
                    if (gf.isFile()) {
                        Files.copy(gf.toPath(), new File(backupGui, gf.getName()).toPath());
                    }
                }
            }
        }

        cleanExpiredBackups();

        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix(
            "log.backup_created", "已备份现有配置到: %s", backupDir.getName()));
    }

    /**
     * 清理过期备份目录
     * 保留时长由config.yml的backup.retention-seconds控制，-1表示永不清理
     */
    private void cleanExpiredBackups() {
        int retentionSec = mainConfig.getInt("backup.retention-seconds", 604800);
        if (retentionSec < 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long retentionMs = retentionSec * 1000L;
        // 保留时长用比赛时间工具格式化显示
        String retentionDisplay = CompetitionUtils.formatDuration(retentionSec, plugin.getMessageManager());

        File[] files = plugin.getDataFolder().listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory() && f.getName().startsWith("backup_")) {
                long age = now - f.lastModified();
                if (age > retentionMs) {
                    deleteDir(f);
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix(
                        "log.backup_expired_removed", "已清理过期备份: %s（保留时长: %s）",
                        f.getName(), retentionDisplay));
                }
            }
        }
    }

    /** 递归删除目录 */
    private void deleteDir(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c.isDirectory()) {
                    deleteDir(c);
                } else {
                    c.delete();
                }
            }
        }
        dir.delete();
    }

    /** 获取支持的语言列表（供Cmd做tab补全） */
    public List<String> getSupportedLangs() {
        return new ArrayList<>(SUPPORTED_LANGS);
    }

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public FileConfiguration getFishConfig() {
        return fishConfig;
    }

    public String getRodType(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        Object rodId = NBTUtil.getNBTData(item, "kkfish_rod");
        if (rodId != null && rodExists(rodId.toString())) {
            return "default_rod".equals(rodId.toString()) ? "default" : rodId.toString();
        }
        ItemMeta meta = item.getItemMeta();
        String displayName = meta == null ? null : meta.getDisplayName();
        ConfigurationSection rodsSection = rodConfig.getConfigurationSection("rods");
        if (rodsSection != null && displayName != null && !displayName.isEmpty()) {
            for (String rodName : rodsSection.getKeys(false)) {
                String name = rodConfig.getString(getRodPropertyPath(rodName, "display-name"), rodName);
                name = CustomItemHook.replaceFontImages(ChatColor.translateAlternateColorCodes('&', name));
                if (displayName.equals(name)) {
                    String material = rodConfig.getString(getRodPropertyPath(rodName, "material"), "FISHING_ROD");
                    if (CustomItemHook.isCustomItemStr(material) || item.getType() == XSeriesUtil.parseMaterial(material)) return rodName;
                }
            }
        }
        if (item.getType() == Material.FISHING_ROD || NBTUtil.hasTag(item, "自定义鱼竿")) {
            return "default";
        }
        return null;
    }

    public ItemStack getPlayerRod(Player player) {
        if (player == null) return null;
        ItemStack main = player.getInventory().getItemInMainHand();
        if (getRodType(main) != null) return main;
        ItemStack off = player.getInventory().getItemInOffHand();
        return getRodType(off) != null ? off : null;
    }

    public String getItemConfigId(ItemStack item, String group, String key) {
        if (item == null || !item.hasItemMeta()) return null;
        FileConfiguration config = "fish".equals(group) ? fishConfig : baitConfig;
        Object id = NBTUtil.getNBTData(item, key);
        if (id != null && config.isConfigurationSection(group + "." + id)) return id.toString();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return null;
        String display = meta.getDisplayName();
        ConfigurationSection section = config.getConfigurationSection(group);
        if (section == null) return null;
        for (String name : section.getKeys(false)) {
            String configured = config.getString(group + "." + name + ".display-name", name);
            configured = CustomItemHook.replaceFontImages(ChatColor.translateAlternateColorCodes('&', configured));
            if (display.equals(configured) || ChatColor.stripColor(display).equals(ChatColor.stripColor(configured))) return name;
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
                        if (matchesCondition(allowedSeasons, season)) {
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
        Map<String, Double> weights = getFishLevelWeights(fishName);
        String primary = null;
        double largest = 0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() > largest) {
                largest = entry.getValue();
                primary = entry.getKey();
            }
        }
        return primary == null ? fishConfig.getInt("fish." + fishName + ".rarity", 1) : getRarityOrder(primary);
    }
    
    public List<String> getFishRarityNames(String fishName) {
        List<String> rarityNames = new ArrayList<>();
        
        if (fishConfig.contains("fish." + fishName + ".level")) {
            Map<String, Double> levels = getFishLevelWeights(fishName);
            
            String mainRarity = "common";
            double maxWeight = 0;
            
            for (Map.Entry<String, Double> entry : levels.entrySet()) {
                String levelName = entry.getKey();
                double weight = entry.getValue();
                if (weight > maxWeight) {
                    maxWeight = weight;
                    mainRarity = levelName;
                }
                if (weight > 0) {
                    rarityNames.add(levelName);
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
        double activity = fishConfig.getDouble("fish." + fishName + ".activity", 1.0);
        if (!Double.isFinite(activity) || activity <= 0) return 1.0;
        return Math.max(0.1, Math.min(activity, 10.0));
    }

    public String getRandomFishLevel(String fishName) {
        return getRandomFishLevel(fishName, null);
    }
    
    public String getRandomFishLevel(String fishName, Player player) {
        return getRandomFishLevel(fishName, player, 1.0, new Random());
    }

    public Map<String, Double> getFishLevelWeights(String fishName) {
        Map<String, Double> weights = new LinkedHashMap<>();
        Object raw = fishConfig.get("fish." + fishName + ".level");
        List<?> entries = raw instanceof List ? (List<?>) raw : Arrays.asList(raw);
        for (Object entry : entries) {
            if (entry instanceof ConfigurationSection) entry = ((ConfigurationSection) entry).getValues(false);
            if (entry instanceof Map) {
                for (Map.Entry<?, ?> pair : ((Map<?, ?>) entry).entrySet()) {
                    addLevelWeight(weights, pair.getKey(), pair.getValue(), fishName);
                }
            } else if (entry instanceof String) {
                String text = ((String) entry).trim();
                int separator = text.lastIndexOf(':');
                addLevelWeight(weights, separator < 0 ? text : text.substring(0, separator),
                        separator < 0 ? 1 : text.substring(separator + 1), fishName);
            }
        }
        return weights;
    }

    private void addLevelWeight(Map<String, Double> weights, Object name, Object value, String fishName) {
        if (name == null || value == null) return;
        try {
            double weight = Double.parseDouble(value.toString());
            String id = getRarityNameByLevel(name.toString());
            if (!Double.isFinite(weight) || weight < 0 || id.isEmpty()) throw new NumberFormatException();
            weights.put(id, weights.getOrDefault(id, 0.0) + weight);
        } catch (NumberFormatException e) {
            kkfish.log("§e鱼 " + fishName + " 的等级权重无效: " + name + "=" + value);
        }
    }

    public String getRandomFishLevel(String fishName, Player player, double rareMultiplier, Random random) {
        try {
            if (fishConfig.contains("fish." + fishName + ".level")) {
                Map<String, Double> levels = getFishLevelWeights(fishName);
                
                if (levels.isEmpty()) {
                    return null;
                }
                
                double totalWeight = 0;
                List<Map.Entry<String, Double>> weightedEntries = new ArrayList<>();
                AuraSkills auraSkills = player == null ? null : plugin.getAuraSkills();
                double skillBonus = auraSkills != null && auraSkills.isAuraSkillsEnabled()
                        ? auraSkills.getRareFishBonus(auraSkills.getFishingLevel(player)) : 1.0;
                
                for (Map.Entry<String, Double> entry : levels.entrySet()) {
                    String levelName = entry.getKey();
                    double finalWeight = entry.getValue() * Math.max(0, getGlobalLevelWeight(levelName));
                    if (getRarityOrder(levelName) >= 3) finalWeight *= Math.max(0, rareMultiplier) * skillBonus;
                    if (finalWeight <= 0 || !Double.isFinite(finalWeight)) continue;
                    weightedEntries.add(new AbstractMap.SimpleEntry<>(levelName, finalWeight));
                    totalWeight += finalWeight;
                }
                if (totalWeight <= 0 || weightedEntries.isEmpty()) {
                    return null;
                }
                
                double randomValue = random.nextDouble() * totalWeight;
                double currentWeight = 0;
                
                for (Map.Entry<String, Double> entry : weightedEntries) {
                    currentWeight += entry.getValue();
                    if (randomValue < currentWeight) return entry.getKey();
                }
                return weightedEntries.get(weightedEntries.size() - 1).getKey();
            }
            int rarity = fishConfig.getInt("fish." + fishName + ".rarity", 1);
            return rarity == 5 ? "legendary" : rarity == 4 ? "epic" : rarity == 3 ? "rare" : rarity == 2 ? "uncommon" : "common";
        } catch (Exception e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("config_fish_level_calc_error", "计算钓鱼等级时出错: %s", e.getMessage()));
            return null;
        }
    }
    
    private double getGlobalLevelWeight(String levelName) {
        if (levelName.contains(":")) {
            levelName = levelName.split(":")[0];
        }
        
        levelName = levelName.toLowerCase();
        
        return mainConfig.getDouble("fishing-settings.rarity." + levelName + ".weight", 50);
    }

    public boolean rodExists(String rodName) {
        return rodName != null && rodConfig.isConfigurationSection("rods." + ("default_rod".equals(rodName) ? "default" : rodName));
    }

    public String getRodPropertyPath(String rodName, String field) {
        String id = "default_rod".equals(rodName) ? "default" : rodName;
        String path = "rods." + id + "." + field;
        if (rodConfig.contains(path)) return path;
        int separator = field.indexOf('.');
        while (separator > 0) {
            String parent = "rods." + id + "." + field.substring(0, separator);
            if (rodConfig.contains(parent) && !rodConfig.isConfigurationSection(parent)) return path;
            separator = field.indexOf('.', separator + 1);
        }
        return "rods.default." + field;
    }

    public double getRodDifficulty(String rodName) {
        return rodConfig.getDouble(getRodPropertyPath(rodName, "difficulty"), 1.0);
    }

    public int getRodFloatAreaSize(String rodName) {
        return rodConfig.getInt(getRodPropertyPath(rodName, "float-area-size"), 30);
    }

    public boolean baitExists(String baitName) {
        return baitConfig.contains("baits." + baitName);
    }

    public String getBaitEffect(String baitName) {
        if (baitConfig.contains("baits." + baitName + ".effects")) {
            List<String> effects = getBaitEffects(baitName);
            return effects.isEmpty() ? "none" : effects.get(0);
        }
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
        if (baitName == null || !getBaitEffects(baitName).contains(effectType)) return 0.0;
        if (baitConfig.contains("baits." + baitName + ".effect-values")) {
            Object valueObj = baitConfig.get("baits." + baitName + ".effect-values." + effectType);
            if (valueObj instanceof Number) {
                return ((Number) valueObj).doubleValue();
            }
        }
        if (!baitConfig.contains("baits." + baitName + ".effects") && effectType.equals(getBaitEffect(baitName))) {
            return getBaitEffectValue(baitName);
        }
        return 0.0;
    }

    /**
     * 鱼饵的诱鱼速度加成，effect 类型为 lure。
     */
    public double getBaitLureSpeed(String baitName) {
        double value = getBaitEffectValueByName(baitName, "lure");
        return Double.isFinite(value) && value > 0 ? value : 0.0;
    }
    
    public List<String> getAllBaitNames() {
        List<String> baitNames = new ArrayList<>();
        if (baitConfig.contains("baits")) {
            ConfigurationSection baitsSection = baitConfig.getConfigurationSection("baits");
            if (baitsSection != null) {
                baitNames.addAll(baitsSection.getKeys(false));
            }
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
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.no_baits_config", "No bait configurations found, no permission groups needed~"));
            return;
        }
        
        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.generate_baits_start", "Starting automatic generation of bait permission groups..."));
        
        for (String baitName : allBaitNames) {
            List<String> perms = new ArrayList<>();
            
            perms.add("kkfish.baits.use." + baitName);
            
            if (baitName.contains("钻石") || baitName.contains("diamond")) {
                perms.add("kkfish.baits.diamond");
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.generate_bait_diamond", "Automatically added permission group for diamond bait '%s'").replace("%s", baitName));
            } else if (baitName.contains("魔法") || baitName.contains("magic")) {
                perms.add("kkfish.baits.magic");
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.generate_bait_magic", "Automatically added permission group for magic bait '%s'").replace("%s", baitName));
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
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.generate_baits_complete", "Bait permission group generation complete, saved to baits.yml!"));
        } catch (Exception e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.save_baits_failed", "Error saving bait permission configuration: ") + e.getMessage());
        }
    }

    public List<String> getAvailableFish(String biome, String weather, long time) {
        return getAvailableFish(biome, weather, time, null);
    }
    
    private List<String> filterFishByConditions(List<String> fishNames, String biome, String weather, long time, String season) {
        List<String> result = new ArrayList<>();
        for (String fishName : fishNames) {
            if (!fishExists(fishName)) continue;
            if (!hasAvailableFishLevel(fishName)) continue;
            boolean canSpawn = true;

            if (fishConfig.contains("fish." + fishName + ".biomes")) {
                List<String> biomes = fishConfig.getStringList("fish." + fishName + ".biomes");
                if (!matchesCondition(biomes, biome)) {
                    canSpawn = false;
                }
            }

            if (canSpawn && fishConfig.contains("fish." + fishName + ".weather")) {
                List<String> weathers = fishConfig.getStringList("fish." + fishName + ".weather");
                if (!matchesCondition(weathers, weather)) {
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
                if (!matchesCondition(seasons, season)) {
                    canSpawn = false;
                }
            }

            if (canSpawn) {
                result.add(fishName);
            }
        }
        return result;
    }

    private boolean matchesCondition(List<String> values, String actual) {
        if (values.isEmpty() || actual == null) return true;
        for (String value : values) {
            if ("ALL".equalsIgnoreCase(value) || "ANY".equalsIgnoreCase(value) || actual.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    public boolean hasAvailableFishLevel(String fishName) {
        if (!fishConfig.contains("fish." + fishName + ".level")) return true;
        for (Map.Entry<String, Double> entry : getFishLevelWeights(fishName).entrySet()) {
            double weight = entry.getValue() * getGlobalLevelWeight(entry.getKey());
            if (weight > 0 && Double.isFinite(weight)) return true;
        }
        return false;
    }

    public List<String> getAvailableFish(String biome, String weather, long time, String season) {
        if (!fishConfig.contains("fish")) {
            return new ArrayList<>();
        }
        ConfigurationSection fishSection = fishConfig.getConfigurationSection("fish");
        if (fishSection == null) {
            return new ArrayList<>();
        }
        return filterFishByConditions(new ArrayList<>(fishSection.getKeys(false)), biome, weather, time, season);
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
        return rodConfig.getString(getRodPropertyPath(rodName, "template"), "default");
    }
    
    public String getHookTemplate(String templateName) {
        if (mainConfig.contains("item-templates.hook-templates." + templateName + ".content")) {
            String content = mainConfig.getString("item-templates.hook-templates." + templateName + ".content", "");
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', content);
        }
        return "%name%\n%description%\n%durability%\n%features%";
    }
    
    public String getHookTemplateName(String hookName) {
        String path = getHookConfigPath(hookName);
        if (path == null) {
            return "default";
        }
        return hookConfig.getString(path + ".template", "default");
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
        return mainConfig.getBoolean("debug.enabled", false);
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
        return isEconomyEnabled() && isEconomySystemEnabled();
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
        mainConfig.set("debug.enabled", debugMode);
        saveConfigs();
        if (debugMode) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("config_debug_enabled", "调试模式已开启！将显示详细的钓鱼过程日志~ "));
        } else {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("config_debug_disabled", "调试模式已关闭~ "));
        }
    }
    
    public void debugLog(String message) {
        if (isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("config_debug_prefix", "[调试] %s", message));
        }
    }
    
    public int getRodDurability(String rodName) {
        if (!rodExists(rodName)) {
            return 0;
        }
        return rodConfig.getInt(getRodPropertyPath(rodName, "durability"), "default".equals(rodName) || "default_rod".equals(rodName) ? 65 : 0);
    }
    

    
    public double getRodChargeSpeed(String rodName) {
        if (!rodExists(rodName)) {
            return 1.0;
        }
        double speed = rodConfig.getDouble(getRodPropertyPath(rodName, "charge-speed"), 1.0);
        return speed > 0 && Double.isFinite(speed) ? speed : 1.0;
    }

    public String getRodChargeSpeedText(String rodName) {
        return String.format(Locale.ROOT, "%.1f", getRodChargeSpeed(rodName));
    }
    
    /** TODO: 当前没有生产调用点；配置不会影响 Bukkit 原版附魔结果。 */
    /** TODO：当前没有生产调用点；配置不会影响 Bukkit 原版附魔结果。 */
    public int getRodEnchantability(String rodName) {
        if (!rodExists(rodName)) {
            return 15;
        }
        return rodConfig.getInt(getRodPropertyPath(rodName, "enchantability"), 15);
    }
    
    public double getRodBiteRateBonus(String rodName) {
        if (!rodExists(rodName)) {
            return 0.0;
        }
        ConfigurationSection rod = rodConfig.getConfigurationSection("rods." + ("default_rod".equals(rodName) ? "default" : rodName));
        if (rod != null && !rod.contains("bite-chance-bonus") && !rod.contains("bite-rate-bonus")) {
            rod = rodConfig.getConfigurationSection("rods.default");
        }
        return getRodBiteRateBonus(rod);
    }

    static double getRodBiteRateBonus(ConfigurationSection rod) {
        if (rod == null) {
            return 0.0;
        }
        if (rod.contains("bite-chance-bonus")) {
            return rod.getDouble("bite-chance-bonus");
        }
        return rod.getDouble("bite-rate-bonus", 0.0);
    }

    public double getRodRareFishChance(String rodName) {
        if (!rodExists(rodName)) {
            return 0.0;
        }
        String id = "default_rod".equals(rodName) ? "default" : rodName;
        String path = "rods." + id;
        if (!rodConfig.contains(path + ".rare-fish-chance") && rodConfig.contains(path + ".carp-fish-chance")) {
            return rodConfig.getDouble(path + ".carp-fish-chance", 0.0);
        }
        String selected = getRodPropertyPath(rodName, "rare-fish-chance");
        return rodConfig.getDouble(selected, rodConfig.getDouble("rods.default.carp-fish-chance", 0.0));
    }

    /**
     * 鱼竿的诱鱼速度加成。缺字段时沿 rods.default 回退，与其它鱼竿属性一致。
     */
    public double getRodLureSpeed(String rodName) {
        double value = rodConfig.getDouble(getRodPropertyPath(rodName, "lure-speed"), 0.0);
        return Double.isFinite(value) && value > 0 ? value : 0.0;
    }

    /**
     * 鱼竿的鱼尺寸倍率。缺字段时沿 rods.default 回退，非法值按 1.0 处理。
     */
    public double getRodSizeMultiplier(String rodName) {
        double value = rodConfig.getDouble(getRodPropertyPath(rodName, "size-multiplier"), 1.0);
        return Double.isFinite(value) && value > 0 ? value : 1.0;
    }

    /**
     * 诱鱼速度总加成：鱼竿 + 鱼钩 + 鱼饵，三者加算。
     */
    public double getLureSpeedBonus(String rodName, String hookName, String baitName) {
        return getRodLureSpeed(rodName) + getHookLureSpeed(hookName) + getBaitLureSpeed(baitName);
    }

    public int getRodCustomModelData(String rodName) {
        if (!rodExists(rodName)) {
            return 0;
        }
        return rodConfig.getInt(getRodPropertyPath(rodName, "custom-model-data"), 0);
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
        return rodConfig.getStringList(getRodPropertyPath(rodName, "effects"));
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
            if (pagesSection == null) {
                return maxPage;
            }
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
        String path = getHookConfigPath(hookName);
        ConfigurationSection section = path == null ? null : hookConfig.getConfigurationSection(path);
        return section == null ? null : section.getValues(false);
    }
    
    public boolean hookExists(String hookName) {
        return getHookConfigPath(hookName) != null;
    }
    
    private String getHookConfigPath(String hookName) {
        if (hookName == null) return null;
        String cached = hookPathCache.get(hookName);
        if (cached != null) return cached;

        if (hookConfig.isConfigurationSection("hooks." + hookName)) {
            return "hooks." + hookName;
        }
        if (hookConfig.contains("hooks")) {
            ConfigurationSection pagesSection = hookConfig.getConfigurationSection("hooks");
            if (pagesSection == null) return null;
            for (String pageKey : pagesSection.getKeys(false)) {
                if (!pageKey.matches("\\d+")) continue;
                ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageKey);
                if (pageSection != null && pageSection.contains(hookName)) {
                    String path = "hooks." + pageKey + "." + hookName;
                    hookPathCache.put(hookName, path);
                    return path;
                }
            }
        }
        
        if (hookConfig.contains(hookName)) {
            hookPathCache.put(hookName, hookName);
            return hookName;
        }
        
        return null;
    }

    private void rebuildHookPathCache() {
        hookPathCache.clear();
        if (!hookConfig.contains("hooks")) return;
        ConfigurationSection pagesSection = hookConfig.getConfigurationSection("hooks");
        if (pagesSection == null) return;
        for (String pageKey : pagesSection.getKeys(false)) {
            ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageKey);
            if (pageSection == null) continue;
            if (!pageKey.matches("\\d+")) {
                hookPathCache.put(pageKey, "hooks." + pageKey);
                continue;
            }
            for (String hookName : pageSection.getKeys(false)) {
                hookPathCache.put(hookName, "hooks." + pageKey + "." + hookName);
            }
        }
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
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("config_invalid_hook_material", "无效的鱼钩材质: %s, 使用默认材质", materialName));
            return XSeriesUtil.getMaterial("OAK_LOG");
        }
    }

    /**
     * 获取鱼钩材质的原始配置字符串（不做Material解析，用于IA物品判断）
     */
    public String getHookMaterialStr(String hookName) {
        String configPath = getHookConfigPath(hookName);
        if (configPath == null) {
            return "OAK_LOG";
        }
        return hookConfig.getString(configPath + ".material", "OAK_LOG");
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

    public double getHookSizeMultiplier(String hookName) {
        double fallback = "diamond".equalsIgnoreCase(hookName) ? 1.3 : "gold".equalsIgnoreCase(hookName) ? 1.2
                : "iron".equalsIgnoreCase(hookName) ? 1.15 : "stone".equalsIgnoreCase(hookName) ? 1.1 : 1.05;
        String path = getHookConfigPath(hookName);
        return path == null ? fallback : hookConfig.getDouble(path + ".effects.size-multiplier", fallback);
    }

    public boolean shouldDisplayHookSizeMultiplier(String hookName) {
        String path = getHookConfigPath(hookName);
        if (path == null || !hookConfig.contains(path + ".effects.size-multiplier")) return false;
        double value = getHookSizeMultiplier(hookName);
        return Double.isFinite(value) && value > 0 && Math.abs(value - 1.0) > 0.0001;
    }

    /**
     * 鱼钩的诱鱼速度加成，读取 effects.lure-speed，缺省为 0。
     */
    public double getHookLureSpeed(String hookName) {
        String path = getHookConfigPath(hookName);
        if (path == null) {
            return 0.0;
        }
        double value = hookConfig.getDouble(path + ".effects.lure-speed", 0.0);
        return Double.isFinite(value) && value > 0 ? value : 0.0;
    }

    public double getHookValueMultiplier(String hookName) {
        double fallback = "diamond".equalsIgnoreCase(hookName) ? 1.3 : "gold".equalsIgnoreCase(hookName) ? 1.2
                : "iron".equalsIgnoreCase(hookName) ? 1.1 : 1.0;
        String path = getHookConfigPath(hookName);
        return path == null ? fallback : hookConfig.getDouble(path + ".effects.value-multiplier", fallback);
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
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("config_player_get_hook_perm", "玩家 %s 通过权限 %s 获得了鱼钩 %s", player.getName(), (player.hasPermission(permission) ? permission : "kkfish.hook.*"), materialType));
            }
            return true;
        }
        
        if (plugin.getDB() != null) {
            boolean hasPurchased = plugin.getDB().hasPlayerPurchasedHook(player.getUniqueId().toString(), materialType);
            if (hasPurchased) {
                if (isDebugMode()) {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("config_player_get_hook_buy", "玩家 %s 通过购买获得了鱼钩 %s", player.getName(), materialType));
                }
                return true;
            }
        }
        
        List<String> permissions = getHookPermissions(materialType);
        if (!permissions.isEmpty()) {
            for (String perm : permissions) {
                if (perm != null && !perm.isEmpty() && player.hasPermission(perm)) {
                    if (isDebugMode()) {
                        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("config_player_get_hook_special", "玩家 %s 通过特殊权限 %s 获得了鱼钩 %s", player.getName(), perm, materialType));
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
        // 主配置：版本迁移 + 当前语言包模板 diff 补齐
        String langCode = mainConfig.getString("language.current", "en_us");
        FileConfiguration mainTemplate = loadPackTemplate(langCode, "config.yml");
        int added = ConfigUpgrade.upgrade(mainConfig, mainTemplate, CONFIG_VERSION);
        if (added > 0) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix(
                "log.config_upgraded", "配置升级完成: 补充 %s 个缺失键 (config-version -> %s)", added, CONFIG_VERSION));
        }

        // 音效配置：固定结构，同样走模板补齐
        FileConfiguration soundTemplate = loadPackTemplate(langCode, "sounds.yml");
        if (soundTemplate != null) {
            int soundAdded = ConfigUpgrade.fillMissingKeys(soundConfig, soundTemplate);
            if (soundAdded > 0) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix(
                    "log.config_upgraded", "配置升级完成: 补充 %s 个缺失键 (sounds.yml)", soundAdded));
            }
        }

        // 内容列表配置：仅空表时给示例引导，不清洗玩家内容
        checkAndAddFishConfigDefaults();
        
        checkAndAddRodConfigDefaults();
        
        checkAndAddBaitConfigDefaults();
        
        saveConfigs();
    }

    /**
     * 直接从jar内加载语言包模板配置（数据目录的pack是临时释放的，会被清理）
     */
    private FileConfiguration loadPackTemplate(String langCode, String fileName) {
        InputStream in = plugin.getResource("config_packs/" + langCode + "/" + fileName);
        if (in == null) {
            return null;
        }
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
    }
    
    private void checkAndAddFishConfigDefaults() {
        if (!fishConfig.contains("fish")) {
            fishConfig.set("fish.example-fish.display-name", "&e示例鱼");
            fishConfig.set("fish.example-fish.material", "COD");
            fishConfig.set("fish.example-fish.rarity", 1);
            fishConfig.set("fish.example-fish.value", 10.0);
            fishConfig.set("fish.example-fish.exp", 5);
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_fish", "添加缺失的鱼类配置: %s", "example-fish"));
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
            rodConfig.set("rods.example-rod.bite-chance-bonus", 0.0);
            rodConfig.set("rods.example-rod.custom-model-data", 0);
            rodConfig.set("rods.example-rod.effects", new ArrayList<>());
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_rod", "添加缺失的鱼竿配置: %s", "example-rod"));
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
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("config_add_missing_bait", "添加缺失的鱼饵配置: %s", "example-bait"));
        }
    }
    

    public FileConfiguration getPoolConfig() {
        return poolConfig;
    }

    public List<String> getPoolFish(WaterType waterType) {
        List<String> cached = validPoolFishCache.get(waterType);
        return cached != null ? cached : new ArrayList<>();
    }

    private void reloadPoolCache() {
        validPoolFishCache.clear();
        for (WaterType waterType : WaterType.values()) {
            String key = waterType.name().toLowerCase();
            if (!poolConfig.contains(key)) continue;
            List<String> raw = poolConfig.getStringList(key);
            List<String> valid = new ArrayList<>();
            for (String fishName : raw) {
                if (fishExists(fishName)) {
                    valid.add(fishName);
                } else {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.config_pool_fish_not_found",
                        "鱼池 " + key + " 中的鱼 '" + fishName + "' 在 fish.yml 中不存在，请检查 pools.yml 和 fish.yml 配置是否一致",
                        key, fishName));
                }
            }
            validPoolFishCache.put(waterType, valid);
        }
    }

    public List<String> getAvailableFishFromPool(WaterType waterType, String biome, String weather, long time, String season) {
        if (waterType == null || !poolConfig.contains(waterType.name().toLowerCase(Locale.ROOT))) {
            return getAvailableFish(biome, weather, time, season);
        }
        return filterFishByConditions(getPoolFish(waterType), biome, weather, time, season);
    }

    public List<String> getAvailableFishFromPool(WaterType waterType, org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) return getAvailableFishFromPool(waterType, null, null, 0, null);
        org.bukkit.World world = location.getWorld();
        String weather = world.isThundering() ? "THUNDER" : world.hasStorm() ? "RAIN" : "CLEAR";
        String season = plugin.isRealisticSeasonsEnabled() ? plugin.getCurrentSeason() : null;
        return getAvailableFishFromPool(waterType, location.getBlock().getBiome().name(), weather, world.getTime(), season);
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

    public double getAuraSkillsBaseXp() {
        return mainConfig.getDouble("fishing-settings.aura-skills-base-xp", 5.0);
    }

    public String getRarityDisplayName(String rarityName) {
        return mainConfig.getString("fishing-settings.rarity." + rarityName + ".display-name", rarityName);
    }

    public String getRarityNameByLevel(String levelName) {
        if (levelName == null || levelName.trim().isEmpty()) return "common";
        String id = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', levelName)).split(":", 2)[0].trim().toLowerCase(Locale.ROOT);
        if ("传说".equals(id)) return "legendary";
        if ("史诗".equals(id)) return "epic";
        if ("稀有".equals(id)) return "rare";
        if ("罕见".equals(id)) return "uncommon";
        if ("普通".equals(id)) return "common";
        ConfigurationSection rarities = mainConfig.getConfigurationSection("fishing-settings.rarity");
        if (rarities != null) for (String key : rarities.getKeys(false)) {
            String display = mainConfig.getString("fishing-settings.rarity." + key + ".display-name", key);
            if (id.equals(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', display)).toLowerCase(Locale.ROOT))) return key;
        }
        return id;
    }

    public int getRarityOrder(String rarityName) {
        if (rarityName == null) return 1;
        switch (rarityName) {
            case "legendary": return 5;
            case "epic": return 4;
            case "rare": return 3;
            case "uncommon": return 2;
            default: return 1;
        }
    }
}
