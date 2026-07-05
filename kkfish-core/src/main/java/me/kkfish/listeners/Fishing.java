package me.kkfish.listeners;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Scoreboard;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.kkfish.competition.CompetitionConfig;
import me.kkfish.managers.Compete;
import me.kkfish.kkfish;
import me.kkfish.managers.Fish;
import me.kkfish.managers.Config;
import me.kkfish.managers.GUI;
import me.kkfish.managers.DB;
import me.kkfish.misc.MessageManager;
import me.kkfish.misc.SoundManager;
import me.kkfish.misc.minigame.MinigameManager;
import me.kkfish.utils.ActionBarUtil;
import me.kkfish.utils.ActionBarUtil.MessageType;
import me.kkfish.utils.XSeriesUtil;

public class Fishing implements Listener {

    private final kkfish plugin;
    private final Fish fish;
    private final SoundManager soundManager;
    private final MessageManager messageManager;


    public Fishing(kkfish plugin) {
        this.plugin = plugin;
        this.fish = plugin.getFish();
        this.soundManager = plugin.getSoundManager();
        this.messageManager = plugin.getMessageManager();

    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        String worldName = player.getWorld().getName();

        if (!plugin.getCustomConfig().isWorldAllowed(worldName)) {
            return;
        }

        if (!player.hasPermission("kkfish.use")) {
            event.setCancelled(true);
            return;
        }

        boolean isVanillaMode = plugin.isPlayerInVanillaMode(player.getUniqueId());

        if (!isVanillaMode) {
            if (plugin.getCustomConfig().isVanillaFishingDisabled()) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getState().name().equals("CAUGHT_ITEM")) {
            if (!plugin.getCustomConfig().isVanillaModeGiveCustomFish()) {
                return;
            }

            event.setCancelled(true);
            plugin.getFish().processVanillaCatch(player);
        }
    }
    

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 创建并加载玩家上下文
        me.kkfish.player.PlayerContextStore store = plugin.getPlayerContextStore();
        if (store != null) {
            store.onPlayerJoin(player);
        }

        Compete compete = plugin.getCompete();
        

        for (String competitionId : compete.getActiveCompetitionIds()) {
            CompetitionConfig config = compete.getCompetitionConfig(competitionId);
            if (config != null && config.getDisplayConfig().isScoreboardEnabled()) {

                Scoreboard scoreboard = compete.getScoreboard(competitionId);
                if (scoreboard != null) {
                    player.setScoreboard(scoreboard);
                } else {
            }
                break;
            }
        }
    }
    
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Fish fish = plugin.getFish();
        MessageManager messageManager = kkfish.getInstance().getMessageManager();
    

        if (fish.getActiveSession(player) != null) {
            fish.endSession(player);
            me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("stop_fishing", "你停止了钓鱼。"), 40, MessageType.FISHING);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        plugin.getFish().endSession(player);
        
        plugin.clearPlayerFishingMode(playerId);
        
        Compete compete = plugin.getCompete();
        if (compete != null) {
    
            for (String competitionId : compete.getActiveCompetitionIds()) {
                Scoreboard scoreboard = compete.getScoreboard(competitionId);
                if (scoreboard != null && player.getScoreboard().equals(scoreboard)) {
                    Scoreboard defaultScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
                    player.setScoreboard(defaultScoreboard);
                    break;
                }
            }
        }
        

        me.kkfish.utils.ActionBarUtil.cancelPersistentMessage(plugin, player);
        

        DB db = plugin.getDB();
        if (db != null) {
            db.clearPlayerCache(player.getUniqueId().toString());
        }
        

        GUI gui = plugin.getGUI();
        if (gui != null) {
            gui.handlePlayerQuit(player);
        }

        // 保存并销毁玩家上下文（在所有清理之后）
        me.kkfish.player.PlayerContextStore store = plugin.getPlayerContextStore();
        if (store != null) {
            store.onPlayerQuit(player);
        }
    }
    

                    


    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Fish fish = plugin.getFish();
        

        if (fish.getActiveSession(player) != null) {
            Location from = event.getFrom();
            Location to = event.getTo();
            
            if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ() || from.getY() != to.getY())) {
                event.setCancelled(true);
            }
        }
    }
    



    @EventHandler
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        Fish fish = plugin.getFish();
        
        if (fish != null) {
            if (fish.isPlayerInMinigame(player.getUniqueId())) {
                if (event.isSprinting()) {
                    fish.handlePlayerClick(player);
                }
            }
        }
    }
    
    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        Fish fish = plugin.getFish();
        
        if (fish != null) {
            boolean triggeredMinigame = fish.triggerMinigame(player);
            if (triggeredMinigame) {
                return;
            }
        }
        
        String worldName = player.getWorld().getName();
        
        if (!plugin.getCustomConfig().isWorldAllowed(worldName)) {
            return;
        }

        if (!player.hasPermission("kkfish.use")) {
            return;
        }

        if (plugin.isPlayerInVanillaMode(player.getUniqueId())) {
            return;
        }

        if (plugin.getCmd().hasFishingRod(player)) {
            ItemStack rod = player.getInventory().getItemInMainHand();
                
            if (fish.isPlayerInMinigame(player.getUniqueId())) {
                return;
            }
                
            if (event.isSneaking()) {
                if (fish.getActiveSession(player) == null && 
                    !fish.hasActiveChargeTask(player.getUniqueId())) {
                    if (checkRodDurability(player, rod)) {
                        fish.startCharging(player);
                    }
                }
            } else {
                if (fish.hasActiveChargeTask(player.getUniqueId())) {
                    fish.stopCharging(player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Fish fish = plugin.getFish();

        if (fish != null && fish.isPlayerInMinigame(player.getUniqueId())) {
            event.setCancelled(true);
            fish.handlePlayerClick(player);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Fish fish = plugin.getFish();
        MessageManager messageManager = kkfish.getInstance().getMessageManager();
        
        if (fish != null) {
            if (fish.isPlayerInMinigame(player.getUniqueId())) {
                event.setCancelled(true);
                fish.handlePlayerClick(player);
                return;
            }
            
            boolean triggeredMinigame = fish.triggerMinigame(player);
            if (triggeredMinigame) {
                event.setCancelled(true);
                return;
            }
        }
        
        String worldName = player.getWorld().getName();
        
        if (!plugin.getCustomConfig().isWorldAllowed(worldName)) {
            return;
        }

        if (!player.hasPermission("kkfish.use")) {
            if (plugin.getCmd().hasFishingRod(player)) {
                event.setCancelled(true);
            }
            return;
        }

        if (plugin.isPlayerInVanillaMode(player.getUniqueId())) {
            return;
        }
        
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) && 
            plugin.getCmd().hasFishingRod(player)) {
            
            ItemStack rod = player.getInventory().getItemInMainHand();
                
            event.setCancelled(true);
            
            if (fish.getActiveSession(player) != null) {
                me.kkfish.utils.ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("reeling_in", "正在收线..."), 40, MessageType.FISHING);
                plugin.getSoundManager().playReelSound(player.getLocation());
                fish.endSession(player);
            }
        }
    }
    

    private boolean checkRodDurability(Player player, ItemStack rod) {
        Config config = plugin.getCustomConfig();
        MessageManager messageManager = kkfish.getInstance().getMessageManager();
        
        String rodType = config.getRodType(rod);
        if (rodType == null) {
            return true;
        }
        int maxDurability = config.getRodDurability(rodType);
        
        if (maxDurability <= 0) {
            return true;
        }
        
        int currentDurability = getRodCurrentDurability(rod);
        
        if (currentDurability <= 0) {
            player.sendMessage(messageManager.getMessage("rod_broken", "你的钓鱼竿已经损坏了！"));
            return false;
        }
        
        return true;
    }
    

    public void deductRodDurability(Player player, ItemStack rod) {
        Config config = plugin.getCustomConfig();
        MessageManager messageManager = kkfish.getInstance().getMessageManager();
        
        String rodType = config.getRodType(rod);
        int maxDurability = config.getRodDurability(rodType);
        
        if (maxDurability <= 0) {
            return;
        }
        
        int currentDurability = getRodCurrentDurability(rod);
        
        FileConfiguration mainConfig = config.getMainConfig();
        int baseLoss = mainConfig.getInt("fishing-settings.durability.base-loss", 1);
        int maxSingleLoss = mainConfig.getInt("fishing-settings.durability.max-single-loss", 5);
        
        if (maxSingleLoss < baseLoss) {
            int temp = maxSingleLoss;
            maxSingleLoss = baseLoss;
            baseLoss = temp;
        }
        
        int durabilityLoss = baseLoss + (int) (Math.random() * (maxSingleLoss - baseLoss + 1));
        
        if (config.isDebugMode()) {
            config.debugLog("玩家 " + player.getName() + " 使用的 " + rodType + " 扣除耐久度: " + durabilityLoss);
        }
        
        currentDurability -= durabilityLoss;
        
        currentDurability = Math.max(currentDurability, 0);
        
        short baseDurability = 100;
        int durabilityValue = (int) Math.round((1.0 - (double)currentDurability / maxDurability) * baseDurability);
        durabilityValue = Math.max(0, Math.min(baseDurability, durabilityValue));
        short newDurabilityBukkit = (short) durabilityValue;
        rod.setDurability(newDurabilityBukkit);
        
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new NamespacedKey(plugin, "rod_durability"), PersistentDataType.INTEGER, currentDurability);
            
            String templateName = config.getRodTemplateName(rodType);
            String template = config.getRodTemplate(templateName);
            if (template == null || template.isEmpty()) {
                template = "&6[===== 鱼竿属性 =====]\n" +
                          "&b│ 难度系数: %difficulty%\n" +
                          "&a│ 浮标区域: %float_area%\n" +
                          "&c│ %rod_durability%: %durability%\n" +
                          "&d│ 充能速度: %charge_speed%\n" +
                          "&d│ 咬钩几率加成: %bite_rate_bonus%\n" +
                          "&6[====================]\n" +
                          " \n" +
                          "&e✨ 特殊效果:\n" +
                          "%effects%\n" +
                          " \n" +
                          "&7钓鱼快乐~";
            }
            
            java.util.Map<String, String> variables = new java.util.HashMap<>();
            variables.put("%name%", meta.getDisplayName());
            variables.put("%difficulty%", String.valueOf(config.getRodDifficulty(rodType)));
            variables.put("%float_area%", String.valueOf(config.getRodFloatAreaSize(rodType)));
            variables.put("%rod_durability%", messageManager.getMessageWithoutPrefix("rod_durability", "耐久度"));
            
            if (maxDurability > 0) {
                String unit = messageManager.getMessageWithoutPrefix("rod_durability_unit", "点");
                int progressBarLength = 16;
                double progressPercentage = (double) currentDurability / maxDurability;
                int filledLength = (int) (progressBarLength * progressPercentage);
                ChatColor durabilityColor = ChatColor.GREEN;
                if (progressPercentage < 0.2) {
                    durabilityColor = ChatColor.RED;
                } else if (progressPercentage < 0.5) {
                    durabilityColor = ChatColor.YELLOW;
                }
                
                StringBuilder barBuilder = new StringBuilder();
                barBuilder.append(" ");
                barBuilder.append(ChatColor.GRAY).append("[");
                barBuilder.append(durabilityColor);
                for (int i = 0; i < filledLength; i++) {
                    barBuilder.append('|');
                }
                barBuilder.append(ChatColor.DARK_GRAY);
                for (int i = filledLength; i < progressBarLength; i++) {
                    barBuilder.append('|');
                }
                barBuilder.append(ChatColor.GRAY);
                barBuilder.append(" ]");
                
                variables.put("%durability%", currentDurability + unit + barBuilder.toString());
            } else {
                variables.put("%durability%", messageManager.getMessageWithoutPrefix("rod_durability_infinite", "无限耐久"));
            }
            
            double chargeSpeed = config.getRodChargeSpeed(rodType);
            String speedText;
            if (chargeSpeed != 1.0) {
                String speedType = chargeSpeed > 1.0 ? 
                        messageManager.getMessageWithoutPrefix("rod_charge_speed_fast", "加速") : 
                        messageManager.getMessageWithoutPrefix("rod_charge_speed_slow", "减速");
                speedText = String.format("%.1f倍 (" + speedType + ")", chargeSpeed);
            } else {
                speedText = messageManager.getMessageWithoutPrefix("rod_charge_speed_normal", "正常");
            }
            variables.put("%charge_speed%", speedText);
            
            double biteRateBonus = config.getRodBiteRateBonus(rodType);
            variables.put("%bite_rate_bonus%", biteRateBonus > 0 ? 
                    String.format("+%.1f%%", biteRateBonus * 100) : 
                    messageManager.getMessageWithoutPrefix("rod_bite_rate_bonus_none", "无"));
            
            java.util.List<String> effects = config.getRodEffects(rodType);
            StringBuilder effectsBuilder = new StringBuilder();
            if (!effects.isEmpty()) {
                for (String effect : effects) {
                    effectsBuilder.append("&7  └─ &r").append(org.bukkit.ChatColor.translateAlternateColorCodes('&', effect)).append("\n");
                }
            } else {
                effectsBuilder.append("&7  └─ " + messageManager.getMessageWithoutPrefix("rod_effects_none", "无特殊效果") + "\n");
            }
            variables.put("%effects%", effectsBuilder.toString());
            
            String formattedTemplate = template;
            for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
                formattedTemplate = formattedTemplate.replace(entry.getKey(), entry.getValue());
            }
            
            java.util.List<String> newLore = new java.util.ArrayList<>();
            String[] lines = formattedTemplate.split("\\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    newLore.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', line));
                } else {
                    newLore.add("");
                }
            }
            
            meta.setLore(newLore);
            rod.setItemMeta(meta);
            player.getInventory().setItemInMainHand(rod);
        }
        
        if (currentDurability <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.sendMessage(messageManager.getMessage("rod_broken", "你的钓鱼竿已经损坏了！"));
            return;
        }
        
        if (currentDurability <= maxDurability * 0.2) {
            player.sendMessage(messageManager.getMessage("rod_low_durability", "钓鱼竿耐久度警告：当前耐久度只有%d点，请注意及时修复或更换！", currentDurability));
        }
    }
    

    private int getRodCurrentDurability(ItemStack rod) {
        Config config = plugin.getCustomConfig();
        
        String rodType = config.getRodType(rod);
        if (rodType == null) {
            return -1;
        }
        int maxDurability = config.getRodDurability(rodType);
        
        if (maxDurability <= 0) {
            return Integer.MAX_VALUE;
        }
        
        ItemMeta meta = rod.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            NamespacedKey key = new NamespacedKey(plugin, "rod_durability");
            if (pdc.has(key, PersistentDataType.INTEGER)) {
                return Math.max(0, pdc.get(key, PersistentDataType.INTEGER));
            }
        }
        
        int baseDurability = 100;
        short currentDurabilityBukkit = rod.getDurability();
        
        int currentDurability = (int) Math.round(maxDurability * (1.0 - (double)currentDurabilityBukkit / baseDurability));
        
        return Math.max(0, currentDurability);
    }
    

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Fish fish = plugin.getFish();
        
        if (fish.isPlayerInMinigame(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        
        if (fish.hasActiveChargeTask(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    

    @EventHandler
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasLore()) {
            event.setCancelled(true);
            
            try {
                ItemStack mainHandItem = player.getInventory().getItemInMainHand().clone();
                
                if (mainHandItem != null && mainHandItem.getType() != org.bukkit.Material.AIR && mainHandItem.isSimilar(item)) {
                    int newAmount = mainHandItem.getAmount() - 1;
                    
                    ItemStack updatedItem = mainHandItem.clone();
                    
                    if (newAmount > 0) {
                        updatedItem.setAmount(newAmount);
                        player.getInventory().setItemInMainHand(updatedItem);
                    } else {
                        player.getInventory().setItemInMainHand(null);
                    }
                }
            } catch (Exception e) {
                kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("fishing_eat_error", "吃鱼时出错了: %s", e.getMessage()));
            }
            
            String fishName = null;
            
            String displayName = meta.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                fishName = ChatColor.stripColor(displayName);
            }
            
            String fishUUID = null;
            try {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                NamespacedKey fishUUIDKey = new NamespacedKey(plugin, "fish_uuid");
                if (pdc.has(fishUUIDKey, PersistentDataType.STRING)) {
                    fishUUID = pdc.get(fishUUIDKey, PersistentDataType.STRING);
                }
            } catch (NoSuchMethodError e) {
                try {
                    Object nbtData = me.kkfish.utils.NBTUtil.getNBTData(item, "fish_uuid");
                    if (nbtData != null) {
                        fishUUID = nbtData.toString();
                    }
                } catch (Exception ex) {
                }
            }
            
            int foodLevelIncrease = 4;
            int saturationIncrease = 2;
            
            if (fishName != null) {
                saturationIncrease = plugin.getCustomConfig().getFishSaturation(fishName);
            }
            
            int newFoodLevel = Math.min(20, player.getFoodLevel() + foodLevelIncrease);
            player.setFoodLevel(newFoodLevel);
            
            float newSaturation = Math.min((float)newFoodLevel, player.getSaturation() + (float)saturationIncrease);
            player.setSaturation(newSaturation);
            
            if (fishUUID != null) {
                List<String> effects = plugin.getDB().getFishEffectsByUUID(fishUUID);
                if (!effects.isEmpty()) {
                    for (String effectStr : effects) {
                        try {
                            String[] parts = effectStr.split(" ");
                            if (parts.length < 2) continue;
                               
                            String effectType = parts[0];
                            String[] levelDuration = parts[1].split(":");
                               
                            if (levelDuration.length < 2) continue;
                               
                            int level = Integer.parseInt(levelDuration[0]);
                            int duration = Integer.parseInt(levelDuration[1]) * 20;
                               
                            PotionEffectType potionEffectType = PotionEffectType.getByName(effectType);
                            if (potionEffectType != null) {
                                PotionEffect effect = new PotionEffect(potionEffectType, duration, level - 1);
                                player.addPotionEffect(effect);
                            }
                        } catch (Exception e) {
                            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("fishing_effect_parse_failed", "解析鱼特效失败: %s - %s", effectStr, e.getMessage()));
                        }
                    }
                }
            }
        }
    }
    

}