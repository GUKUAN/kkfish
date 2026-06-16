package me.kkfish.managers;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import me.kkfish.kkfish;
import me.kkfish.competition.CompetitionConfig;
import me.kkfish.fishing.HookMechanic;
import me.kkfish.fishing.HookMechanicFactory;
import me.kkfish.fishing.WaterType;
import me.kkfish.misc.MessageManager;
import me.kkfish.misc.minigame.MinigameManager;
import me.kkfish.player.PlayerContext;
import me.kkfish.player.PlayerContextStore;
import me.kkfish.player.PersistentPlayerData;
import me.kkfish.player.RuntimeData;
import me.kkfish.utils.XSeriesUtil;

/**
 * 钓鱼系统协调器。
 *
 * <p>原 2497 行的 God Class 已拆分为 6 个专职组件：
 * <ul>
 *   <li>{@link ChargeProgressTracker} — 蓄力进度追踪与蓄力条显示</li>
 *   <li>{@link HookProjectile} — 鱼钩抛掷、轨迹、水域检测、粒子效果</li>
 *   <li>{@link BiteCheckScheduler} — 咬钩检查、概率计算、提示展示</li>
 *   <li>{@link FishItemFactory} — 鱼类物品创建、价值计算</li>
 *   <li>{@link FishAnimationService} — 鱼飞向玩家的动画</li>
 *   <li>{@link FishBroadcastService} — 钓鱼广播消息</li>
 * </ul>
 *
 * <p>本类仅保留公共 API 委派、共享状态管理（会话/冷却/上下文）及跨组件协调逻辑。
 */
public class Fish {

    private final kkfish plugin;
    private final MessageManager messageManager;
    private final Config config;
    private PlayerContextStore playerContextStore;

    private HookMechanicFactory hookMechanicFactory;

    private final Random random = new Random();

    private final MinigameManager minigameManager;

    // 拆分后的组件
    private ChargeProgressTracker chargeProgressTracker;
    private HookProjectile hookProjectile;
    private BiteCheckScheduler biteCheckScheduler;
    private FishItemFactory fishItemFactory;
    private FishAnimationService fishAnimationService;
    private FishBroadcastService fishBroadcastService;

    public Fish(kkfish plugin) {
        this.plugin = plugin;
        this.messageManager = kkfish.getInstance().getMessageManager();
        this.config = plugin.getCustomConfig();
        this.minigameManager = plugin.getMinigameManager();
        this.hookMechanicFactory = new HookMechanicFactory(plugin);
        this.playerContextStore = plugin.getPlayerContextStore();

        // 初始化拆分后的组件
        this.chargeProgressTracker = new ChargeProgressTracker(plugin, config, messageManager, playerContextStore, minigameManager, this);
        this.hookProjectile = new HookProjectile(plugin, config, messageManager, playerContextStore, hookMechanicFactory, this);
        this.biteCheckScheduler = new BiteCheckScheduler(plugin, config, messageManager, playerContextStore, minigameManager, this, random);
        this.fishItemFactory = new FishItemFactory(plugin, config, messageManager, random);
        this.fishAnimationService = new FishAnimationService(plugin, config, this, random);
        this.fishBroadcastService = new FishBroadcastService(plugin, config, messageManager);
    }

    // ==================== 上下文与状态 ====================

    /**
     * 获取玩家的 PlayerContext。
     * @param player 玩家（可为 null）
     * @return 上下文，若玩家或上下文不可用则返回 null
     */
    private PlayerContext getContext(Player player) {
        if (player == null || playerContextStore == null) return null;
        return playerContextStore.getContext(player.getUniqueId());
    }

    /**
     * 判断玩家是否有活跃的蓄力任务（替代旧的 getActiveChargeTasks().containsKey()）。
     * @param playerId 玩家 UUID
     * @return true 如果存在活跃蓄力任务
     */
    public boolean hasActiveChargeTask(UUID playerId) {
        if (playerContextStore == null) return false;
        PlayerContext ctx = playerContextStore.getContext(playerId);
        return ctx != null && ctx.getRuntime().getActiveChargeTask() != null;
    }

    /**
     * @deprecated 由 {@link #hasActiveChargeTask(UUID)} 替代，保留返回空 Map 以兼容旧调用方。
     */
    @Deprecated
    public Map<UUID, ChargeProgressTracker.ChargeProgressTask> getActiveChargeTasks() {
        return Collections.emptyMap();
    }

    // ==================== 随机鱼类生成 ====================

    public String[] generateRandomFish(Player player, String rodName, String baitName, double rareFishChance) {
        Config config = plugin.getCustomConfig();
        org.bukkit.configuration.file.FileConfiguration fishConfig = config.getFishConfig();

        WaterType waterType = null;
        PlayerContext ctx = getContext(player);
        if (ctx != null) {
            waterType = ctx.getSession().getWaterType();
        }

        List<String> fishList;
        if (waterType != null) {
            String biome = player.getLocation().getBlock().getBiome().name();
            String weather = player.getWorld().hasStorm() ? "RAIN" : "CLEAR";
            long time = player.getWorld().getTime();
            String season = null;
            if (plugin.isRealisticSeasonsEnabled()) {
                season = plugin.getCurrentSeason();
            }
            fishList = config.getAvailableFishFromPool(waterType, biome, weather, time, season);
        } else {
            fishList = config.getAllFishNames();
        }

        if (fishList.isEmpty()) {
            return new String[] {"cod", "30.0", "common"};
        }

        Map<String, Double> fishWeights = new LinkedHashMap<>();
        double totalWeight = 0;

        for (String fish : fishList) {
            double weight = 1.0;

            int rarity = config.getFishRarity(fish);
            switch (rarity) {
                case 1:
                    weight = 10.0;
                    break;
                case 2:
                    weight = 5.0;
                    break;
                case 3:
                    weight = 2.0;
                    break;
                case 4:
                    weight = 1.0;
                    break;
                case 5:
                    weight = 0.5;
                    break;
            }

            if (rareFishChance > 0 && rarity >= 3) {
                weight *= (1.0 + rareFishChance);
            }

            fishWeights.put(fish, weight);
            totalWeight += weight;
        }

        double randomValue = Math.random() * totalWeight;
        double currentWeight = 0;
        String selectedFish = fishList.get(0);

        for (Map.Entry<String, Double> entry : fishWeights.entrySet()) {
            currentWeight += entry.getValue();
            if (randomValue < currentWeight) {
                selectedFish = entry.getKey();
                break;
            }
        }

        double minSize = fishConfig.getDouble("fish." + selectedFish + ".min-size", 20.0);
        double maxSize = fishConfig.getDouble("fish." + selectedFish + ".max-size", 60.0);
        double randomSize = minSize + Math.random() * (maxSize - minSize);

        String fishLevel = config.getRandomFishLevel(selectedFish);

        return new String[] {selectedFish, String.valueOf(randomSize), fishLevel};
    }

    // ==================== 蓄力（委派给 ChargeProgressTracker） ====================

    public void startCharging(Player player) {
        chargeProgressTracker.startCharging(player);
    }

    public void stopCharging(Player player) {
        chargeProgressTracker.stopCharging(player);
    }

    public void stopCharging(Player player, boolean isOver100Percent) {
        chargeProgressTracker.stopCharging(player, isOver100Percent);
    }

    // ==================== 抛钩（委派给 HookProjectile） ====================

    public void throwFishHook(Player player, double chargePercentage) {
        hookProjectile.throwFishHook(player, chargePercentage);
    }

    public void handleHookInWater(Player player, ArmorStand hookEntity, Location currentLoc, org.bukkit.util.Vector velocity, double chargePercentage, String baitName) {
        hookProjectile.handleHookInWater(player, hookEntity, currentLoc, velocity, chargePercentage, baitName);
    }

    // ==================== 粒子效果（委派给 HookProjectile） ====================

    public void spawnParticleEffects(Location location) {
        hookProjectile.spawnParticleEffects(location);
    }

    public void createWaterSplashEffect(Location location) {
        hookProjectile.createWaterSplashEffect(location);
    }

    public void createLavaSplashEffect(Location location) {
        hookProjectile.createLavaSplashEffect(location);
    }

    public void createVoidSplashEffect(Location location) {
        hookProjectile.createVoidSplashEffect(location);
    }

    // ==================== 咬钩检查（委派给 BiteCheckScheduler） ====================

    public void scheduleBiteCheck(Player player, double chargePercentage, String baitName) {
        biteCheckScheduler.scheduleBiteCheck(player, chargePercentage, baitName);
    }

    public boolean triggerMinigame(Player player) {
        return biteCheckScheduler.triggerMinigame(player);
    }

    public void removeBiteHint(UUID playerId) {
        biteCheckScheduler.removeBiteHint(playerId);
    }

    public void removeBiteHint(UUID playerId, boolean sendEscapeMessage) {
        biteCheckScheduler.removeBiteHint(playerId, sendEscapeMessage);
    }

    // ==================== 鱼物品创建（委派给 FishItemFactory） ====================

    public ItemStack createFishItem(String fishName) {
        return fishItemFactory.createFishItem(fishName);
    }

    public ItemStack createFishItem(String fishName, boolean forceRarity) {
        return fishItemFactory.createFishItem(fishName, forceRarity);
    }

    public ItemStack createFishItem(String fishName, boolean forceRarity, Player player) {
        return fishItemFactory.createFishItem(fishName, forceRarity, player);
    }

    public ItemStack createFishItem(String fishName, boolean forceRarity, Player player, double fishSize, String fishLevel) {
        return fishItemFactory.createFishItem(fishName, forceRarity, player, fishSize, fishLevel);
    }

    public ItemStack createFishItem(String fishName, boolean forceRarity, Player player, double fishSize, String fishLevel, Double preCalculatedValue) {
        return fishItemFactory.createFishItem(fishName, forceRarity, player, fishSize, fishLevel, preCalculatedValue);
    }

    public double calculateFishValue(String fishName, double size, int rarity) {
        return fishItemFactory.calculateFishValue(fishName, size, rarity);
    }

    public ItemStack createOceanBackgroundItem() {
        return fishItemFactory.createOceanBackgroundItem();
    }

    // ==================== 鱼动画（委派给 FishAnimationService） ====================

    public interface AnimationCompleteCallback {
        void onAnimationComplete();
    }

    public void animateFishToPlayer(Player player, Location fishStartLocation, ItemStack fishItem, double fishValue) {
        fishAnimationService.animateFishToPlayer(player, fishStartLocation, fishItem, fishValue);
    }

    public void animateFishToPlayer(Player player, Location fishStartLocation, Location hookLocation, ItemStack fishItem, double fishValue, WaterType waterType, AnimationCompleteCallback callback) {
        fishAnimationService.animateFishToPlayer(player, fishStartLocation, hookLocation, fishItem, fishValue, waterType, callback);
    }

    // ==================== 广播（委派给 FishBroadcastService） ====================

    public void sendFishBroadcast(Player player, String fishName, double fishSize, int fishLevel, double fishValue) {
        fishBroadcastService.sendFishBroadcast(player, fishName, fishSize, fishLevel, fishValue);
    }

    // ==================== 原版捕获 ====================

    public void processVanillaCatch(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        String selectedFish = getRandomFish(player);
        if (selectedFish == null || selectedFish.isEmpty()) {
            return;
        }

        org.bukkit.configuration.file.FileConfiguration fishConfig = config.getFishConfig();
        double minSize = fishConfig.getDouble("fish." + selectedFish + ".min-size", 20.0);
        double maxSize = fishConfig.getDouble("fish." + selectedFish + ".max-size", 60.0);
        double size = minSize + Math.random() * (maxSize - minSize);
        String fishLevel = config.getRandomFishLevel(selectedFish, player);

        ItemStack fishItem = createFishItem(selectedFish, false, player, size, fishLevel);

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(fishItem);
        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            player.sendMessage(messageManager.getMessage("inventory_full", "背包已满，鱼掉在了地上！"));
        } else {
            String fishName = fishItem.hasItemMeta() && fishItem.getItemMeta().hasDisplayName()
                ? fishItem.getItemMeta().getDisplayName()
                : selectedFish;
            player.sendMessage(messageManager.getMessage("catch_success", "恭喜！你钓到了一条 %s！", fishName));
        }

        int exp = config.getFishExp(selectedFish);
        if (exp > 0) {
            player.giveExp(exp);
            player.sendMessage(messageManager.getMessage("fishing_exp_reward", "你获得了 %s 点钓鱼经验！", exp));
        }

        double value = calculateFishValue(selectedFish, size, config.getFishRarity(selectedFish));
        if (value > 0) {
        }
    }

    // ==================== 清理 ====================

    public void cleanup() {
        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("cleanup_start", "正在清理钓鱼系统资源..."));

        if (playerContextStore != null) {
            for (PlayerContext ctx : playerContextStore.getAllContexts()) {
                if (ctx == null) continue;
                // 取消所有运行时任务（蓄力/咬钩/效果等）
                ctx.getRuntime().cancelAllTasks();
                // 移除钓鱼会话实体
                ArmorStand entity = ctx.getSession().getFishingSession();
                if (entity != null) {
                    try {
                        entity.remove();
                    } catch (Exception ignored) {
                    }
                }
                // 清理会话资源
                ctx.getSession().clear();
            }
        }

        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("cleanup_complete", "钓鱼系统资源清理完成！"));
    }

    // ==================== 小游戏触发与 GUI 委派 ====================

    public void openFishCollectionGUI(Player player) {
        plugin.getGUI().openMainMenu(player);
    }

    public void openFishDexGUI(Player player) {
        plugin.getGUI().openFishDex(player);
    }

    public void openFishRecordGUI(Player player) {
        plugin.getGUI().openFishRecord(player);
    }

    public void openHelpGUI(Player player) {
        plugin.getGUI().openHelp(player);
    }

    public void openHookMaterialGUI(Player player) {
        plugin.getGUI().openHookMaterial(player);
    }

    public boolean isPlayerInMinigame(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return player != null && minigameManager != null && minigameManager.isPlayerInGame(player);
    }

    public void handlePlayerClick(Player player) {
        if (minigameManager != null) {
            minigameManager.handlePlayerInteraction(player);
        }
    }

    // ==================== 会话管理 ====================

    public void endSession(Player player) {
        if (player == null) {
            return;
        }

        PlayerContext ctx = getContext(player);

        ArmorStand armorStand = null;
        HookMechanic mechanic = null;
        if (ctx != null) {
            armorStand = ctx.getSession().getFishingSession();
            mechanic = ctx.getSession().getHookMechanic();
            // 取消所有运行时任务（蓄力/进度/咬钩/浮动/轨迹等）
            ctx.getRuntime().cancelAllTasks();
            // 清空运行时短期状态
            ctx.getRuntime().setChargeStartTime(0);
            ctx.getRuntime().setFishBitten(false);
            ctx.getRuntime().setBiteHintData(null);
            // 清理会话资源（ArmorStand 引用、小游戏数据、水域、机制等）
            ctx.getSession().clear();
        }

        if (armorStand != null) {
            try {
                armorStand.remove();
            } catch (Exception ignored) {
            }
        }

        if (mechanic != null) {
            mechanic.cleanup(player, armorStand);
        }

        if (minigameManager != null) {
            minigameManager.endGame(player);
        }

        setPlayerCooldown(player);
    }

    private void setPlayerCooldown(Player player) {
        int cooldownTime = config.getMainConfig().getInt("fishing-settings.cast-cooldown", 5000);
        PlayerContext ctx = getContext(player);
        if (ctx != null) {
            ctx.getRuntime().setCooldown(System.currentTimeMillis() + cooldownTime);
        }
    }

    public boolean isPlayerOnCooldown(Player player) {
        PlayerContext ctx = getContext(player);
        if (ctx == null) return false;
        return ctx.getRuntime().isOnCooldown();
    }

    public long getRemainingCooldown(Player player) {
        PlayerContext ctx = getContext(player);
        if (ctx == null) return 0;
        return ctx.getRuntime().getRemainingCooldown();
    }

    // ==================== 随机鱼名（旧版，返回单个鱼名） ====================

    public String getRandomFish(Player player) {
        List<String> fishList;

        Compete compete = plugin.getCompete();
        boolean hasActiveCompetition = compete != null && !compete.getActiveCompetitionIds().isEmpty();
        CompetitionConfig activeCompetitionConfig = null;
        if (hasActiveCompetition) {
            String competitionId = compete.getActiveCompetitionIds().iterator().next();
            activeCompetitionConfig = compete.getCompetitionConfig(competitionId);
        }

        if (activeCompetitionConfig != null && activeCompetitionConfig.hasFishList()) {
            fishList = new ArrayList<>(activeCompetitionConfig.getFishList().keySet());
        } else {
            if (plugin.isRealisticSeasonsEnabled() && config.isSeasonalFishingEnabled()) {
                String currentSeason = plugin.getCurrentSeason();
                if (currentSeason != null) {
                    fishList = config.getAvailableFish(currentSeason);
                } else {
                    fishList = config.getAllFishNames();
                }
            } else {
                fishList = config.getAllFishNames();
            }
        }

        if (fishList.isEmpty()) {
            return plugin.getMessageManager().getMessageWithoutPrefix("fish_unknown", "未知鱼");
        }

        double rareFishBonus = 1.0;
        if (player != null && player.getInventory().getItemInMainHand() != null) {
            ItemStack rod = player.getInventory().getItemInMainHand();
            if (rod.getType() == Material.FISHING_ROD && rod.hasItemMeta()) {
                ItemMeta meta = rod.getItemMeta();
                org.bukkit.enchantments.Enchantment luckEnchant = org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("luck_of_the_sea"));
                if (luckEnchant != null && meta.hasEnchant(luckEnchant)) {
                    int level = meta.getEnchantLevel(luckEnchant);
                    rareFishBonus += level * 0.1;
                }
            }
        }

        Map<String, Double> fishWeights = new LinkedHashMap<>();
        double totalWeight = 0;

        for (String fish : fishList) {
            double weight = 1.0;

            if (activeCompetitionConfig != null && activeCompetitionConfig.hasFishList()) {
                weight = activeCompetitionConfig.getFishList().getOrDefault(fish, 1.0);
            } else {
                int rarity = config.getFishRarity(fish);

                switch (rarity) {
                    case 1:
                        weight = 10.0;
                        break;
                    case 2:
                        weight = 5.0;
                        break;
                    case 3:
                        weight = 2.0;
                        break;
                    case 4:
                        weight = 1.0;
                        break;
                    case 5:
                        weight = 0.5;
                        break;
                }

                if (rareFishBonus > 1.0 && rarity >= 3) {
                    weight *= rareFishBonus;
                }
            }

            fishWeights.put(fish, weight);
            totalWeight += weight;
        }

        double randomValue = Math.random() * totalWeight;
        double currentWeight = 0;

        for (Map.Entry<String, Double> entry : fishWeights.entrySet()) {
            currentWeight += entry.getValue();
            if (randomValue < currentWeight) {
                return entry.getKey();
            }
        }

        return fishList.get(random.nextInt(fishList.size()));
    }

    // ==================== 钓鱼记录 ====================

    public void recordFishCatch(Player player, String fishName, ItemStack fishItem) {
        PlayerContext ctx = getContext(player);
        if (ctx == null) {
            return;
        }
        PersistentPlayerData persistent = ctx.getPersistent();

        ItemMeta meta = fishItem.getItemMeta();
        if (meta != null && meta.hasLore()) {
            String fishLevel = "普通";
            double fishSize = 0.0;
            int fishValue = 0;

            List<String> lore = meta.getLore();
            for (String line : lore) {
                if (line.contains("级别: ")) {
                    fishLevel = line.substring(line.lastIndexOf(' ') + 1);
                }
                try {
                    String cleanLine = ChatColor.stripColor(line);

                    if (cleanLine.matches("\\d+(\\.\\d+)?")) {
                        fishSize = Double.parseDouble(cleanLine);
                    } else if (cleanLine.contains("大小: ") && cleanLine.contains("cm")) {
                        int sizeStart = cleanLine.indexOf("大小: ") + 4;
                        int cmPos = cleanLine.indexOf("cm");
                        if (sizeStart > 0 && cmPos > sizeStart) {
                            String sizeStr = cleanLine.substring(sizeStart, cmPos).trim();
                            if (!sizeStr.isEmpty()) {
                                fishSize = Double.parseDouble(sizeStr);
                            }
                        }
                    } else if (cleanLine.contains("cm")) {
                        String numStr = cleanLine.replaceAll("[^\\d.]", "");
                        if (!numStr.isEmpty()) {
                            fishSize = Double.parseDouble(numStr);
                        }
                    }
                } catch (Exception e) {
                    kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.fishing_size_parse_failed", "Failed to parse fish size: ") + line);
                }
                if (line.contains("价值: ") && line.contains("金币")) {
                    try {
                        int valueStart = line.lastIndexOf(' ') + 1;
                        int coinStart = line.indexOf(" 金币");
                        if (coinStart < 0) {
                            coinStart = line.indexOf("金币");
                        }
                        if (valueStart > 0 && coinStart > valueStart) {
                            // 提取数字部分，过滤掉非数字字符
                            String valueStr = line.substring(valueStart, coinStart).replaceAll("[^\\d]", "").trim();
                            if (!valueStr.isEmpty()) {
                                fishValue = Integer.parseInt(valueStr);
                            }
                        }
                    } catch (Exception e) {
                        kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.fishing_value_parse_failed", "Failed to parse fish value: ") + line);
                    }
                }
            }

            // 记录捕获（自动 incrementCount + updateMaxSize）
            persistent.recordCatch(fishName, fishSize);

            plugin.getDB().logFishing(player, fishName, fishLevel, fishSize, fishValue);
        } else {
            // 无 lore 信息时仅记录计数
            persistent.recordCatch(fishName, 0.0);
        }

    }

    // ==================== 鱼咬钩状态 ====================

    public void resetFishBitten(Player player) {
        if (player != null) {
            PlayerContext ctx = getContext(player);
            if (ctx != null) {
                ctx.getRuntime().setFishBitten(false);
            }
        }
    }

    public boolean isFishBitten(Player player) {
        if (player == null) {
            return false;
        }
        PlayerContext ctx = getContext(player);
        return ctx != null && ctx.getRuntime().isFishBitten();
    }

    // ==================== 鱼钩材质 ====================

    public Material getPlayerHookMaterial(Player player) {
        if (player == null) {
            return XSeriesUtil.getMaterial("WHITE_WOOL");
        }

        UUID playerId = player.getUniqueId();
        PlayerContext ctx = getContext(player);
        if (ctx == null) {
            return XSeriesUtil.getMaterial("WHITE_WOOL");
        }
        Material cached = ctx.getSession().getHookMaterial();
        if (cached == null) {
            String materialType = plugin.getDB().getPlayerHookMaterial(playerId.toString());
            cached = me.kkfish.utils.MaterialResolver.getMaterialFromType(materialType, config);
            ctx.getSession().setHookMaterial(cached);
        }
        return cached;
    }

    public String getPlayerHookMaterialName(Player player) {
        return getPlayerHookMaterial(player).name();
    }

    public void setPlayerHookMaterial(Player player, Material material) {
        if (player != null && material != null) {
            PlayerContext ctx = getContext(player);
            if (ctx == null) return;
            ctx.getSession().setHookMaterial(material);

            ArmorStand hookEntity = ctx.getSession().getFishingSession();
            if (hookEntity != null && !hookEntity.isDead()) {
                hookEntity.getEquipment().setHelmet(new ItemStack(material));
                try {
                    hookEntity.getEquipment().setHelmetDropChance(0);
                } catch (Exception e) {
                }
            }
        }
    }

    // ==================== 会话查询 ====================

    public Object getActiveSession(Player player) {
        if (player == null) {
            return null;
        }
        PlayerContext ctx = getContext(player);
        return ctx != null ? ctx.getSession().getFishingSession() : null;
    }

    public WaterType getWaterType(Player player) {
        if (player == null) {
            return null;
        }
        PlayerContext ctx = getContext(player);
        return ctx != null ? ctx.getSession().getWaterType() : null;
    }
}
