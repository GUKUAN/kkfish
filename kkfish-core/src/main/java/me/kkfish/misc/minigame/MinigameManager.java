package me.kkfish.misc.minigame;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import me.kkfish.utils.SchedulerUtil;
import org.bukkit.scheduler.BukkitRunnable;

import me.kkfish.utils.ActionBarUtil.MessageType;
import me.kkfish.kkfish;
import me.kkfish.managers.Config;
import me.kkfish.fishing.WaterType;


public class MinigameManager {
    
    private final kkfish plugin;
    private final Config configManager;
    private final me.kkfish.events.EventBus eventBus;
    private final Map<UUID, GameSession> gameSessions = new ConcurrentHashMap<>();
    private final Random random = new Random();
    
    public MinigameManager(kkfish plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getCustomConfig();
        this.eventBus = plugin.getEventBus();
    }
    
    private void deductRodDurabilityByFish(Player player, String fishName, ItemStack fishItem, double fishSize, String fishLevel) {
        ItemStack rod = configManager.getPlayerRod(player);
        
        plugin.getFishingListener().deductRodDurability(player, rod);
    }
    
    
    
    public void startMinigame(Player player, double chargePercentage) {
        if (gameSessions.containsKey(player.getUniqueId())) return;
        startMinigame(player, chargePercentage, null);
    }
    
    public void startMinigame(Player player, double chargePercentage, String baitName) {
        if (gameSessions.containsKey(player.getUniqueId())) return;
        startMinigame(player, chargePercentage, baitName, 0.0);
    }
    
    public void startMinigame(Player player, double chargePercentage, String baitName, double rareFishChance) {
        UUID playerId = player.getUniqueId();
        
        if (gameSessions.containsKey(playerId)) {
            return;
        }
        
        String rodName = getRodNameByPlayer(player);
        
        Object activeSession = plugin.getFish().getActiveSession(player);
        Location hookLocation;
        if (activeSession instanceof ArmorStand) {
            hookLocation = ((ArmorStand) activeSession).getLocation();
        } else {
            // 钩子实体不可用时，取玩家朝向方向估算钩子位置
            hookLocation = player.getEyeLocation().add(player.getLocation().getDirection().multiply(5));
            configManager.debugLog(plugin.getMessageManager().getMessageWithoutPrefix("debug.minigame_hook_fallback", "Hook entity unavailable, using estimated position for player: %s", player.getName()));
        }
        
        WaterType waterType = plugin.getFish().getWaterType(player);
        if (waterType == null) {
            waterType = WaterType.WATER;
        }
        
        List<String> candidates = configManager.getAvailableFishFromPool(waterType, hookLocation);
        if (candidates.isEmpty()) {
            player.sendMessage(plugin.getMessageManager().getMessage(player, "no_available_fish", "§eNo fish can be caught in this pool under the current conditions."));
            plugin.getFish().endSession(player);
            return;
        }
        GameSession session = null;
        try {
            session = new GameSession(plugin, player, hookLocation, waterType, chargePercentage, rodName, baitName, rareFishChance, candidates);
            gameSessions.put(playerId, session);
            session.start();
        } catch (RuntimeException e) {
            gameSessions.remove(playerId);
            if (session != null) session.cancel();
            plugin.getFish().endSession(player);
            kkfish.log("§e启动钓鱼小游戏失败: " + e.getMessage());
            player.sendMessage(plugin.getMessageManager().getMessage(player, "minigame_start_failed", "§cUnable to start the fishing minigame. Please try again later."));
            return;
        }
        
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
            if (fishItem == null) {
                player.sendMessage(messageManager.getMessage(player, "no_available_fish", "§eNo fish can be caught in this pool under the current conditions."));
                fishingManager.endSession(player);
                return;
            }
            final String fishDisplayName = fishItem.hasItemMeta() && fishItem.getItemMeta().hasDisplayName()
                ? fishItem.getItemMeta().getDisplayName() : fishName;
            
            deductRodDurabilityByFish(player, fishName, fishItem, session.fishSize, session.fishLevel);
            
                if (player != null) {
                    Location fishStartLocation = session.hookLocation.clone();
                    
                    fishingManager.animateFishToPlayer(player, fishStartLocation, session.hookLocation, fishItem, actualFishValue, session.waterType, new me.kkfish.managers.Fish.AnimationCompleteCallback() {
                    @Override
                    public void onAnimationComplete() {
                        if (!player.isOnline()) {
                            plugin.getFish().endSession(player);
                            return;
                        }
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(fishItem);
                        if (!overflow.isEmpty()) {
                            for (ItemStack item : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), item);
                            player.sendMessage(messageManager.getMessage("inventory_full", "§c背包已满，鱼掉落在地！"));
                        }
                            
                            final int expReward = plugin.getCustomConfig().getFishExp(fishName);
                            boolean tempHasAuraSkills = false;
                            double tempFishingXp = -1;
                            
                            try {
                                tempFishingXp = fishingManager.giveFishingExperience(player, fishName, session.fishSize,
                                        session.fishSizeMin, session.fishSizeMax, session.fishLevel);
                                tempHasAuraSkills = tempFishingXp > 0;
                             } catch (Exception e) {
                                 tempHasAuraSkills = false;
                                 kkfish.log("§e发放钓鱼经验失败: " + e.getMessage());
                            }
                            
                            final boolean hasAuraSkills = tempHasAuraSkills;
                            final double fishingXp = tempFishingXp;
                               
                            String rodName = session.getRodName();
                            List<String> rodEffects = configManager.getRodEffects(rodName);
                            if (!rodEffects.isEmpty()) {
                                applyRodEffects(player, rodEffects);
                            }
                            
                            // 捕获事件携带原始数据，记录不再从描述文字反向解析。
                            if (eventBus != null) {
                                boolean announce = configManager.isFishAnnouncementEnabled(fishName);
                                int fishRarity = getRarityValue(session.fishLevel);
                                eventBus.publish(new me.kkfish.events.FishCaughtEvent(
                                    player, fishName, fishDisplayName, session.fishSize, session.fishLevel,
                                    fishRarity, actualFishValue, fishItem, announce));
                            }
                            
                            List<String> fishCommands = configManager.getFishCommands(fishName);
                            if (!fishCommands.isEmpty()) {
                                executeFishCommands(player, fishCommands);
                            }
                            
                            SchedulerUtil.runEntityTaskDelayed(plugin, player, new BukkitRunnable() {
                                @Override
                                public void run() {
                                    me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player,
                                        messageManager.getMessage("catch_success", "§a你钓到了 %s§a！", fishDisplayName), 80, MessageType.MINIGAME);

                                    if (plugin.getCustomConfig().isVanillaExpEnabled() && expReward > 0) {
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
                        recordFishingStats(player, false);
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
            // 小游戏失败也要清理钓鱼会话，否则玩家无法再次钓鱼
            recordFishingStats(player, true);
            plugin.getFish().endSession(player);
        }
        
        if (session.isSuccess) {
        } else {
            plugin.getSoundManager().playFailSound(player.getLocation());
        }
    }
    
    /**
     * 记录钓鱼统计（总次数 + 可选失败次数）。
     *
     * @param player 玩家
     * @param failed 是否为失败结算
     */
    private void recordFishingStats(Player player, boolean failed) {
        me.kkfish.player.PlayerContextStore store = plugin.getPlayerContextStore();
        if (store == null) {
            return;
        }
        me.kkfish.player.PlayerContext ctx = store.getUsableContext(player.getUniqueId());
        if (ctx == null) {
            return;
        }
        ctx.getPersistent().recordAttempt();
        if (failed) {
            ctx.getPersistent().recordFail();
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
            return configManager.getRarityOrder(configManager.getRarityNameByLevel(fishLevel));
        }
        
    public String getRodNameByPlayer(Player player) {
        String id = configManager.getRodType(configManager.getPlayerRod(player));
        return id == null ? "wood" : id;
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
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.minigame_rod_effect_parse_failed", "§e解析鱼竿特效失败: " + effectStr, effectStr));
            }
        }
    }
}
