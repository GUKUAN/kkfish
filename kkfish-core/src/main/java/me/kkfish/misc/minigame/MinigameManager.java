package me.kkfish.misc.minigame;

import org.bukkit.Bukkit;
import java.util.ArrayList;
import org.bukkit.Bukkit;
import java.util.LinkedHashMap;
import org.bukkit.Bukkit;
import java.util.List;
import org.bukkit.Bukkit;
import java.util.Map;
import org.bukkit.Bukkit;
import java.util.Random;
import org.bukkit.Bukkit;
import java.util.UUID;
import org.bukkit.Bukkit;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import me.kkfish.utils.NBTUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.Bukkit;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Bukkit;
import org.bukkit.potion.PotionEffect;
import org.bukkit.Bukkit;
import me.kkfish.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.Bukkit;
import me.kkfish.utils.ActionBarUtil.MessageType;
import org.bukkit.Bukkit;
import me.kkfish.managers.Compete;
import org.bukkit.Bukkit;
import me.kkfish.kkfish;
import org.bukkit.Bukkit;
import me.kkfish.managers.Config;
import org.bukkit.Bukkit;
import me.kkfish.fishing.WaterType;
import org.bukkit.Bukkit;
import me.kkfish.fishing.HookMechanicFactory;


public class MinigameManager {
    
    private final kkfish plugin;
    private final Config configManager;
    private final Map<UUID, GameSession> gameSessions = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    public MinigameManager(kkfish plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getCustomConfig();
    }
    
    private void deductRodDurabilityByFish(Player player, String fishName, ItemStack fishItem, double fishSize, String fishLevel) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        
        plugin.getFishingListener().deductRodDurability(player, rod);
    }
    
    
    
    public void startMinigame(Player player, double chargePercentage) {
        if (gameSessions.containsKey(player.getUniqueId())) return;
        startMinigame(player, chargePercentage, null);
    }
    
    public void startMinigame(Player player, double chargePercentage, String baitName) {
        if (gameSessions.containsKey(player.getUniqueId())) return;
        
        Location hookLocation = player.getLocation().clone();
        Object activeSession = plugin.getFish().getActiveSession(player);
        if (activeSession instanceof ArmorStand) {
            hookLocation = ((ArmorStand) activeSession).getLocation();
        }
        
        WaterType waterType = plugin.getFish().getWaterType(player);
        if (waterType == null) {
            waterType = WaterType.WATER;
        }
        
        GameSession session = new GameSession(plugin, player, hookLocation, waterType, chargePercentage, getRodNameByPlayer(player), baitName, 0.0);
        gameSessions.put(player.getUniqueId(), session);
        session.start();
        plugin.getSoundManager().playBiteSound(player.getLocation());
    }
    
    public void startMinigame(Player player, double chargePercentage, String baitName, double rareFishChance) {
        UUID playerId = player.getUniqueId();
        
        if (gameSessions.containsKey(playerId)) {
            return;
        }
        
        String rodName = getRodNameByPlayer(player);
        
        Location hookLocation = player.getLocation().clone();
        Object activeSession = plugin.getFish().getActiveSession(player);
        if (activeSession instanceof ArmorStand) {
            hookLocation = ((ArmorStand) activeSession).getLocation();
        }
        
        WaterType waterType = plugin.getFish().getWaterType(player);
        if (waterType == null) {
            waterType = WaterType.WATER;
        }
        
        GameSession session = new GameSession(plugin, player, hookLocation, waterType, chargePercentage, rodName, baitName, rareFishChance);
        gameSessions.put(playerId, session);
        
        session.start();
        
        plugin.getSoundManager().playBiteSound(player.getLocation());
    }
    
    public void handlePlayerInteraction(Player player) {
        UUID playerId = player.getUniqueId();
        GameSession session = gameSessions.get(playerId);
        if (session != null) {
            session.onPlayerInteraction();
        }
    }
    
    public boolean isPlayerInGame(Player player) {
        return player != null && gameSessions.containsKey(player.getUniqueId());
    }
    
    public void endGame(Player player) {
        if (!isPlayerInGame(player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        GameSession session = gameSessions.remove(playerId);
        if (session != null && !session.isCancelled()) {
            session.cancel();
        }

        cleanupSessionEntities(session);

        if (session == null) {
            return;
        }

        me.kkfish.misc.MessageManager messageManager = kkfish.getInstance().getMessageManager();
        me.kkfish.managers.Fish fishingManager = plugin.getFish();
        
        fishingManager.resetFishBitten(player);
        
        if (session.isSuccess) {
            final String fishName = session.targetFish;
            
            final double actualFishValue = session.getActualFishValue();
            final ItemStack fishItem = session.createFishItem();
            
            deductRodDurabilityByFish(player, fishName, fishItem, session.fishSize, session.fishLevel);
            
                if (player != null) {
                    Location fishStartLocation = session.hookLocation.clone();
                    fishStartLocation.add(0, 0.5, 0);
                    
                    fishingManager.animateFishToPlayer(player, fishStartLocation, session.hookLocation, fishItem, actualFishValue, session.waterType, new me.kkfish.managers.Fish.AnimationCompleteCallback() {
                    @Override
                    public void onAnimationComplete() {
                        if (player.getInventory().firstEmpty() != -1) {
                            player.getInventory().addItem(fishItem);
                            
                            final int expReward = plugin.getCustomConfig().getFishExp(fishName);
                            boolean tempHasAuraSkills = false;
                            double tempFishingXp = -1;
                            
                            try {
                                if (plugin.getAuraSkills() != null) {
                                    int fishRarity = getRarityValue(session.fishLevel);
                                    tempFishingXp = plugin.getAuraSkills().addFishingExperience(player, session.fishSize, fishRarity);
                                    tempHasAuraSkills = (tempFishingXp > 0);
                                }
                            } catch (Exception e) {
                                tempHasAuraSkills = false;
                            }
                            
                            final boolean hasAuraSkills = tempHasAuraSkills;
                            final double fishingXp = tempFishingXp;
                               
                            String rodName = getRodNameByPlayer(player);
                            List<String> rodEffects = configManager.getRodEffects(rodName);
                            if (!rodEffects.isEmpty()) {
                                applyRodEffects(player, rodEffects);
                            }
                            
                            plugin.getFish().recordFishCatch(player, fishName, fishItem);
                            
                            Compete competitionManager = plugin.getCompete();
                            if (competitionManager != null) {
                                competitionManager.recordPlayerCatch(player, fishName, actualFishValue);
                            }
                            
                            if (configManager.isFishAnnouncementEnabled(fishName)) {
                                int level = 1;
                                try {
                                    String levelName = session.fishLevel;
                                    
                                    switch (levelName.toLowerCase()) {
                                        case "legendary":
                                            level = 5;
                                            break;
                                        case "epic":
                                            level = 4;
                                            break;
                                        case "rare":
                                            level = 3;
                                            break;
                                        case "uncommon":
                                            level = 2;
                                            break;
                                        default:
                                            level = 1;
                                            break;
                                    }
                                } catch (Exception e) {
                                    kkfish.log("§e" + "处理鱼等级时出错: " + session.fishLevel);
                                }
                                fishingManager.sendFishBroadcast(player, fishName, session.fishSize, level, actualFishValue);
                            }
                            
                            List<String> fishCommands = configManager.getFishCommands(fishName);
                            if (!fishCommands.isEmpty()) {
                                executeFishCommands(player, fishCommands);
                            }
                            
                            SchedulerUtil.runEntityTaskDelayed(plugin, player, new BukkitRunnable() {
                                @Override
                                public void run() {
                                    me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player,
                                        messageManager.getMessage("catch_success", "§a你钓到了 %s§a！", fishName), 80, MessageType.MINIGAME);

                                    if (plugin.getCustomConfig().isVanillaExpEnabled() && expReward > 0) {
                                        player.giveExp(expReward);

                                        if (!hasAuraSkills) {
                                            me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player,
                                                messageManager.getMessage("exp_reward", "§a获得了 %s 经验值！", expReward), 60, MessageType.MINIGAME);
                                        }
                                    }

                                    if (hasAuraSkills && fishingXp > 0) {
                                        me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player,
                                            messageManager.getMessage("fishing_exp_reward", "§b获得了 %s 钓鱼经验！", String.format("%.1f", fishingXp)), 60, MessageType.MINIGAME);
                                    }
                                }
                            }, 5);
                        } else {
                            SchedulerUtil.runEntityTaskDelayed(plugin, player, new BukkitRunnable() {
                                @Override
                                public void run() {
                                    me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player,
                                        messageManager.getMessage("inventory_full", "§c背包已满，鱼掉落在地！"), 60, MessageType.MINIGAME);
                                }
                            }, 5);
                            
                            player.getWorld().dropItemNaturally(player.getLocation(), fishItem);
                        }
                        
                        plugin.getFish().endSession(player);
                    }
                });
            }
        } else {
            SchedulerUtil.runEntityTaskDelayed(plugin, player, new BukkitRunnable() {
                @Override
                public void run() {
                    me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player,
                        messageManager.getMessage("fish_escape", "§c鱼儿跑掉了..."), 60, MessageType.MINIGAME);
                }
            }, 5);
        }
        
        if (session.isSuccess) {
        } else {
            plugin.getSoundManager().playFailSound(player.getLocation());
        }
    }
    
        private void executeFishCommands(Player player, List<String> commands) {
            for (String cmd : commands) {
                if (cmd.startsWith("-o")) {
                    String actualCmd = cmd.substring(2).trim().replace("%player%", player.getName());
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), actualCmd);
                } else if (cmd.startsWith("-p")) {
                    String actualCmd = cmd.substring(2).trim().replace("%player%", player.getName());
                    plugin.getServer().dispatchCommand(player, actualCmd);
                } else if (cmd.startsWith("-c")) {
                    String actualCmd = cmd.substring(2).trim().replace("%player%", player.getName());
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), actualCmd);
                }
            }
        }
        
        private void cleanupSessionEntities(GameSession session) {
            if (session != null && session.player != null) {
                session.player.resetTitle();
            }
        }
        
        private String getRandomFish() {
            List<String> fishList = plugin.getCustomConfig().getAllFishNames();
            if (fishList.isEmpty()) {
                return plugin.getMessageManager().getMessageWithoutPrefix("fish_unknown", "未知鱼");
            }
            return fishList.get(random.nextInt(fishList.size()));
        }
        
        private int getRarityValue(String fishLevel) {
            if (fishLevel == null) {
                return 1;
            }
            switch (fishLevel.toLowerCase()) {
                case "legendary":
                    return 5;
                case "epic":
                    return 4;
                case "rare":
                    return 3;
                case "uncommon":
                    return 2;
                default:
                    return 1;
            }
        }
        
    public String getRodNameByPlayer(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item == null || item.getType() == me.kkfish.utils.XSeriesUtil.parseMaterial("AIR")) {
            configManager.debugLog("玩家" + player.getName() + "没有手持物品");
            return "wood";
        }
        
        if (item.getType() == me.kkfish.utils.XSeriesUtil.parseMaterial("FISHING_ROD")) {
            configManager.debugLog("玩家" + player.getName() + "手持原版钓鱼竿");
            return "default_rod";
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            configManager.debugLog("玩家" + player.getName() + "手持物品无元数据: " + item.getType());
            return "wood";
        }
        
        String playerName = player.getName();
        String itemType = item.getType().toString();
        configManager.debugLog("玩家" + playerName + "手持物品: " + itemType + ", 有描述: " + meta.hasLore());
        
        Object rodNbt = NBTUtil.getNBTData(item, "kkfish_rod");
        if (rodNbt != null && configManager.rodExists(rodNbt.toString())) {
            return rodNbt.toString();
        }

        if (isCustomRod(item, meta, playerName)) {
            return "default_rod";
        }

        configManager.debugLog("玩家" + playerName + "手持物品不是有效鱼竿: " + itemType);
        return "wood";
    }
    private boolean isCustomRod(ItemStack item, ItemMeta meta, String playerName) {
        boolean hasRodLore = false;
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (String line : lore) {
                if (line != null && !line.isEmpty()) {
                    String strippedLine = ChatColor.stripColor(line);
                    if (strippedLine.contains("[鱼竿: ")) {
                        hasRodLore = true;
                        break;
                    }
                }
            }
        }
        
        if (!hasRodLore) {
            return false;
        }
        
        try {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey tagsKey = new NamespacedKey(plugin, "Tags");
            
            if (container.has(tagsKey, PersistentDataType.STRING)) {
                String tagsString = container.get(tagsKey, PersistentDataType.STRING);
                if (tagsString != null && tagsString.contains("自定义鱼竿")) {
                    configManager.debugLog("玩家" + playerName + "手持自定义钓鱼竿(类型: " + item.getType() + ")，已验证NBT标签");
                    return true;
                }
            }
        } catch (Exception e) {
            configManager.debugLog("检查NBT Tags时出错: " + e.getMessage());
        }
        
        return false;
    }
    
    private void applyRodEffects(Player player, List<String> effects) {
        for (String effectStr : effects) {
            try {
                String[] parts = effectStr.trim().split(" ");
                if (parts.length < 2) continue;
                
                String effectName = parts[0].toUpperCase();
                String[] levelDuration = parts[1].split(":");
                if (levelDuration.length < 2) continue;
                
                int level = Integer.parseInt(levelDuration[0]);
                int duration = Integer.parseInt(levelDuration[1]);
                
                org.bukkit.potion.PotionEffectType effectType = org.bukkit.potion.PotionEffectType.getByName(effectName);
                if (effectType != null) {
                    PotionEffect effect = new PotionEffect(effectType, duration * 20, level - 1);
                    player.addPotionEffect(effect);
                }
            } catch (Exception e) {
                kkfish.log("§e" + "解析鱼竿特效失败: " + effectStr);
            }
        }
    }
}
