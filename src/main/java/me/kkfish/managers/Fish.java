package me.kkfish.managers;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.enchantments.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.FileConfiguration;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

import me.kkfish.kkfish;
import me.kkfish.competition.CompetitionConfig;
import me.kkfish.fishing.HookMechanic;
import me.kkfish.fishing.HookMechanicFactory;
import me.kkfish.fishing.WaterType;
import me.kkfish.managers.Compete;
import me.kkfish.managers.Config;
import me.kkfish.managers.ItemValue;
import me.kkfish.misc.MessageManager;
import me.kkfish.misc.minigame.MinigameManager;
import me.kkfish.utils.ActionBarUtil;
import me.kkfish.utils.ActionBarUtil.MessageType;
import me.kkfish.utils.SchedulerUtil;
import me.kkfish.utils.XSeriesUtil;

public class Fish {

    private final kkfish plugin;
    private final Logger logger;
    private final MessageManager messageManager;
    private final Config config; // 新增配置管理器引用
    private final Map<UUID, ArmorStand> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, MinigameData> minigameData = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> fishBitten = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chargeStartTime = new ConcurrentHashMap<>();
    private final Map<UUID, ChargeProgressTask> activeChargeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> activeProgressTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> biteCheckTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, FishRecord>> playerFishRecords = new ConcurrentHashMap<>();
    
    private final Map<UUID, Material> playerHookMaterials = new ConcurrentHashMap<>();
    
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    
    private final Map<UUID, Long> playerMessageCooldowns = new ConcurrentHashMap<>();
    
    private final Map<UUID, BiteHintData> biteHintDataMap = new ConcurrentHashMap<>();
    
    private final Map<UUID, WaterType> playerWaterType = new ConcurrentHashMap<>();
    
    private final Map<UUID, HookMechanic> playerHookMechanic = new ConcurrentHashMap<>();
    
    private HookMechanicFactory hookMechanicFactory;
    
    private final Random random = new Random();
    private final StringBuilder stringBuilder = new StringBuilder();
    private final Vector tempVector = new Vector();
    private final Location tempLocation = new Location(null, 0, 0, 0);
    
    private class BiteHintData {
        double chargePercentage;
        String baitName;
        double rareFishChance;
        long expireTime;
        BukkitRunnable expireTask;
        
        public BiteHintData(double chargePercentage, String baitName, double rareFishChance, long expireTime, BukkitRunnable expireTask) {
            this.chargePercentage = chargePercentage;
            this.baitName = baitName;
            this.rareFishChance = rareFishChance;
            this.expireTime = expireTime;
            this.expireTask = expireTask;
        }
    }

    private final MinigameManager minigameManager;

    public Fish(kkfish plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.messageManager = kkfish.getInstance().getMessageManager();
        this.config = plugin.getCustomConfig();
        this.minigameManager = plugin.getMinigameManager();
        this.hookMechanicFactory = new HookMechanicFactory(plugin);
    }

    public Map<UUID, ChargeProgressTask> getActiveChargeTasks() {
        return activeChargeTasks;
    }
    
    public String[] generateRandomFish(Player player, String rodName, String baitName, double rareFishChance) {
        Config config = plugin.getCustomConfig();
        FileConfiguration fishConfig = config.getFishConfig();
        
        WaterType waterType = playerWaterType.get(player.getUniqueId());
        
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

    public void startCharging(Player player) {
        if (player == null) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        
        if (isPlayerOnCooldown(player)) {
            long remainingCooldown = getRemainingCooldown(player);
            long currentTime = System.currentTimeMillis();
            
            if (!playerMessageCooldowns.containsKey(playerId) || 
                (currentTime - playerMessageCooldowns.get(playerId)) >= 1000) {
                player.sendMessage(messageManager.getMessage("cast_cooldown", "请等待 %.1f 秒后再钓鱼！", (remainingCooldown / 1000.0)));
                playerMessageCooldowns.put(playerId, currentTime);
            }
            return;
        }
        
        config.debugLog(messageManager.getMessageWithoutPrefix("debug_fishing_start", "玩家%s尝试开始蓄力钓鱼", player.getName()));
        
        if (chargeStartTime.containsKey(playerId)) {
            config.debugLog(messageManager.getMessageWithoutPrefix("debug_fishing_charging", "玩家%s已经在蓄力中，操作被拒绝", player.getName()));
            return;
        }
        
        if (activeSessions.containsKey(playerId)) {
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

        chargeStartTime.put(playerId, System.currentTimeMillis());
        plugin.getSoundManager().playPrepareSound(player.getLocation());
        startChargeProgressTask(player, rodName);
        config.debugLog(messageManager.getMessageWithoutPrefix("debug_fishing_charge_start", "玩家%s蓄力开始，最大蓄力时间调整为: %f", player.getName(), config.getMainConfig().getInt("fishing-settings.max-charge-time", 3000) / config.getRodChargeSpeed(rodName)));
    }

    private void startChargeProgressTask(Player player, String rodName) {
        int maxChargeTime = config.getMainConfig().getInt("fishing-settings.max-charge-time", 3000);
        
        double chargeSpeedMultiplier = config.getRodChargeSpeed(rodName);
        if (chargeSpeedMultiplier != 1.0) {
            maxChargeTime = (int)(maxChargeTime / chargeSpeedMultiplier);
        }
        
        final int adjustedMaxChargeTime = maxChargeTime;
        final UUID playerId = player.getUniqueId();

        ChargeProgressTask task = new ChargeProgressTask(player, adjustedMaxChargeTime);
        
        config.debugLog("创建新的BukkitRunnable来持续执行蓄力进度更新");
        BukkitRunnable progressTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!chargeStartTime.containsKey(playerId)) {
                    config.debugLog("玩家不再蓄力，取消任务 for player: " + player.getName());
                    this.cancel();
                    return;
                }
                
                task.run();
            }
        };
        
        activeProgressTasks.put(playerId, progressTask);
        activeChargeTasks.put(playerId, task);
        
        config.debugLog("添加蓄力进度任务到activeChargeTasks: " + playerId);
        config.debugLog("activeChargeTasks大小: " + activeChargeTasks.size());
        config.debugLog("添加进度更新任务到activeProgressTasks: " + playerId);
        config.debugLog("activeProgressTasks大小: " + activeProgressTasks.size());
        
        try {
            progressTask.runTaskTimer(plugin, 0, 1);
            config.debugLog("BukkitRunnable.runTaskTimer调用成功");
        } catch (Exception e) {
            config.debugLog("BukkitRunnable.runTaskTimer调用失败: " + e.getMessage());
            
            try {
                config.debugLog("尝试使用Bukkit.getScheduler().runTaskTimer()");
                Bukkit.getScheduler().runTaskTimer(plugin, progressTask, 0, 1);
                config.debugLog("Bukkit.getScheduler().runTaskTimer调用成功");
            } catch (Exception ex) {
                config.debugLog("Bukkit.getScheduler().runTaskTimer调用失败: " + ex.getMessage());
                
                config.debugLog("尝试使用持续执行任务的方法");
                
                config.debugLog("直接执行一次蓄力进度任务");
                progressTask.run();
                
                runContinuousTask(progressTask, 1);
            }
        }
    }

    private void runContinuousTask(BukkitRunnable task, long delayTicks) {
        try {
            config.debugLog("调度持续执行任务，延迟: " + delayTicks + " ticks");
            
            task.run();
            
            BukkitRunnable nextTask = new BukkitRunnable() {
                @Override
                public void run() {
                    runContinuousTask(task, delayTicks);
                }
            };
            
            SchedulerUtil.scheduleTask(plugin, nextTask, delayTicks, 0);
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
        UUID playerId = player.getUniqueId();
        if (!chargeStartTime.containsKey(playerId)) {
            return;
        }

        long chargeTime = System.currentTimeMillis() - chargeStartTime.get(playerId);
        int maxChargeTime = config.getMainConfig().getInt("fishing-settings.max-charge-time", 3000);
        double chargePercentage = Math.min(chargeTime * 100.0 / maxChargeTime, 100.0);

        chargeStartTime.remove(playerId);
        
        ChargeProgressTask chargeTask = activeChargeTasks.remove(playerId);
        if (chargeTask != null) {
            try {
                chargeTask.cancel();
                config.debugLog("成功取消蓄力进度任务 for player: " + player.getName());
            } catch (IllegalStateException e) {
                config.debugLog("任务尚未调度，无需取消 for player: " + player.getName());
            }
        }
        
        BukkitRunnable progressTask = activeProgressTasks.remove(playerId);
        if (progressTask != null) {
            try {
                progressTask.cancel();
                config.debugLog("成功取消进度更新任务 for player: " + player.getName());
            } catch (Exception e) {
                config.debugLog("取消进度更新任务时出错: " + e.getMessage());
            }
        }

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

        throwFishHook(player, chargePercentage);
    }

    public void processVanillaCatch(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        String selectedFish = getRandomFish(player);
        if (selectedFish == null || selectedFish.isEmpty()) {
            return;
        }

        FileConfiguration fishConfig = config.getFishConfig();
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

    private double calculateFishValue(String fishName, double size, int rarity) {
        FileConfiguration fishConfig = config.getFishConfig();
        double baseValue = fishConfig.getDouble("fish." + fishName + ".value", 10.0);
        
        double sizeMultiplier = size / 30.0;
        
        double rarityMultiplier;
        switch (rarity) {
            case 1: rarityMultiplier = 1.0; break;
            case 2: rarityMultiplier = 1.2; break;
            case 3: rarityMultiplier = 1.5; break;
            case 4: rarityMultiplier = 2.0; break;
            case 5: rarityMultiplier = 3.0; break;
            default: rarityMultiplier = 1.0; break;
        }
        
        return baseValue * sizeMultiplier * rarityMultiplier;
    }

    public void cleanup() {
        logger.info(plugin.getMessageManager().getMessageWithoutPrefix("cleanup_start", "正在清理钓鱼系统资源..."));
        
        for (ChargeProgressTask task : activeChargeTasks.values()) {
            task.cancel();
        }
        for (BukkitRunnable task : activeProgressTasks.values()) {
            task.cancel();
        }
        for (BukkitRunnable task : biteCheckTasks.values()) {
            task.cancel();
        }
        
        for (ArmorStand entity : activeSessions.values()) {
            if (entity != null) {
                entity.remove();
            }
        }
        
        activeSessions.clear();
        minigameData.clear();
        chargeStartTime.clear();
        fishBitten.clear();
        activeChargeTasks.clear();
        activeProgressTasks.clear();
        biteCheckTasks.clear();
        
        logger.info(plugin.getMessageManager().getMessageWithoutPrefix("cleanup_complete", "钓鱼系统资源清理完成！"));
    }

    private void throwFishHook(Player player, double chargePercentage) {
        if (chargePercentage >= 100.0) {
            chargePercentage = 0;
        }

        String baitName = checkAndConsumeBait(player);

        final double finalChargePercentage = chargePercentage;
        final String finalBaitName = baitName;
        
        UUID playerId = player.getUniqueId();
        plugin.getSoundManager().playCastSound(player.getLocation());

        ArmorStand hookEntity = createHookEntity(player);
        
        Vector direction = calculateParabolicTrajectory(player, chargePercentage);
        
        handleHookTrajectory(player, hookEntity, direction, finalChargePercentage, finalBaitName);
    }
    
    private String checkAndConsumeBait(Player player) {
        ItemStack offhandItem = player.getInventory().getItemInOffHand();
        String baitName = null;
        if (offhandItem != null && offhandItem.getType() != Material.AIR) {
            ItemMeta meta = offhandItem.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                for (String line : lore) {
                    if (line.contains(plugin.getMessageManager().getMessageWithoutPrefix("bait_usage_tip", "放于副手，蓄力抛出时消耗"))) {
                            String displayName = meta.getDisplayName();
                            String currentBaitName = ChatColor.stripColor(displayName);
                            
                            if (!plugin.getCustomConfig().hasBaitPermission(player, currentBaitName)) {
                                player.sendMessage(plugin.getMessageManager().getMessage("bait_no_permission", "§d你没有权限使用这个鱼饵！"));
                                break;
                            }
                            
                            offhandItem.setAmount(offhandItem.getAmount() - 1);
                            if (offhandItem.getAmount() <= 0) {
                                player.getInventory().setItemInOffHand(null);
                            } else {
                                player.getInventory().setItemInOffHand(offhandItem);
                            }
                            player.sendMessage(plugin.getMessageManager().getMessage("bait_used", "§a已消耗一个鱼饵，获得特殊效果！"));
                            
                            baitName = currentBaitName;
                            break;
                        }
                }
            }
        }
        return baitName;
    }
    
    private ArmorStand createHookEntity(Player player) {
        World world = player.getWorld();
        UUID playerId = player.getUniqueId();
        
        Location startLocation = tempLocation.clone();
        startLocation.setWorld(world);
        startLocation.setX(player.getEyeLocation().getX());
        startLocation.setY(player.getEyeLocation().getY());
        startLocation.setZ(player.getEyeLocation().getZ());
        startLocation.setPitch(player.getEyeLocation().getPitch());
        startLocation.setYaw(player.getEyeLocation().getYaw());
        
        ArmorStand hookEntity = world.spawn(startLocation, ArmorStand.class);
        hookEntity.setVisible(false);
        hookEntity.setGravity(false);
        hookEntity.setSmall(true);
        hookEntity.setMarker(true);
        hookEntity.setInvulnerable(true);
        hookEntity.setCustomNameVisible(false);
        hookEntity.setArms(false);
        hookEntity.setBasePlate(false);
        
        String hookMaterialNameFromDB = plugin.getDB().getPlayerHookMaterial(playerId.toString());
        
        if (!plugin.getCustomConfig().hasHookMaterialPermission(player, hookMaterialNameFromDB)) {
            Material woodMaterial = XSeriesUtil.getMaterial("OAK_LOG");
            playerHookMaterials.put(playerId, woodMaterial);
            player.sendMessage(plugin.getMessageManager().getMessage("hook_no_permission", "§d你没有权限使用这个鱼钩材质！已自动切换为木质鱼钩。"));
        }
        
        Material hookMaterial = getPlayerHookMaterial(player);
        
        hookEntity.getEquipment().setHelmet(new ItemStack(hookMaterial));
        try {
            hookEntity.getEquipment().setHelmetDropChance(0);
        } catch (Exception e) {
        }
        hookEntity.getEquipment().setItemInMainHand(null);
        
        activeSessions.put(playerId, hookEntity);
        
        return hookEntity;
    }
    
    private Vector calculateParabolicTrajectory(Player player, double chargePercentage) {
        Vector direction = tempVector.clone();
        direction.copy(player.getLocation().getDirection());
        double power = chargePercentage / 100.0;
        double speed = 0.3 + power * 0.3;
        
        direction.multiply(speed);
        direction.setY(direction.getY() + 0.08 + power * 0.05);
        
        return direction;
    }
    
    private void handleHookTrajectory(Player player, ArmorStand hookEntity, Vector direction, double chargePercentage, String baitName) {
        final BukkitTask[] taskRef = new BukkitTask[1];
        
        taskRef[0] = SchedulerUtil.scheduleTask(plugin, new Runnable() {
            private int ticks = 0;
            private final Vector velocity = direction.clone();
            private final double gravity = 0.06 - (chargePercentage / 100.0 * 0.03);
            private boolean endVoidCountdownStarted = false;
            private int endVoidCountdownTicks = 0;
            
            @Override
            public void run() {
                ticks++;
                
                velocity.setY(velocity.getY() - gravity);
                
                Location currentLoc = hookEntity.getLocation();
                currentLoc.add(velocity);
                
                hookEntity.teleport(currentLoc);
                
                WaterType waterType = hookMechanicFactory.detectWaterType(currentLoc, player);
                
                boolean isOnGround = checkIfHookOnGround(currentLoc.getBlock());
                
                if (waterType == null && hookMechanicFactory.isEndVoidCandidate(currentLoc, player)) {
                    if (!endVoidCountdownStarted) {
                        endVoidCountdownStarted = true;
                        endVoidCountdownTicks = 0;
                    }
                    endVoidCountdownTicks++;
                    
                    if (endVoidCountdownTicks >= config.getEndCountdownTicks()) {
                        waterType = WaterType.VOID;
                        endVoidCountdownStarted = false;
                    }
                } else {
                    endVoidCountdownStarted = false;
                }
                
                boolean shouldCheckEscape = ticks > 100 && config.isFishEscapeBeforeMinigameEnabled();
                
                if (waterType != null || isOnGround || shouldCheckEscape) {
                    if (taskRef[0] != null) {
                        taskRef[0].cancel();
                    }
                    
                    if (waterType != null) {
                        playerWaterType.put(player.getUniqueId(), waterType);
                        HookMechanic mechanic = hookMechanicFactory.create(waterType);
                        playerHookMechanic.put(player.getUniqueId(), mechanic);
                        mechanic.onHookLand(player, hookEntity, currentLoc, velocity, chargePercentage, baitName);
                    } else if (isOnGround) {
                        handleHookOnGround(player, hookEntity, currentLoc, velocity);
                    } else if (config.isFishEscapeBeforeMinigameEnabled()) {
                        handleHookFailure(player);
                    }
                }
            }
        }, 0, 1);
    }
    
    private boolean checkIfHookOnGround(Block block) {
        try {
            java.lang.reflect.Method isPassableMethod = block.getClass().getMethod("isPassable");
            if (isPassableMethod != null) {
                boolean isPassable = (Boolean) isPassableMethod.invoke(block);
                if (!isPassable) {
                    return !isHookPassableBlock(block);
                }
                return false;
            }
        } catch (Exception ignored) {
        }
        
        if (block.getType().isSolid()) {
            return !isHookPassableBlock(block);
        }
        return false;
    }

    private boolean isHookPassableBlock(Block block) {
        String name = block.getType().name();
        return name.equals("TORCH") || 
               name.equals("REDSTONE_TORCH") || 
               name.equals("LANTERN") || 
               name.equals("SEA_LANTERN") || 
               name.equals("GLOWSTONE") || 
               name.equals("REDSTONE_LAMP") || 
               name.equals("REDSTONE_LAMP_OFF") || 
               name.equals("CAMPFIRE") || 
               name.equals("SOUL_CAMPFIRE") || 
               name.equals("SOUL_TORCH") || 
               name.equals("WALL_TORCH") || 
               name.equals("SOUL_WALL_TORCH") || 
               name.equals("LIGHT_BLOCK") || 
               name.equals("SOUL_LANTERN") || 
               name.equals("WALL_LANTERN") || 
               name.equals("SOUL_WALL_LANTERN");
    }
    
    private void handleHookInWater(Player player, ArmorStand hookEntity, Location currentLoc, Vector velocity, double chargePercentage, String baitName) {
        createWaterSplashEffect(currentLoc);
        
        me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("hook_in_water", "鱼钩已落入水中，等待鱼儿上钩..."), 40, MessageType.FISHING);
        
        final Vector entryVelocity = velocity.clone();
        
        handleHookInWaterMovement(player, hookEntity, entryVelocity, chargePercentage, baitName);
    }
    
    private void handleHookInWaterMovement(Player player, ArmorStand hookEntity, Vector entryVelocity, double chargePercentage, String baitName) {
        final BukkitRunnable[] runnableRef = new BukkitRunnable[1];
        final org.bukkit.scheduler.BukkitTask[] taskRef = new org.bukkit.scheduler.BukkitTask[1];
        
        runnableRef[0] = new BukkitRunnable() {
            private double distanceMoved = 0;
            private final double targetDistance = 0.5;
            private final double waterResistance = 0.03;
            private int tickCount = 0;
            private final int maxTicks = 20;
            
            @Override
            public void run() {
                tickCount++;
                
                entryVelocity.multiply(1 - waterResistance);
                entryVelocity.setY(entryVelocity.getY() - 0.01);
                
                Location currentLoc = hookEntity.getLocation();
                currentLoc.add(entryVelocity);
                
                hookEntity.teleport(currentLoc);
                
                distanceMoved += Math.abs(entryVelocity.getY());
                
                if (distanceMoved >= targetDistance || currentLoc.getBlockY() <= 0 || tickCount >= maxTicks) {
                    if (taskRef[0] != null) {
                        SchedulerUtil.cancelTask(taskRef[0]);
                    }
                    
                    startHookFloatingEffect(hookEntity);
                    
                    scheduleBiteCheck(player, chargePercentage, baitName);
                }
            }
        };
        
        taskRef[0] = SchedulerUtil.scheduleTask(plugin, runnableRef[0], 0, 1);
    }
    
    private void startHookFloatingEffect(ArmorStand hookEntity) {
        SchedulerUtil.scheduleTask(plugin, new BukkitRunnable() {
            private int floatTicks = 0;
            private final double floatAmplitude = 0.2;
            private final Location floatStartLoc = hookEntity.getLocation().clone();
            
            @Override
            public void run() {
                floatTicks++;
                
                double yOffset = Math.sin(floatTicks * 0.1) * floatAmplitude;
                Location currentLoc = floatStartLoc.clone();
                currentLoc.setY(currentLoc.getY() + yOffset);
                
                hookEntity.teleport(currentLoc);
            }
        }, 0, 1);
    }
    
    private void handleHookOnGround(Player player, ArmorStand hookEntity, Location currentLoc, Vector velocity) {
        me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("hook_not_in_water", "鱼钩没有落入水中！"), 40, MessageType.FISHING);
        
        final BukkitRunnable[] runnableRef = new BukkitRunnable[1];
        final org.bukkit.scheduler.BukkitTask[] taskRef = new org.bukkit.scheduler.BukkitTask[1];
        
        runnableRef[0] = new BukkitRunnable() {
            private double distanceMoved = 0;
            private final double targetDistance = 0.5;
            private final Vector groundVelocity = velocity.clone().normalize();
            
            @Override
            public void run() {
                Location currentLoc = hookEntity.getLocation();
                currentLoc.add(groundVelocity.clone().multiply(0.1));
                
                hookEntity.teleport(currentLoc);
                
                distanceMoved += 0.1;
                
                boolean isBlockPassable = true;
                try {
                    Block block = currentLoc.getBlock();
                    java.lang.reflect.Method isPassableMethod = block.getClass().getMethod("isPassable");
                    if (isPassableMethod != null) {
                        isBlockPassable = (Boolean) isPassableMethod.invoke(block);
                    }
                } catch (Exception e) {
                    isBlockPassable = !currentLoc.getBlock().getType().isSolid();
                }
                
                if (distanceMoved >= targetDistance || !isBlockPassable) {
                    if (taskRef[0] != null) {
                        SchedulerUtil.cancelTask(taskRef[0]);
                    }
                    endSession(player);
                }
            }
        };
        
        taskRef[0] = SchedulerUtil.scheduleTask(plugin, runnableRef[0], 0, 1);
    }
    
    private void handleHookFailure(Player player) {
        me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("hook_not_in_water", "鱼钩没有落入水中！"), 40, MessageType.FISHING);
        endSession(player);
    }

    private void spawnParticleEffects(Location location) {
        if (!config.isFishEmergeParticleEnabled()) return;
        
        try {
            double offsetX = config.getFishEmergeParticleOffsetX();
            double offsetY = config.getFishEmergeParticleOffsetY();
            double offsetZ = config.getFishEmergeParticleOffsetZ();
            
            Location particleLocation = location.clone().add(offsetX, offsetY, offsetZ);
            
            String particleTypeStr = config.getFishEmergeParticleType();
            Particle particleType = getSafeParticle(particleTypeStr, Particle.REDSTONE);
            
            int count = config.getFishEmergeParticleCount();
            double spreadX = config.getFishEmergeParticleSpreadX();
            double spreadY = config.getFishEmergeParticleSpreadY();
            double spreadZ = config.getFishEmergeParticleSpreadZ();
            double extra = config.getFishEmergeParticleExtra();
            
            try {
                Class.forName("org.bukkit.Particle$DustOptions");
                if (particleType == Particle.REDSTONE) {
                    int red = config.getFishEmergeParticleRed();
                    int green = config.getFishEmergeParticleGreen();
                    int blue = config.getFishEmergeParticleBlue();
                    float size = config.getFishEmergeParticleSize();
                    
                    plugin.getEntityBatchProcessor().addParticle(
                            particleType, 
                            particleLocation, 
                            count, 
                            spreadX, spreadY, spreadZ, 
                            extra, 
                            new Particle.DustOptions(Color.fromRGB(red, green, blue), size)
                    );
                } else {
                    plugin.getEntityBatchProcessor().addParticle(
                            particleType, 
                            particleLocation, 
                            count, 
                            spreadX, spreadY, spreadZ, 
                            extra,
                            null
                    );
                }
            } catch (ClassNotFoundException e) {
                Particle fallbackParticle = getSafeParticle("CLOUD", null);
                if (fallbackParticle != null) {
                    plugin.getEntityBatchProcessor().addParticle(
                            fallbackParticle, 
                            particleLocation, 
                            count, 
                            spreadX, spreadY, spreadZ, 
                            extra,
                            null
                    );
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to spawn particle effects: " + e.getMessage());
            try {
                double offsetX = config.getFishEmergeParticleOffsetX();
                double offsetY = config.getFishEmergeParticleOffsetY();
                double offsetZ = config.getFishEmergeParticleOffsetZ();
                Location particleLocation = location.clone().add(offsetX, offsetY, offsetZ);
                
                Particle cloudParticle = getSafeParticle("CLOUD", null);
                if (cloudParticle != null) {
                    plugin.getEntityBatchProcessor().addParticle(
                            cloudParticle, 
                            particleLocation, 
                            15, 
                            config.getFishEmergeParticleSpreadX(), 
                            config.getFishEmergeParticleSpreadY(), 
                            config.getFishEmergeParticleSpreadZ(), 
                            0.1,
                            null
                    );
                }
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to spawn fallback particle effects: " + ex.getMessage());
            }
        }
    }
    
    private void createWaterSplashEffect(Location location) {
        if (!config.isHookWaterSplashParticleEnabled()) return;
        
        String particleType = config.getHookWaterSplashParticleType();
        int count = config.getHookWaterSplashParticleCount();
        double spread = config.getHookWaterSplashParticleSpreadX();
        double speed = 0.3;
        
        try {
            Particle particle = getSafeParticle(particleType, null);
            if (particle != null) {
                plugin.getEntityBatchProcessor().addParticle(particle, location, count, spread, spread, spread, speed, null);
            }
            
            try {
                Particle dripWaterParticle = getSafeParticle("DRIP_WATER", null);
                if (dripWaterParticle != null) {
                    plugin.getEntityBatchProcessor().addParticle(dripWaterParticle, location, count/2, spread*0.8, spread*0.8, spread*0.8, 0.1, null);
                }
            } catch (Exception e) {
            }
            
            try {
                Particle bubblePopParticle = getSafeParticle("BUBBLE_POP", null);
                if (bubblePopParticle != null) {
                    plugin.getEntityBatchProcessor().addParticle(bubblePopParticle, location, count/3, spread*0.7, spread*0.7, spread*0.7, 0.05, null);
                } else {
                    Particle bubbleParticle = getSafeParticle("BUBBLE", null);
                    if (bubbleParticle != null) {
                        plugin.getEntityBatchProcessor().addParticle(bubbleParticle, location, count/3, spread*0.7, spread*0.7, spread*0.7, 0.05, null);
                    }
                }
            } catch (Exception e) {
                // 版本不支持，忽略
            }
        } catch (Exception e) {
            // 如果粒子类型不存在，使用默认值
            Particle waterSplashParticle = getSafeParticle("WATER_SPLASH", null);
            if (waterSplashParticle != null) {
                plugin.getEntityBatchProcessor().addParticle(waterSplashParticle, location, count, spread, spread, spread, speed, null);
            }
            
            // 额外添加水滴粒子增强效果
            try {
                Particle dripWaterParticle = getSafeParticle("DRIP_WATER", null);
                if (dripWaterParticle != null) {
                    plugin.getEntityBatchProcessor().addParticle(dripWaterParticle, location, count/2, spread*0.8, spread*0.8, spread*0.8, 0.1, null);
                }
            } catch (Exception ex) {
                // 版本不支持，忽略
            }
            
            // 添加气泡粒子增强视觉效果
            try {
                // 尝试使用BUBBLE_POP粒子（1.13+）
                Particle bubblePopParticle = getSafeParticle("BUBBLE_POP", null);
                if (bubblePopParticle != null) {
                    plugin.getEntityBatchProcessor().addParticle(bubblePopParticle, location, count/3, spread*0.7, spread*0.7, spread*0.7, 0.05, null);
                } else {
                    // 在1.12.2及更低版本中，BUBBLE_POP粒子不存在，尝试使用BUBBLE粒子
                    Particle bubbleParticle = getSafeParticle("BUBBLE", null);
                    if (bubbleParticle != null) {
                        plugin.getEntityBatchProcessor().addParticle(bubbleParticle, location, count/3, spread*0.7, spread*0.7, spread*0.7, 0.05, null);
                    }
                }
            } catch (Exception ex) {
                // 版本不支持，忽略
            }
        }
        
        // 播放溅水声音效果
        plugin.getSoundManager().playWaterSplashSound(location);
    }
    
    private void createLavaSplashEffect(Location location) {
        if (!config.isHookWaterSplashParticleEnabled()) return;
        
        String particleType = config.getHookWaterSplashParticleType();
        int count = config.getHookWaterSplashParticleCount();
        double spread = config.getHookWaterSplashParticleSpreadX();
        
        try {
            Particle flameParticle = getSafeParticle("FLAME", null);
            if (flameParticle != null) {
                plugin.getEntityBatchProcessor().addParticle(flameParticle, location, count, spread, spread, spread, 0.2, null);
            }
            
            Particle lavaParticle = getSafeParticle("LAVA", null);
            if (lavaParticle != null) {
                plugin.getEntityBatchProcessor().addParticle(lavaParticle, location, count / 2, spread * 0.8, spread * 0.8, spread * 0.8, 0.1, null);
            }
            
            Particle smokeParticle = getSafeParticle("SMOKE", null);
            if (smokeParticle != null) {
                plugin.getEntityBatchProcessor().addParticle(smokeParticle, location, count / 3, spread * 0.7, spread * 0.7, spread * 0.7, 0.05, null);
            }
        } catch (Exception e) {
            Particle flameParticle = getSafeParticle("FLAME", null);
            if (flameParticle != null) {
                plugin.getEntityBatchProcessor().addParticle(flameParticle, location, count, spread, spread, spread, 0.2, null);
            }
        }
        
        plugin.getSoundManager().playWaterSplashSound(location);
    }
    
    private void createVoidSplashEffect(Location location) {
        if (!config.isHookWaterSplashParticleEnabled()) return;
        
        int count = config.getHookWaterSplashParticleCount();
        double spread = config.getHookWaterSplashParticleSpreadX();
        
        try {
            Particle endRodParticle = getSafeParticle("END_ROD", null);
            if (endRodParticle != null) {
                plugin.getEntityBatchProcessor().addParticle(endRodParticle, location, count, spread, spread, spread, 0.1, null);
            }
            
            Particle dragonBreathParticle = getSafeParticle("DRAGON_BREATH", null);
            if (dragonBreathParticle != null) {
                plugin.getEntityBatchProcessor().addParticle(dragonBreathParticle, location, count / 2, spread * 0.8, spread * 0.8, spread * 0.8, 0.05, null);
            }
            
            Particle portalParticle = getSafeParticle("PORTAL", null);
            if (portalParticle != null) {
                plugin.getEntityBatchProcessor().addParticle(portalParticle, location, count / 3, spread * 0.7, spread * 0.7, spread * 0.7, 0.1, null);
            }
        } catch (Exception e) {
            Particle endRodParticle = getSafeParticle("END_ROD", null);
            if (endRodParticle != null) {
                plugin.getEntityBatchProcessor().addParticle(endRodParticle, location, count, spread, spread, spread, 0.1, null);
            }
        }
        
        plugin.getSoundManager().playWaterSplashSound(location);
    }
    
    public void scheduleBiteCheck(Player player, double chargePercentage, String baitName) {
        FileConfiguration mainConfig = config.getMainConfig();
        
        WaterType waterType = playerWaterType.get(player.getUniqueId());
        int minDelay;
        int maxDelay;
        
        if (waterType == WaterType.LAVA) {
            minDelay = config.getLavaBiteTimeMin() * 50;
            maxDelay = config.getLavaBiteTimeMax() * 50;
        } else if (waterType == WaterType.VOID) {
            minDelay = config.getVoidBiteTimeMin() * 50;
            maxDelay = config.getVoidBiteTimeMax() * 50;
        } else {
            minDelay = mainConfig.getInt("fishing-settings.bite-check-delay-min", 5000);
            maxDelay = mainConfig.getInt("fishing-settings.bite-check-delay-max", 15000);
        }

        int delay = (int) (minDelay + Math.random() * (maxDelay - minDelay) * (1 - chargePercentage / 200));
        
        final double finalChargePercentage = chargePercentage;
        final String finalBaitName = baitName;
        final UUID playerId = player.getUniqueId();

        BukkitRunnable biteTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeSessions.containsKey(playerId)) {
                    checkFishBite(player, finalChargePercentage, finalBaitName);
                }
                biteCheckTasks.remove(playerId);
            }
        };
        
        biteCheckTasks.put(playerId, biteTask);
        biteTask.runTaskLater(plugin, delay / 50);
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
        
        WaterType waterType = playerWaterType.get(player.getUniqueId());
        if (waterType == WaterType.LAVA) {
            biteRate *= config.getLavaBiteChanceMultiplier();
        } else if (waterType == WaterType.VOID) {
            biteRate *= config.getVoidBiteChanceMultiplier();
        }
        
        String rodName = minigameManager.getRodNameByPlayer(player);
        
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
            stringBuilder.setLength(0);
            stringBuilder.append("玩家 ")
                        .append(player.getName())
                        .append(" 的咬钩概率计算: 蓄力=")
                        .append(chargePercentage)
                        .append("%, 调整后=")
                        .append(biteRate)
                        .append(", 鱼饵=")
                        .append(baitName);
            config.debugLog(stringBuilder.toString());
        }
    }
    
    private void handleFishEscape(Player player) {
        me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("fish_escape", "鱼儿跑掉了..."), 40, MessageType.FISHING);
        plugin.getSoundManager().playFailSound(player.getLocation());
        
        HookMechanic mechanic = playerHookMechanic.get(player.getUniqueId());
        if (mechanic != null) {
            mechanic.playEscapeEffect(player, player.getLocation());
        }
        
        endSession(player);
    }
    
    private void showBiteHint(Player player, double chargePercentage, String baitName, double rareFishChance) {
        UUID playerId = player.getUniqueId();
        
        int hintTimeoutSeconds = plugin.getCustomConfig().getMainConfig().getInt("fishing-settings.bite-hint-timeout", 2);
        long expireTime = System.currentTimeMillis() + hintTimeoutSeconds * 1000;
        
        Location hookLocation = player.getLocation();
        ArmorStand hookEntity = activeSessions.get(playerId);
        if (hookEntity != null && hookEntity.isValid()) {
            hookLocation = hookEntity.getLocation();
        }
        
        plugin.getSoundManager().playBiteSound(hookLocation);
        
        HookMechanic mechanic = playerHookMechanic.get(playerId);
        if (mechanic != null) {
            mechanic.playBiteEffect(player, hookLocation);
        }
        
        String hintText = messageManager.getMessageWithoutPrefix("fishing_hint", "!");
        
        sendFloatingText(hookLocation, hintText, 20, 20, 20);
        
        me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("fish_bite", "有鱼咬钩了！点击右键或蹲下开始钓鱼小游戏！"), hintTimeoutSeconds * 20, MessageType.FISHING);
        
        BukkitRunnable expireTask = new BukkitRunnable() {
            @Override
            public void run() {
                removeBiteHint(playerId);
            }
        };
        
        BiteHintData data = new BiteHintData(chargePercentage, baitName, rareFishChance, expireTime, expireTask);
        biteHintDataMap.put(playerId, data);
        
        expireTask.runTaskLater(plugin, hintTimeoutSeconds * 20);
    }
    
    private void sendFloatingText(Location location, String text, int fadeInTime, int stayTime, int fadeOutTime) {
        Location spawnLocation = location.clone().add(0, 1.5, 0);
        ArmorStand floatingText = location.getWorld().spawn(spawnLocation, ArmorStand.class);
        floatingText.setVisible(false);
        floatingText.setGravity(false);
        floatingText.setMarker(true);
        floatingText.setCustomNameVisible(true);
        floatingText.setCustomName(text);
        
        final BukkitRunnable[] fadeInTaskRef = new BukkitRunnable[1];
        final org.bukkit.scheduler.BukkitTask[] fadeInTaskRefTask = new org.bukkit.scheduler.BukkitTask[1];
        
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
        fadeInTaskRefTask[0] = fadeInTaskRef[0].runTaskTimer(plugin, 0, 1);
        
        BukkitRunnable fadeOutTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (fadeInTaskRefTask[0] != null) {
                    fadeInTaskRefTask[0].cancel();
                }
                
                final BukkitRunnable[] fadeOutTaskRef = new BukkitRunnable[1];
                final org.bukkit.scheduler.BukkitTask[] fadeOutTaskRefTask = new org.bukkit.scheduler.BukkitTask[1];
                
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
                fadeOutTaskRefTask[0] = fadeOutTaskRef[0].runTaskTimer(plugin, 0, 1);
            }
        };
        fadeOutTask.runTaskLater(plugin, fadeInTime + stayTime);
    }
    
    private void sendFloatingText(Location location, String text, int duration, float scale) {
        sendFloatingText(location, text, 5, duration - 10, 5);
    }
    
    private void removeBiteHint(UUID playerId) {
        removeBiteHint(playerId, true);
    }
    
    private void removeBiteHint(UUID playerId, boolean sendEscapeMessage) {
        BiteHintData data = biteHintDataMap.remove(playerId);
        if (data != null) {
            if (data.expireTask != null) {
                data.expireTask.cancel();
            }
            
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && sendEscapeMessage) {
                me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("fish_escape", "鱼儿跑掉了..."), 40, MessageType.FISHING);
            }
        }
    }
    
    public boolean triggerMinigame(Player player) {
        UUID playerId = player.getUniqueId();
        BiteHintData data = biteHintDataMap.get(playerId);
        
        if (data != null && System.currentTimeMillis() < data.expireTime) {
            String hookedText = messageManager.getMessageWithoutPrefix("fishing_hooked", "上钩了!");
            
            Location hookLocation = player.getLocation();
            ArmorStand hookEntity = activeSessions.get(playerId);
            if (hookEntity != null && hookEntity.isValid()) {
                hookLocation = hookEntity.getLocation();
            }
            
            sendFloatingText(hookLocation, hookedText, 20, 20, 20);
            
            removeBiteHint(playerId, false);
            
            minigameManager.startMinigame(player, data.chargePercentage, data.baitName, data.rareFishChance);
            
            return true;
        }
        
        return false;
    }

    private ItemStack createOceanBackgroundItem() {
        Material glassMaterial = XSeriesUtil.getMaterial("LIGHT_BLUE_STAINED_GLASS_PANE");
        if (glassMaterial == null) {
            glassMaterial = Material.GLASS_PANE;
        }
        ItemStack glass = new ItemStack(glassMaterial);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f ");
            glass.setItemMeta(meta);
        }
        return glass;
    }

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

    private void startFishingMinigame(Player player, double chargePercentage) {
        if (minigameManager != null) {
            minigameManager.startMinigame(player, chargePercentage);
        }
    }
    
    public void handlePlayerClick(Player player) {
        if (minigameManager != null) {
            minigameManager.handlePlayerInteraction(player);
        }
    }
    
    public void endSession(Player player) {
        if (player == null) {
            return;
        }
        
        UUID playerId = player.getUniqueId();
        
        ArmorStand armorStand = activeSessions.remove(playerId);
        if (armorStand != null) {
            armorStand.remove();
        }
        
        minigameData.remove(playerId);
        chargeStartTime.remove(playerId);
        fishBitten.remove(playerId);
        
        ChargeProgressTask chargeTask = activeChargeTasks.remove(playerId);
        if (chargeTask != null) {
            chargeTask.cancel();
        }
        
        BukkitRunnable progressTask = activeProgressTasks.remove(playerId);
        if (progressTask != null) {
            progressTask.cancel();
        }
        
        BukkitRunnable biteTask = biteCheckTasks.remove(playerId);
        if (biteTask != null) {
            biteTask.cancel();
        }
        
        playerWaterType.remove(playerId);
        HookMechanic mechanic = playerHookMechanic.remove(playerId);
        if (mechanic != null) {
            ArmorStand hookEntity = armorStand;
            mechanic.cleanup(player, hookEntity);
        }
        
        if (minigameManager != null) {
            minigameManager.endGame(player);
        }
        
        setPlayerCooldown(player);
    }
    
    private void setPlayerCooldown(Player player) {
        int cooldownTime = config.getMainConfig().getInt("fishing-settings.cast-cooldown", 5000);
        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownTime);
    }
    
    private boolean isPlayerOnCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        if (!playerCooldowns.containsKey(playerId)) {
            return false;
        }
        
        long cooldownEnd = playerCooldowns.get(playerId);
        if (System.currentTimeMillis() >= cooldownEnd) {
            playerCooldowns.remove(playerId);
            return false;
        }
        
        return true;
    }
    
    private long getRemainingCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        if (!playerCooldowns.containsKey(playerId)) {
            return 0;
        }
        
        long remaining = playerCooldowns.get(playerId) - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

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
                Enchantment luckEnchant = Enchantment.getByKey(NamespacedKey.minecraft("luck_of_the_sea"));
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
        
        return fishList.get(new Random().nextInt(fishList.size()));
    }
    
    public ItemStack createFishItem(String fishName) {
        return createFishItem(fishName, false, null);
    }

    public ItemStack createFishItem(String fishName, boolean forceRarity) {
        return createFishItem(fishName, forceRarity, null);
    }

    public ItemStack createFishItem(String fishName, boolean forceRarity, Player player) {
        FileConfiguration fishConfig = config.getFishConfig();
        
        double minSize = fishConfig.getDouble("fish." + fishName + ".min-size", 20.0);
        double maxSize = fishConfig.getDouble("fish." + fishName + ".max-size", 60.0);
        double randomSize = minSize + Math.random() * (maxSize - minSize);
        
        String fishLevel = config.getRandomFishLevel(fishName);
        
        return createFishItem(fishName, forceRarity, player, randomSize, fishLevel);
    }
    
    public ItemStack createFishItem(String fishName, boolean forceRarity, Player player, double fishSize, String fishLevel) {
        return createFishItem(fishName, forceRarity, player, fishSize, fishLevel, null);
    }
    
    public ItemStack createFishItem(String fishName, boolean forceRarity, Player player, double fishSize, String fishLevel, Double preCalculatedValue) {
        FileConfiguration fishConfig = config.getFishConfig();
        
        String displayName = fishConfig.getString("fish." + fishName + ".display-name", fishName);
        String description = fishConfig.getString("fish." + fishName + ".description", plugin.getMessageManager().getMessageWithoutPrefix("fish_default_description", "一条普通的鱼"));
        String materialStr = fishConfig.getString("fish." + fishName + ".material", "COD");
        Material material;
        try {
            material = XSeriesUtil.parseMaterial(materialStr);
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("log.invalid_fish_material", "无效的鱼材质: %s, 使用默认材质COD", materialStr));
            material = XSeriesUtil.getMaterial("COD");
        }
        
        ItemStack fishItem = new ItemStack(material);
        ItemMeta meta = fishItem.getItemMeta();
        
        boolean hasCustomNBT = fishConfig.getBoolean("fish." + fishName + ".has-custom-nbt", false);
        if (hasCustomNBT && config.isCustomNBTSupportEnabled()) {
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("log.fishing_create_fish_item", "Creating fish item with custom NBT: %s").replace("%s", fishName));
        }
        
        if (meta != null) {
            displayName = displayName.replace('&', '§');
            meta.setDisplayName(displayName);
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "=" + ChatColor.WHITE + plugin.getMessageManager().getMessageWithoutPrefix("fish_info_label", "【 鱼的信息 】") + ChatColor.GRAY + "=");
            if (description.contains("\n")) {
                String[] lines = description.split("\\n");
                for (String line : lines) {
                    lore.add(ChatColor.WHITE + line);
                }
            } else {
                lore.add(ChatColor.WHITE + description);
            }
            
            double minSize = fishConfig.getDouble("fish." + fishName + ".min-size", 20.0);
            double maxSize = fishConfig.getDouble("fish." + fishName + ".max-size", 60.0);
            
            int rarity = config.getFishRarity(fishName);
            
            double sizeBonus = 1.0;
            double valueBonus = 1.0;
            String hookMaterial = "wood";
            
            if (player != null) {
                hookMaterial = plugin.getDB().getPlayerHookMaterial(player.getUniqueId().toString());
                
                switch (hookMaterial.toLowerCase()) {
                    case "wood":
                        sizeBonus = 1.05;
                        break;
                    case "stone":
                        sizeBonus = 1.1;
                        break;
                    case "iron":
                        sizeBonus = 1.15;
                        valueBonus = 1.1;
                        break;
                    case "gold":
                        sizeBonus = 1.2;
                        valueBonus = 1.2;
                        break;
                    case "diamond":
                        sizeBonus = 1.3;
                        valueBonus = 1.3;
                        break;
                    default:
                        sizeBonus = 1.05;
                        break;
                }
                
                fishSize = Math.min(fishSize * sizeBonus, maxSize);
            }
            
            if (player != null && !hookMaterial.equalsIgnoreCase("wood")) {
                String materialColor = ChatColor.GRAY.toString();
                switch (hookMaterial.toLowerCase()) {
                    case "stone":
                        materialColor = ChatColor.DARK_GRAY.toString();
                        break;
                    case "iron":
                        materialColor = ChatColor.WHITE.toString();
                        break;
                    case "gold":
                        materialColor = ChatColor.GOLD.toString();
                        break;
                    case "diamond":
                        materialColor = ChatColor.AQUA.toString();
                        break;
                }
            }
            
            int value;
            String rarityDisplayName = fishLevel;
            if (preCalculatedValue != null) {
                value = (int) Math.round(preCalculatedValue);
            } else {
                double baseValue = fishConfig.getDouble("fish." + fishName + ".value", 10.0);
                double rarityMultiplier = 1.0;
                
                String rarityName = config.getRarityNameByLevel(fishLevel);
                rarityMultiplier = config.getRarityValueMultiplier(rarityName);
                rarityDisplayName = config.getRarityDisplayName(rarityName);
                
                double finalValue = baseValue * fishSize / maxSize * rarityMultiplier * valueBonus;
                
                if (config.isDebugMode()) {
                    plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_calculation", "[Debug] 计算鱼的价值: 基础价值=%s, 鱼大小=%s, 最大大小=%s, 稀有度倍率=%s, 鱼钩材质加成=%s, 最终价值=%s", baseValue, fishSize, maxSize, rarityMultiplier, valueBonus, finalValue));
                }
                
                if (plugin.isRealisticSeasonsEnabled() && config.isSeasonalPriceFluctuationEnabled()) {
                    String currentSeason = plugin.getCurrentSeason();
                    if (currentSeason != null) {
                        double seasonalMultiplier = config.getSeasonalPriceMultiplier(currentSeason);
                        finalValue *= seasonalMultiplier;
                        
                        double baseFluctuation = config.getBasePriceFluctuation();
                        double randomFluctuation = 1.0 + (new Random().nextDouble() - 0.5) * 2 * baseFluctuation;
                        finalValue *= randomFluctuation;
                        
                        if (config.isDebugMode()) {
                            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_seasonal_price_fluctuation", "[Debug] 应用季节性价格浮动: 当前季节=%s, 季节倍率=%s, 随机浮动=%s, 浮动后价值=%s", currentSeason, seasonalMultiplier, randomFluctuation, finalValue));
                        }
                    }
                }
                
                value = (int) Math.round(finalValue);
            }
            
            String sizeQuality;
            if (fishSize < minSize + (maxSize - minSize) * 0.3) {
                sizeQuality = messageManager.getMessageWithoutPrefix("fish_size_small", "Small Fry");
            } else if (fishSize < minSize + (maxSize - minSize) * 0.7) {
                sizeQuality = messageManager.getMessageWithoutPrefix("fish_size_medium", "Medium");
            } else {
                sizeQuality = messageManager.getMessageWithoutPrefix("fish_size_large", "Large");
            }
            
            lore.clear();
            
            String templateName = config.getFishTemplateName(fishName);
            String template = config.getFishTemplate(templateName);
            
            String displayLevel = fishLevel.split(":")[0];
            
            Map<String, String> replacements = new HashMap<>();
            replacements.put("%name%", displayName);
            replacements.put("%description%", ChatColor.WHITE + description);
            replacements.put("%size%", ChatColor.GREEN + String.format("%.0f", fishSize));
            replacements.put("%size_quality%", ChatColor.GREEN + sizeQuality);
            replacements.put("%value%", ChatColor.RED + String.valueOf(value));
            replacements.put("%rarity%", ChatColor.translateAlternateColorCodes('&', rarityDisplayName));
            replacements.put("%separator%", ChatColor.GRAY + messageManager.getMessageWithoutPrefix("separator", "-------------------"));
            
            StringBuilder effectsInfo = new StringBuilder();
            List<String> effects = fishConfig.getStringList("fish." + fishName + ".effects");
            if (!effects.isEmpty()) {
                for (String effect : effects) {
                    String displayEffect = getEffectDisplayName(effect);
                    effectsInfo.append(ChatColor.WHITE + "  • " + displayEffect + "\n");
                }
                if (effectsInfo.length() > 0) {
                    effectsInfo.setLength(effectsInfo.length() - 1);
                }
            }
            replacements.put("%effects%", effectsInfo.toString());
            
            List<String> tips = messageManager.getMessageList(player, "fish_tips", new ArrayList<>());
            if (tips.isEmpty()) {
                tips = Arrays.asList("你可以在市场上出售它换取金币!", "今天你钓了多少条鱼了?", "它放在水族馆里会很好看!", "鱼肉看起来很美味!", "也许你可以用它来制作特殊药水?");
            }
            String randomTipContent = tips.get(new Random().nextInt(tips.size()));
            String randomTip = ChatColor.GRAY + "「 " + ChatColor.WHITE + randomTipContent + ChatColor.GRAY + " 」";
            replacements.put("%tip%", randomTip);
            
            String formattedLore = template;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                formattedLore = formattedLore.replace(entry.getKey(), entry.getValue());
            }
            
            formattedLore = ChatColor.translateAlternateColorCodes('&', formattedLore);
            
            String[] lines = formattedLore.split("\\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    lore.add(line);
                }
            }
            
            meta.setLore(lore);
            
            boolean enchantGlow = fishConfig.getBoolean("fish." + fishName + ".enchant-glow", false);
            if (enchantGlow) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            
            int customModelData = fishConfig.getInt("fish." + fishName + ".custom-model-data", -1);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            
            UUID fishUUID = UUID.randomUUID();
            String uuidStr = fishUUID.toString();
            
            if (config.isDebugMode()) {
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_uuid_generated", "[Debug] 生成鱼的UUID: %s", uuidStr));
            }
            
            lore.add(ChatColor.BLACK + "ID: " + uuidStr.substring(0, 8));
            meta.setLore(lore);
            
            if (config.isDebugMode()) {
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_stored", "[Debug] 存储鱼的价值到数据库: UUID=%s, 价值=%s", uuidStr, value));
            }
            
            plugin.getDB().storeFishUUIDValue(uuidStr, value);
            
            List<String> fishEffects = fishConfig.getStringList("fish." + fishName + ".effects");
            
            if (config.isDebugMode()) {
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_effects_stored", "[Debug] 存储鱼的特殊效果到数据库: UUID=%s, 效果数量=%s", uuidStr, fishEffects.size()));
            }
            
            plugin.getDB().storeFishEffects(uuidStr, fishEffects);
            
            fishItem.setItemMeta(meta);
            
            boolean uuidStored = me.kkfish.utils.NBTUtil.setNBTData(fishItem, "fish_uuid", uuidStr);
            
            if (config.isDebugMode()) {
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_uuid_stored_to_nbt", "[Debug] 存储UUID到NBT: %s", uuidStored ? "成功" : "失败"));
            }
            
            if (!uuidStored) {
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("log.fishing_uuid_nbt_failed", "Unable to store UUID in item NBT, but will continue creating item"));
            }
        }
        
        return fishItem;
    }
    
    private String getEffectDisplayName(String effect) {
        try {
            String[] parts = effect.split(" ");
            if (parts.length < 2) return effect;
              
            String effectType = parts[0];
            String[] levelDuration = parts[1].split(":");
              
            if (levelDuration.length < 2) return effect;
              
            if (config.getFishConfig().contains("effects-map")) {
                String mappedName = config.getFishConfig().getString("effects-map." + effectType);
                if (mappedName != null && !mappedName.isEmpty()) {
                    return mappedName + levelDuration[0] + " " + levelDuration[1] + "s";
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("log.fishing_effect_parse_failed", "Error parsing effect name: ") + e.getMessage());
        }
        return effect;
    }
    
    public void sendFishBroadcast(Player player, String fishName, double fishSize, int fishLevel, double fishValue) {
        boolean enabled = config.getMainConfig().getBoolean("broadcast.enabled", true);
        if (!enabled) return;
        
        String broadcastRange = config.getMainConfig().getString("broadcast.range", "global");
        
        String rarityDesc = getRarityDescription(fishLevel);
        
        String sizeDesc = getSizeDescription(fishSize);
        
        String broadcastMessage = plugin.getMessageManager().getMessage("fish_caught_broadcast", "§b[钓鱼] %player% 钓到了一条 %size%的%rarity%鱼 %fish%，价值 %value%!");
        
        broadcastMessage = broadcastMessage.replace("%player%", player.getName());
        broadcastMessage = broadcastMessage.replace("%fish%", fishName);
        broadcastMessage = broadcastMessage.replace("%size%", sizeDesc);
        broadcastMessage = broadcastMessage.replace("%rarity%", rarityDesc);
        broadcastMessage = broadcastMessage.replace("%value%", String.format("%.1f", fishValue));
        
        switch (broadcastRange.toLowerCase()) {
            case "global":
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!onlinePlayer.equals(player)) {
                        onlinePlayer.sendMessage(broadcastMessage);
                    }
                }
                break;
            case "world":
                for (Player onlinePlayer : player.getWorld().getPlayers()) {
                    if (!onlinePlayer.equals(player)) {
                        onlinePlayer.sendMessage(broadcastMessage);
                    }
                }
                break;
            case "none":
                break;
        }
    }
    
    private String getSizeDescription(double size) {
        if (size < 1.5) {
            return plugin.getMessageManager().getMessageWithoutPrefix("fish_size_small", "小鱼苗");
        } else if (size < 2.5) {
            return plugin.getMessageManager().getMessageWithoutPrefix("fish_size_medium", "中等大小");
        } else if (size < 3.5) {
            return plugin.getMessageManager().getMessageWithoutPrefix("fish_size_large", "较大");
        } else {
            return plugin.getMessageManager().getMessageWithoutPrefix("fish_size_huge", "巨大");
        }
    }
    
    private String getRarityDescription(int level) {
        if (level < 1) {
            return plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.common", "普通");
        } else if (level < 2) {
            return plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.uncommon", "优秀");
        } else if (level < 3) {
            return plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.rare", "稀有");
        } else if (level < 4) {
            return plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.epic", "史诗");
        } else {
            return plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.legendary", "传说");
        }
    }
    
    public interface AnimationCompleteCallback {
        void onAnimationComplete();
    }
    
    public void animateFishToPlayer(Player player, Location fishStartLocation, Location hookLocation, ItemStack fishItem, double fishValue, WaterType waterType, AnimationCompleteCallback callback) {
        if (!config.isFishAnimationEnabled()) {
            if (callback != null) {
                callback.onAnimationComplete();
            }
            return;
        }
        
        World world = player.getWorld();
        
        ItemStack cleanFishItem = new ItemStack(fishItem.getType(), 1);
        ItemMeta meta = cleanFishItem.getItemMeta();
        if (meta != null) {
            if (fishItem.getItemMeta().hasDisplayName()) {
                meta.setDisplayName(fishItem.getItemMeta().getDisplayName());
            }
            if (fishItem.getItemMeta().hasLore()) {
                meta.setLore(fishItem.getItemMeta().getLore());
            }
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            cleanFishItem.setItemMeta(meta);
        }
        
        Item fishEntity = world.dropItemNaturally(hookLocation, cleanFishItem);
        fishEntity.setPickupDelay(Integer.MAX_VALUE);
        
        try {
            java.lang.reflect.Method setInvulnerableMethod = fishEntity.getClass().getMethod("setInvulnerable", boolean.class);
            setInvulnerableMethod.invoke(fishEntity, true);
        } catch (Exception e) {
        }
        
        try {
            fishEntity.setFireTicks(0);
        } catch (Exception e) {
        }
        
        try {
            java.lang.reflect.Method setGlowingMethod = fishEntity.getClass().getMethod("setGlowing", boolean.class);
            setGlowingMethod.invoke(fishEntity, true);
        } catch (Exception e) {
        }
        
        try {
            java.lang.reflect.Method setGravityMethod = fishEntity.getClass().getMethod("setGravity", boolean.class);
            setGravityMethod.invoke(fishEntity, false);
        } catch (Exception e) {
        }
        
        fishEntity.setVelocity(new Vector(0, 0, 0));
        
        if (waterType == WaterType.LAVA) {
            createLavaSplashEffect(hookLocation);
        } else if (waterType == WaterType.VOID) {
            createVoidSplashEffect(hookLocation);
        } else {
            createWaterSplashEffect(hookLocation);
        }
        spawnParticleEffects(hookLocation);
        
        double distance = hookLocation.distance(player.getLocation());
        int animationTicks = (int) Math.min(40 + distance * 4, 100);
        
        new OldFishAnimationTask(this, animationTicks, fishEntity, player, callback, 
                hookLocation.getX(), hookLocation.getZ(), 0.3, hookLocation.getY(), 1.5, world, 0.6).runTaskTimer(plugin, 0, 1);
    }
    
    private static class OldFishAnimationTask extends BukkitRunnable {
        private final Fish fishingManager;
        private final Item fishEntity;
        private final Player player;
        private final AnimationCompleteCallback callback;
        private final World world;
        private final double startX;
        private final double startY;
        private final double startZ;
        
        private int ticks;
        private boolean isComplete;
        private boolean isAtHead;
        private int headStayTicks;
        private final int HEAD_STAY_TICKS;
        private final int ANIMATION_STAGES = 3;
        private final int JUMP_TO_HEAD_STAGE = 1;
        private final int STAY_AT_HEAD_STAGE = 2;
        private final int GO_TO_INVENTORY_STAGE = 3;
        private int currentStage;
        private ArmorStand floatingTextEntity;
        private final String fishName;
        private final double fishSize;

        public OldFishAnimationTask(Fish fishingManager, int maxTicks, Item fishEntity, 
                                  Player player, AnimationCompleteCallback callback, double startX, 
                                  double startZ, double peakProgress, double startY, double parabolaFactor, 
                                  World world, double maxSpeed) {
            this.fishingManager = fishingManager;
            this.fishEntity = fishEntity;
            this.player = player;
            this.callback = callback;
            this.world = world;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            
            this.ticks = 0;
            this.isComplete = false;
            this.isAtHead = false;
            this.headStayTicks = 0;
            this.currentStage = JUMP_TO_HEAD_STAGE;
            
            this.HEAD_STAY_TICKS = 40;
            
            this.fishName = parseFishName(fishEntity.getItemStack());
            this.fishSize = parseFishSize(fishEntity.getItemStack());
            
            createFloatingText();
        }
        
        private String parseFishName(ItemStack fishItem) {
            if (fishItem.hasItemMeta() && fishItem.getItemMeta().hasDisplayName()) {
                return fishItem.getItemMeta().getDisplayName();
            }
            return fishItem.getType().name();
        }
        
        private double parseFishSize(ItemStack fishItem) {
            if (fishItem.hasItemMeta() && fishItem.getItemMeta().hasLore()) {
                List<String> lore = fishItem.getItemMeta().getLore();
                for (String line : lore) {
                    try {
                        String cleanLine = org.bukkit.ChatColor.stripColor(line).trim();
                        if (cleanLine.contains("大小: ") && cleanLine.contains("cm")) {
                            int sizeStart = cleanLine.indexOf("大小: ") + 4;
                            int cmPos = cleanLine.indexOf("cm");
                            if (sizeStart > 0 && cmPos > sizeStart) {
                                String sizeStr = cleanLine.substring(sizeStart, cmPos).trim();
                                return Double.parseDouble(sizeStr);
                            }
                        }
                        if (cleanLine.matches("\\d+(\\.\\d+)?")) {
                            return Double.parseDouble(cleanLine);
                        }
                        if (cleanLine.contains("cm")) {
                            String numStr = cleanLine.replaceAll("[^\\d.]", "");
                            if (!numStr.isEmpty()) {
                                return Double.parseDouble(numStr);
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
            return 0.0;
        }
        
        private void createFloatingText() {
            Location textLoc = fishEntity.getLocation().add(0, 0.8, 0);
            floatingTextEntity = world.spawn(textLoc, ArmorStand.class);
            floatingTextEntity.setGravity(false);
            floatingTextEntity.setVisible(false);
            floatingTextEntity.setCustomNameVisible(true);
            floatingTextEntity.setCustomName(org.bukkit.ChatColor.YELLOW + fishName + " (" + String.format("%.1f", fishSize) + "cm)");
            floatingTextEntity.setMarker(true);
        }
        
        private void updateFloatingTextLocation() {
            if (floatingTextEntity != null && floatingTextEntity.isValid()) {
                floatingTextEntity.teleport(fishEntity.getLocation().add(0, 0.8, 0));
            }
        }
        
        private void removeFloatingText() {
            if (floatingTextEntity != null && floatingTextEntity.isValid()) {
                floatingTextEntity.remove();
            }
        }

        @Override
        public void run() {
            if (!this.isComplete && this.fishEntity.isValid()) {
                updateFloatingTextLocation();
                
                switch (currentStage) {
                    case JUMP_TO_HEAD_STAGE:
                        runJumpToHeadAnimation();
                        break;
                    case STAY_AT_HEAD_STAGE:
                        runStayAtHeadAnimation();
                        break;
                    case GO_TO_INVENTORY_STAGE:
                        runGoToInventoryAnimation();
                        break;
                }
                
                ++this.ticks;
            } else {
                finishAnimation();
            }
        }
        
        private void runJumpToHeadAnimation() {
            Location headLocation = player.getEyeLocation().add(0, 0.5, 0);
            
            fishEntity.setPickupDelay(Integer.MAX_VALUE);
            
            int baseDuration = fishingManager.config.getFishJumpToHeadBaseDuration();
            double distanceMultiplier = fishingManager.config.getFishJumpToHeadDistanceMultiplier();
            int maxDuration = fishingManager.config.getFishJumpToHeadMaxDuration();
            double initialJumpHeight = fishingManager.config.getFishJumpToHeadInitialJumpHeight();
            double easingFactor = fishingManager.config.getFishJumpToHeadEasingFactor();
            
            Location startLocation = new Location(world, startX, startY, startZ);
            double distance = startLocation.distance(headLocation);
            
            double totalDuration = Math.min(baseDuration + distance * distanceMultiplier, maxDuration);
            
            double progress = Math.min(ticks / totalDuration, 1.0);
            
            double smoothedProgress = 1 - Math.pow(1 - progress, easingFactor);
            
            double x = startX + (headLocation.getX() - startX) * smoothedProgress;
            double z = startZ + (headLocation.getZ() - startZ) * smoothedProgress;
            
            double startY = this.startY;
            double endY = headLocation.getY();
            double heightDifference = endY - startY;
            
            double midY = Math.max(startY, endY) + initialJumpHeight;
            double y = startY + heightDifference * smoothedProgress + initialJumpHeight * Math.sin(Math.PI * smoothedProgress);
            
            Location targetLoc = new Location(world, x, y, z);
            fishEntity.teleport(targetLoc);
            
            Particle waterSplashParticle = fishingManager.getSafeParticle("WATER_SPLASH", null);
            if (waterSplashParticle != null) {
                fishingManager.spawnSafeParticle(world, waterSplashParticle, fishEntity.getLocation(), 1, 0.1, 0.1, 0.1, 0.05, null);
            }
            
            if (progress >= 0.95 || fishEntity.getLocation().distance(headLocation) < 0.5) {
                try {
                    java.lang.reflect.Method setGravityMethod = fishEntity.getClass().getMethod("setGravity", boolean.class);
                    setGravityMethod.invoke(fishEntity, false);
                } catch (Exception e) {
                }
                fishEntity.setVelocity(new Vector(0, 0, 0));
                fishEntity.teleport(headLocation);
                
                currentStage = STAY_AT_HEAD_STAGE;
                
                Particle villagerHappyParticle = fishingManager.getSafeParticle("VILLAGER_HAPPY", null);
                if (villagerHappyParticle != null) {
                    fishingManager.spawnSafeParticle(world, villagerHappyParticle, headLocation, 10, 0.3, 0.3, 0.3, 0.1, null);
                }
            }
        }
        
        private void runStayAtHeadAnimation() {
            Location headLocation = player.getEyeLocation().add(0, 0.5, 0);
            
            fishEntity.setPickupDelay(Integer.MAX_VALUE);
            
            try {
                java.lang.reflect.Method setGravityMethod = fishEntity.getClass().getMethod("setGravity", boolean.class);
                setGravityMethod.invoke(fishEntity, false);
            } catch (Exception e) {
            }
            
            Particle villagerHappyParticle = fishingManager.getSafeParticle("VILLAGER_HAPPY", null);
            if (villagerHappyParticle != null) {
                fishingManager.spawnSafeParticle(world, villagerHappyParticle, fishEntity.getLocation(), 2, 0.2, 0.2, 0.2, 0.05, null);
            }
            
            Vector difference = headLocation.clone().subtract(fishEntity.getLocation()).toVector();
            
            Vector restoringForce = difference.clone().multiply(0.2);
            
            double floatSpeed = 0.05;
            double floatProgress = (ticks % 20) / 20.0;
            double floatOffset = 0.1 * Math.sin(floatProgress * Math.PI * 2);
            
            Location targetFloatLocation = headLocation.clone().add(0, floatOffset, 0);
            
            Vector floatDirection = targetFloatLocation.clone().subtract(fishEntity.getLocation()).toVector();
            Vector floatForce = floatDirection.multiply(0.15);
            
            double swaySpeed = 0.03;
            double swayProgress = (ticks % 30) / 30.0;
            double swayAmount = 0.05 * Math.sin(swayProgress * Math.PI * 2);
            
            float yaw = player.getLocation().getYaw();
            double radians = Math.toRadians(yaw);
            double swayX = Math.sin(radians) * swayAmount;
            double swayZ = Math.cos(radians) * swayAmount;
            
            Vector totalForce = new Vector(
                restoringForce.getX() + swayX * 0.2 + floatForce.getX(),
                restoringForce.getY() + floatForce.getY(),
                restoringForce.getZ() + swayZ * 0.2 + floatForce.getZ()
            );
            
            Random random = new Random();
            totalForce.add(new Vector(
                (random.nextDouble() - 0.5) * 0.01,
                (random.nextDouble() - 0.5) * 0.01,
                (random.nextDouble() - 0.5) * 0.01
            ));
            
            fishEntity.setVelocity(totalForce);
            
            if (ticks >= HEAD_STAY_TICKS) {
                try {
                    java.lang.reflect.Method setGravityMethod = fishEntity.getClass().getMethod("setGravity", boolean.class);
                    setGravityMethod.invoke(fishEntity, true);
                } catch (Exception e) {
                }
                
                currentStage = GO_TO_INVENTORY_STAGE;
                ticks = 0;
            }
        }
        
        private void runGoToInventoryAnimation() {
            fishEntity.setPickupDelay(Integer.MAX_VALUE);
            
            Location inventoryLocation = player.getEyeLocation();
            
            Location currentLocation = fishEntity.getLocation();
            
            double distanceToTarget = currentLocation.distance(inventoryLocation);
            
            double minDistanceThreshold = 0.1;
            
            if (distanceToTarget > minDistanceThreshold) {
                Vector direction = inventoryLocation.clone().subtract(currentLocation).toVector();
                direction.normalize();
                
                double baseSpeedFactor = 0.4;
                
                double distanceFactor = Math.min(distanceToTarget * 3, 1.0);
                double speedFactor = baseSpeedFactor * distanceFactor;
                
                speedFactor = Math.max(speedFactor, 0.1);
                
                double stageFactor = 1.0;
                if (ticks < 3) {
                    double accelerationProgress = (double)ticks / 3;
                    stageFactor = accelerationProgress * accelerationProgress * 1.5;
                } else if (distanceToTarget < 0.3) {
                    stageFactor = distanceToTarget * 3;
                }
                
                Vector velocity = direction.clone().multiply(speedFactor * stageFactor);
                
                Random random = new Random();
                velocity.add(new Vector(
                    (random.nextDouble() - 0.5) * 0.02,
                    (random.nextDouble() - 0.5) * 0.02,
                    (random.nextDouble() - 0.5) * 0.02
                ));
                
                fishEntity.setVelocity(velocity);
                
                try {
                    java.lang.reflect.Method setGravityMethod = fishEntity.getClass().getMethod("setGravity", boolean.class);
                    setGravityMethod.invoke(fishEntity, false);
                } catch (Exception e) {
                }
                
                int particleCount = 3;
                if (distanceToTarget < 0.5) {
                    particleCount = 5;
            }
            Particle itemCrackParticle = fishingManager.getSafeParticle("ITEM_CRACK", null);
            if (itemCrackParticle != null) {
                fishingManager.spawnSafeParticle(world, itemCrackParticle, fishEntity.getLocation(), particleCount, 0.1, 0.1, 0.1, 0.1, fishEntity.getItemStack());
            }
            
            Particle cloudParticle = fishingManager.getSafeParticle("CLOUD", null);
            if (cloudParticle != null) {
                fishingManager.spawnSafeParticle(world, cloudParticle, fishEntity.getLocation(), 2, 0.05, 0.05, 0.05, 0.01, null);
            }
                
                if (distanceToTarget < 0.3) {
                    try {
                        java.lang.reflect.Method setGlowingMethod = fishEntity.getClass().getMethod("setGlowing", boolean.class);
                        setGlowingMethod.invoke(fishEntity, ticks % 2 == 0);
                    } catch (Exception e) {
                    }
                } else if (ticks > 0) {
                    try {
                        java.lang.reflect.Method setGlowingMethod = fishEntity.getClass().getMethod("setGlowing", boolean.class);
                        setGlowingMethod.invoke(fishEntity, true);
                    } catch (Exception e) {
                    }
                }
            } else {
                fishEntity.setVelocity(new Vector(0, 0, 0));
                
                Particle itemCrackParticle = fishingManager.getSafeParticle("ITEM_CRACK", null);
                if (itemCrackParticle != null) {
                    fishingManager.spawnSafeParticle(world, itemCrackParticle, inventoryLocation, 8, 0.2, 0.2, 0.2, 0.1, fishEntity.getItemStack());
                }
                Particle cloudParticle = fishingManager.getSafeParticle("CLOUD", null);
                if (cloudParticle != null) {
                    fishingManager.spawnSafeParticle(world, cloudParticle, inventoryLocation, 10, 0.2, 0.2, 0.2, 0.1, null);
                }
                Particle villagerHappyParticle = fishingManager.getSafeParticle("VILLAGER_HAPPY", null);
                if (villagerHappyParticle != null) {
                    fishingManager.spawnSafeParticle(world, villagerHappyParticle, inventoryLocation, 5, 0.1, 0.1, 0.1, 0.1, null);
                }
                
                isComplete = true;
            }
        }
        
        private void finishAnimation() {
            this.cancel();
            
            removeFloatingText();
            
            fishingManager.plugin.getSoundManager().playSuccessSound(player.getLocation());
            
            Particle cloudParticle = fishingManager.getSafeParticle("CLOUD", null);
            if (cloudParticle != null) {
                fishingManager.spawnSafeParticle(world, cloudParticle, player.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1, null);
            }
            Particle villagerHappyParticle = fishingManager.getSafeParticle("VILLAGER_HAPPY", null);
            if (villagerHappyParticle != null) {
                fishingManager.spawnSafeParticle(world, villagerHappyParticle, player.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1, null);
            }
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (fishEntity.isValid()) {
                        fishEntity.remove();
                    }
                    if (callback != null) {
                        callback.onAnimationComplete();
                    }
                }
            }.runTaskLater(fishingManager.plugin, 3L);
        }
    }
    
    private Particle getSafeParticle(String particleName, Particle fallback) {
        try {
            return Particle.valueOf(particleName);
        } catch (Exception e) {
            return fallback;
        }
    }
    
    private void spawnSafeParticle(World world, Particle particle, Location location, int count, double spreadX, double spreadY, double spreadZ, double extra, Object data) {
        try {
            world.spawnParticle(particle, location, count, spreadX, spreadY, spreadZ, extra, data);
        } catch (Exception e) {
        }
    }

    private void spawnParticleTrail(Location location, int ticks) {
        try {
            World world = location.getWorld();
            if (world != null) {
                Particle waterSplashParticle = getSafeParticle("WATER_SPLASH", null);
                if (waterSplashParticle != null) {
                    spawnSafeParticle(world, waterSplashParticle, location, 1, 0.15, 0.15, 0.15, 0.02, null);
                }
                if (ticks % 8 == 0) {
                    Particle cloudParticle = getSafeParticle("CLOUD", null);
                    if (cloudParticle != null) {
                        spawnSafeParticle(world, cloudParticle, location, 1, 0.08, 0.08, 0.08, 0.01, null);
                    }
                }
            }
        } catch (Exception e) {
            try {
                World world = location.getWorld();
                if (world != null) {
                    Particle dripWaterParticle = getSafeParticle("DRIP_WATER", null);
                    if (dripWaterParticle != null) {
                        spawnSafeParticle(world, dripWaterParticle, location, 1, 0.15, 0.15, 0.15, 0.02, null);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
    
    public void animateFishToPlayer(Player player, Location fishStartLocation, ItemStack fishItem, double fishValue) {
        animateFishToPlayer(player, fishStartLocation, fishStartLocation, fishItem, fishValue, WaterType.WATER, null);
    }
    
    public void recordFishCatch(Player player, String fishName, ItemStack fishItem) {
        UUID playerId = player.getUniqueId();
        
        if (!playerFishRecords.containsKey(playerId)) {
            playerFishRecords.put(playerId, new ConcurrentHashMap<>());
        }
        
        Map<String, FishRecord> fishRecords = playerFishRecords.get(playerId);
        
        FishRecord record = fishRecords.getOrDefault(fishName, new FishRecord());
        record.incrementCount();
        fishRecords.put(fishName, record);
        
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
                    plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("log.fishing_size_parse_failed", "Failed to parse fish size: ") + line);
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
                        plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("log.fishing_value_parse_failed", "Failed to parse fish value: ") + line);
                    }
                }
            }
            
            record.updateMaxSize(fishSize);
            
            plugin.getDB().logFishing(player, fishName, fishLevel, fishSize, fishValue);
        }
        
    }
    
    public void resetFishBitten(Player player) {
        if (player != null) {
            fishBitten.put(player.getUniqueId(), false);
        }
    }
    
    public boolean isFishBitten(Player player) {
        if (player == null) {
            return false;
        }
        return fishBitten.getOrDefault(player.getUniqueId(), false);
    }
    
    public Material getPlayerHookMaterial(Player player) {
        if (player == null) {
            return XSeriesUtil.getMaterial("WHITE_WOOL");
        }
        
        UUID playerId = player.getUniqueId();
        if (!playerHookMaterials.containsKey(playerId)) {
            String materialType = plugin.getDB().getPlayerHookMaterial(playerId.toString());
            Material material = getMaterialFromType(materialType);
            playerHookMaterials.put(playerId, material);
        }
        
        return playerHookMaterials.get(playerId);
    }
    
    private Material getMaterialFromType(String type) {
        if (type == null) {
            return XSeriesUtil.getMaterial("OAK_LOG");
        }
        
        try {
            Material configMaterial = plugin.getCustomConfig().getHookMaterial(type);
            if (configMaterial != null) {
                return configMaterial;
            }
        } catch (Exception e) {
        }
        
        switch(type.toLowerCase()) {
            case "wood":
                return XSeriesUtil.getMaterial("OAK_LOG");
            case "stone":
                return XSeriesUtil.getMaterial("COBBLESTONE");
            case "iron":
                return XSeriesUtil.getMaterial("IRON_BLOCK");
            case "gold":
                return XSeriesUtil.getMaterial("GOLD_BLOCK");
            case "diamond":
                return XSeriesUtil.getMaterial("DIAMOND_BLOCK");
            case "white_wool":
                return XSeriesUtil.getMaterial("WHITE_WOOL");
            case "orange_wool":
                return XSeriesUtil.getMaterial("ORANGE_WOOL");
            case "magenta_wool":
                return XSeriesUtil.getMaterial("MAGENTA_WOOL");
            case "light_blue_wool":
                return XSeriesUtil.getMaterial("LIGHT_BLUE_WOOL");
            case "yellow_wool":
                return XSeriesUtil.getMaterial("YELLOW_WOOL");
            case "lime_wool":
                return XSeriesUtil.getMaterial("LIME_WOOL");
            case "pink_wool":
                return XSeriesUtil.getMaterial("PINK_WOOL");
            case "gray_wool":
                return XSeriesUtil.getMaterial("GRAY_WOOL");
            case "light_gray_wool":
                return XSeriesUtil.getMaterial("LIGHT_GRAY_WOOL");
            case "cyan_wool":
                return XSeriesUtil.getMaterial("CYAN_WOOL");
            case "purple_wool":
                return XSeriesUtil.getMaterial("PURPLE_WOOL");
            case "blue_wool":
                return XSeriesUtil.getMaterial("BLUE_WOOL");
            case "brown_wool":
                return XSeriesUtil.getMaterial("BROWN_WOOL");
            case "green_wool":
                return XSeriesUtil.getMaterial("GREEN_WOOL");
            case "red_wool":
                return XSeriesUtil.getMaterial("RED_WOOL");
            case "black_wool":
                return XSeriesUtil.getMaterial("BLACK_WOOL");
            default:
                try {
                    Material material = XSeriesUtil.getMaterial(type);
                    if (material != null) {
                        return material;
                    }
                } catch (Exception e) {
                }
                
                try {
                    String normalizedType = type.toUpperCase().replace("_", "");
                    Material material = XSeriesUtil.getMaterial(normalizedType);
                    if (material != null) {
                        return material;
                    }
                } catch (Exception e) {
                }
                return XSeriesUtil.getMaterial("OAK_LOG");
        }
    }
    
    public String getPlayerHookMaterialName(Player player) {
        return getPlayerHookMaterial(player).name();
    }
    
    public void setPlayerHookMaterial(Player player, Material material) {
        if (player != null && material != null) {
            UUID playerId = player.getUniqueId();
            playerHookMaterials.put(playerId, material);
            
            if (activeSessions.containsKey(playerId)) {
                ArmorStand hookEntity = activeSessions.get(playerId);
                if (hookEntity != null && !hookEntity.isDead()) {
                    hookEntity.getEquipment().setHelmet(new ItemStack(material));
                    try {
                        hookEntity.getEquipment().setHelmetDropChance(0);
                    } catch (Exception e) {
                    }
                }
            }
        }
    }
    
    public Object getActiveSession(Player player) {
        if (player == null) {
            return null;
        }
        return activeSessions.get(player.getUniqueId());
    }
    
    public WaterType getWaterType(Player player) {
        if (player == null) {
            return null;
        }
        return playerWaterType.get(player.getUniqueId());
    }
    
    private interface FishingSession {
    }
    
    private class ChargeProgressTask extends BukkitRunnable {
        private final Player player;
        private final int maxChargeTime;
        
        public ChargeProgressTask(Player player, int maxChargeTime) {
            this.player = player;
            this.maxChargeTime = maxChargeTime;
        }
        
        @Override
        public void run() {
            UUID playerId = player.getUniqueId();
            
            plugin.getCustomConfig().debugLog("ChargeProgressTask.run() 被调用 for player: " + player.getName());
            
            if (!chargeStartTime.containsKey(playerId)) {
                plugin.getCustomConfig().debugLog("玩家不再蓄力，结束任务 for player: " + player.getName());
                return;
            }
            
            long chargeTime = System.currentTimeMillis() - chargeStartTime.get(playerId);
            if (chargeTime >= maxChargeTime) {
                plugin.getCustomConfig().debugLog("蓄力时间超过最大值，停止蓄力 for player: " + player.getName());
                stopCharging(player, true);
                return;
            }
            
            if (chargeTime % 200 == 0) {
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
            me.kkfish.utils.ActionBarUtil.sendActionBar(player, barBuilder.toString());
            plugin.getCustomConfig().debugLog("ActionBar消息发送完成 for player: " + player.getName());
        }
    }
    
    private class MinigameData {
        private double greenBarPosition = 0.5;
        private double greenBarSpeed = 0;
        private double fishPosition = 0.5;
        private double progress = 0;
    }
    
    private class FishRecord {
        private int count = 0;
        private double maxSize = 0.0;
        
        public void incrementCount() {
            count++;
        }
        
        public int getCount() {
            return count;
        }
        
        public double getMaxSize() {
            return maxSize;
        }
        
        public void updateMaxSize(double size) {
            if (size > maxSize) {
                maxSize = size;
            }
        }
    }
}