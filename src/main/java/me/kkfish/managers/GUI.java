package me.kkfish.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.kkfish.managers.Compete;
import me.kkfish.competition.CompetitionConfig;
import me.kkfish.gui.GUIMenuLoader;
import me.kkfish.kkfish;
import me.kkfish.handlers.GUIAction;
import me.kkfish.misc.MessageManager;
import me.kkfish.utils.XSeriesUtil;
import me.kkfish.gui.GUIHolder;
import me.kkfish.gui.FishRecord;
import me.kkfish.gui.SlotMapping;

public class GUI {
    private final me.kkfish.kkfish plugin;
    private final MessageManager messageManager;
    private final Config config;
    private final DB db;
    private final me.kkfish.listeners.GUIListener guiListener;
    
    // 存储玩家当前的鱼类图鉴页面
    private final Map<UUID, Integer> fishDexPages = new HashMap<>();
    
    // 存储玩家当前的鱼钩材质选择页面
    private final Map<UUID, Integer> hookMaterialPages = new HashMap<>();
    
    // 存储玩家当前选择的排序方式
    private final Map<UUID, String> hookSortMethods = new HashMap<>();
    
    // 存储玩家的搜索关键词
    private final Map<UUID, String> hookSearchQueries = new HashMap<>();
    
    // GUI缓存，避免重复创建相同的GUI
    private final Map<String, Inventory> guiCache = new HashMap<>();
    
    // 存储每个玩家的每个GUI页面的槽位到鱼钩ID的映射
    private final Map<UUID, Map<String, Map<Integer, String>>> slotToHookMap = new HashMap<>();
    
    // GUI菜单加载器
    private final GUIMenuLoader menuLoader;
    
    // GUI动作处理器
    private final GUIAction actionHandler;
    
    // GUI类型枚举，便于识别和管理
    public enum GUIType {
        MAIN_MENU,
        HOOK_MATERIAL,
        FISH_DEX,
        FISH_RECORD,
        HELP_GUI,
        COMPETITION_CATEGORY,
        REWARD_PREVIEW,
        SELL_GUI
    }
    
    public GUI(me.kkfish.kkfish plugin) {
        this.plugin = plugin;
        this.messageManager = MessageManager.getInstance(plugin);
        this.config = plugin.getCustomConfig();
        this.db = plugin.getDB();
        
        // 创建GUI菜单加载器
        this.menuLoader = new GUIMenuLoader(plugin);
        
        // 创建GUI动作处理器
        this.actionHandler = new GUIAction(plugin);
        
        // 注册GUI事件监听器
        this.guiListener = new me.kkfish.listeners.GUIListener(this);
    }
    
    public void clearGUICache() {
        guiCache.clear();
    }
    
    public void reloadMenuConfigs() {
        menuLoader.loadAllMenus();
        clearGUICache();
    }
    
    public DB getDB() {
        return plugin.getDB();
    }
    
    public void setHookSortBy(Player player, String sortBy) {
        hookSortMethods.put(player.getUniqueId(), sortBy);
    }
    
    public String getHookSortBy(Player player) {
        return hookSortMethods.getOrDefault(player.getUniqueId(), messageManager.getMessageWithoutPrefix("sort_by_name", "按名称排序"));
    }
    
    private String getGUITitle(GUIType type, int page) {
        switch(type) {
            case MAIN_MENU:
                return "Main Menu";
            case HOOK_MATERIAL:
                return "Hook Material Selection";
            case FISH_DEX:
                return "Fish Dex" + " " + 
                       messageManager.getMessageWithoutPrefix("gui_page_number", "- Page %s", String.valueOf(page + 1));
            case FISH_RECORD:
                return "Fishing Record";
            case HELP_GUI:
                return "Help Guide";
            case COMPETITION_CATEGORY:
                return messageManager.getMessageWithoutPrefix("gui_competition_category_text", "Fishing Competitions");
            case REWARD_PREVIEW:
                return messageManager.getMessageWithoutPrefix("gui_reward_preview_title", "Reward Preview");
            case SELL_GUI:
                return "Sell Fish";
            default:
                return messageManager.getMessageWithoutPrefix("gui_unknown_title", "Unknown Interface");
        }
    }
    
    public void openGUI(Player player, GUIType type) {
        openGUI(player, type, 0);
    }
    
    public void openGUI(Player player, GUIType type, int page) {
        if (type == GUIType.SELL_GUI && !config.isEconomyEnabled()) {
            player.sendMessage(messageManager.getMessage(player, "economy_not_enabled", "§c经济系统未启用，无法使用卖出功能！"));
            return;
        }
        
        String menuName = type.name().toLowerCase();
        menuName = menuName.replace("_", "_");
        
        if (type == GUIType.MAIN_MENU) {
            menuName = "main_menu";
        } else if (type == GUIType.HOOK_MATERIAL) {
            menuName = "hook_material";
        } else if (type == GUIType.FISH_DEX) {
            menuName = "fish_dex";
        } else if (type == GUIType.FISH_RECORD) {
            menuName = "fish_record";
        } else if (type == GUIType.HELP_GUI) {
            menuName = "help_gui";
        } else if (type == GUIType.COMPETITION_CATEGORY) {
            menuName = "competition_category";
        } else if (type == GUIType.REWARD_PREVIEW) {
            menuName = "reward_preview";
        } else if (type == GUIType.SELL_GUI) {
            menuName = "sell_gui";
        }
        
        Inventory gui = createConfiguredGUI(player, menuName, page);
        
        if (gui != null) {
            player.openInventory(gui);
        }
    }
    
    private Inventory createConfiguredGUI(Player player, String menuName, int page) {
        if (!menuLoader.hasMenuConfig(menuName)) {
            plugin.getLogger().warning("菜单配置不存在: " + menuName);
            return null;
        }
        
        GUIMenuLoader.MenuConfig menuConfig = menuLoader.getMenuConfig(menuName);
        if (menuConfig == null) {
            return null;
        }
        
        int size = menuConfig.getSize();
        String title = menuConfig.getMenuTitle();
        
        if (title.startsWith("i18n:")) {
            String key = title.substring(5);
            title = messageManager.getMessageWithoutPrefix(player, key, title);
        }
        
        title = title.replace("%player_name%", player.getName());
        title = title.replace("%player%", player.getName());
        title = title.replace("%p", player.getName());
        title = title.replace("%page%", String.valueOf(page + 1));
        
        Inventory gui = Bukkit.createInventory(
            new GUIHolder(GUIType.valueOf(menuName.toUpperCase().replace("_", "_")), page),
            size,
            ChatColor.translateAlternateColorCodes('&', title)
        );
        
        fillMenuItems(gui, menuConfig, player, page);
        
        return gui;
    }
    
    private void fillMenuItems(Inventory gui, GUIMenuLoader.MenuConfig menuConfig, Player player, int page) {
        for (GUIMenuLoader.MenuConfig.MenuItem item : menuConfig.getItems().values()) {
            if (item.getId().equals("fish_dex_items")) {
                handleFishDexItems(gui, item, player, page);
            } else if (item.getId().equals("hook_material_items")) {
                handleHookMaterialItems(gui, item, player, page);
            } else if (item.getId().equals("competition_items")) {
                handleCompetitionItems(gui, item, player, page);
            } else if (item.getId().equals("reward_items")) {
                handleRewardItems(gui, item, player, page);
            } else {
                ItemStack itemStack = createMenuItemFromConfig(item, player, page);
                if (itemStack == null) {
                    continue;
                }
                
                for (int slot : item.getSlots()) {
                    if (slot >= 0 && slot < gui.getSize()) {
                        gui.setItem(slot, itemStack);
                    }
                }
            }
        }
    }
    
    private void handleCompetitionItems(Inventory gui, GUIMenuLoader.MenuConfig.MenuItem item, Player player, int page) {
        Compete compete = plugin.getCompete();
        Collection<CompetitionConfig> competitionConfigs = compete.getCompetitionConfigs();
        
        List<Integer> slots = item.getSlots();
        int competitionIndex = 0;
        
        for (int slot : slots) {
            if (competitionIndex < competitionConfigs.size() && slot >= 0 && slot < gui.getSize()) {
                CompetitionConfig config = (CompetitionConfig) competitionConfigs.toArray()[competitionIndex];
                
                ItemStack competitionItem = createCompetitionItemFromConfig(item, player, config);
                if (competitionItem != null) {
                    gui.setItem(slot, competitionItem);
                }
                
                competitionIndex++;
            }
        }
    }
    
    private void handleRewardItems(Inventory gui, GUIMenuLoader.MenuConfig.MenuItem item, Player player, int page) {
        Compete compete = plugin.getCompete();
        Collection<CompetitionConfig> competitionConfigs = compete.getCompetitionConfigs();
        
        CompetitionConfig competitionConfig = getCompetitionByIndex(competitionConfigs, page);
        if (competitionConfig == null) {
            return;
        }
        
        Map<Integer, List<String>> rewards = competitionConfig.getRewards();
        Map<Integer, List<String>> rewardDisplayInfo = competitionConfig.getRewardDisplayInfo();
        
        List<Integer> slots = item.getSlots();
        int rewardIndex = 0;
        
        for (Map.Entry<Integer, List<String>> entry : rewards.entrySet()) {
            if (rewardIndex < slots.size()) {
                int slot = slots.get(rewardIndex);
                if (slot >= 0 && slot < gui.getSize()) {
                    int rank = entry.getKey();
                    List<String> commands = entry.getValue();
                    List<String> displayInfo = rewardDisplayInfo.get(rank);
                    
                    ItemStack rewardItem = createRewardItemFromConfig(item, rank, commands, displayInfo);
                    if (rewardItem != null) {
                        gui.setItem(slot, rewardItem);
                    }
                }
                rewardIndex++;
            }
        }
    }
    
    // 从配置创建奖励物品
    private ItemStack createRewardItemFromConfig(GUIMenuLoader.MenuConfig.MenuItem itemConfig, int rank, List<String> commands, List<String> displayInfo) {
        // 解析奖励信息
        String rewardType = "Unknown";
        String rewardAmount = "Unknown";
        String rewardCondition = messageManager.getMessageWithoutPrefix("gui_reward_condition", "第" + rank + "名");
        Material material = XSeriesUtil.getMaterial("CHEST");
        
        // 优先使用配置的显示信息
        if (displayInfo != null && !displayInfo.isEmpty()) {
            rewardType = displayInfo.get(0);
            if (displayInfo.size() > 1) {
                rewardAmount = displayInfo.get(1);
            }
        } else {
            // 如果没有配置显示信息，才通过解析命令来生成显示信息
            for (String command : commands) {
                // 去掉命令开头的斜杠（如果有）
                String normalizedCommand = command.startsWith("/") ? command.substring(1) : command;
                
                if (normalizedCommand.startsWith("eco give")) {
                    // 经济奖励
                    rewardType = messageManager.getMessageWithoutPrefix("gui_reward_type_eco", "Eco Reward");
                    String[] parts = normalizedCommand.split(" ");
                    if (parts.length >= 3) {
                        rewardAmount = parts[2] + " " + messageManager.getMessageWithoutPrefix("gui_currency", "Coins");
                    }
                    material = XSeriesUtil.getMaterial("GOLD_INGOT");
                } else if (normalizedCommand.startsWith("give")) {
                    // 物品奖励
                    rewardType = messageManager.getMessageWithoutPrefix("gui_reward_type_item", "Item Reward");
                    String[] parts = normalizedCommand.split(" ");
                    if (parts.length >= 2) {
                        // 格式: give player item amount
                        if (parts.length >= 4) {
                            rewardAmount = parts[2] + " × " + parts[3];
                            material = parseMaterial(parts[2]);
                        } else if (parts.length >= 3) {
                            rewardAmount = parts[2] + " × 1";
                            material = parseMaterial(parts[2]);
                        }
                    }
                    if (material == null) {
                        material = XSeriesUtil.getMaterial("ITEM_FRAME");
                    }
                }
            }
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // 设置显示名称
        String displayName = itemConfig.getDisplayName();
        // 检查是否是国际化键值（以i18n:开头）
        if (displayName.startsWith("i18n:")) {
            String key = displayName.substring(5);
            displayName = messageManager.getMessageWithoutPrefix(key, displayName);
        }
        displayName = displayName.replace("%reward_name%", messageManager.getMessageWithoutPrefix("gui_reward_name", "第" + rank + "名奖励"));
        displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        meta.setDisplayName(displayName);
        
        // 设置lore
        List<String> lore = new ArrayList<>();
        for (String line : itemConfig.getLore()) {
            // 检查是否是国际化键值（以i18n:开头）
            if (line.startsWith("i18n:")) {
                String key = line.substring(5);
                line = messageManager.getMessageWithoutPrefix(key, line);
            }
            String replacedLine = line;
            
            // 处理%reward_display%占位符
            if (replacedLine.contains("%reward_display%")) {
                if (displayInfo != null && !displayInfo.isEmpty()) {
                    // 显示配置的奖励简介
                    for (String infoLine : displayInfo) {
                        String displayLine = "&7| &f" + infoLine;
                        displayLine = ChatColor.translateAlternateColorCodes('&', displayLine);
                        lore.add(displayLine);
                    }
                    continue; // 跳过默认处理
                }
            }
            
            // 处理其他占位符
            replacedLine = replacedLine.replace("%reward_type%", rewardType);
            replacedLine = replacedLine.replace("%reward_amount%", rewardAmount);
            replacedLine = replacedLine.replace("%reward_condition%", rewardCondition);
            replacedLine = ChatColor.translateAlternateColorCodes('&', replacedLine);
            lore.add(replacedLine);
        }
        
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        
        // 设置Custom Model Data
        if (itemConfig.hasCustomModelData()) {
            meta.setCustomModelData(itemConfig.getCustomModelData());
        }
        
        item.setItemMeta(meta);
        
        return item;
    }
    
    // 从配置创建比赛物品
    private ItemStack createCompetitionItemFromConfig(GUIMenuLoader.MenuConfig.MenuItem itemConfig, Player player, CompetitionConfig competitionConfig) {
        try {
            // 解析材质
            String materialStr = itemConfig.getMaterial().replace("%competition_item%", getCompetitionMaterial(competitionConfig.getType()));
            Material material = parseMaterial(materialStr);
            if (material == null) {
                material = XSeriesUtil.getMaterial("FISHING_ROD");
            }
            
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            
            // 设置显示名称
            String displayName = itemConfig.getDisplayName();
            // 检查是否是国际化键值（以i18n:开头）
            if (displayName.startsWith("i18n:")) {
                String key = displayName.substring(5);
                displayName = messageManager.getMessageWithoutPrefix(player, key, displayName);
            }
            displayName = displayName.replace("%competition_name%", competitionConfig.getName());
            displayName = displayName.replace("%player_name%", player.getName());
            displayName = displayName.replace("%player%", player.getName());
            displayName = displayName.replace("%p", player.getName());
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            
            // 设置 lore
            List<String> lore = new ArrayList<>();
            for (String line : itemConfig.getLore()) {
                // 检查是否是国际化键值（以i18n:开头）
                if (line.startsWith("i18n:")) {
                    String key = line.substring(5);
                    line = messageManager.getMessageWithoutPrefix(player, key, line);
                }
                line = line.replace("%competition_name%", competitionConfig.getName());
                line = line.replace("%competition_type%", getCompetitionTypeDisplayName(competitionConfig.getType()));
                line = line.replace("%competition_time%", competitionConfig.getSchedule());
                line = line.replace("%competition_fee%", "0");
                line = line.replace("%competition_reward%", getCompetitionRewardString(competitionConfig));
                line = line.replace("%competition_id%", competitionConfig.getId());
                line = line.replace("%player_name%", player.getName());
                line = line.replace("%player%", player.getName());
                line = line.replace("%p", player.getName());
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
            
            // 设置 Custom Model Data
            if (itemConfig.hasCustomModelData()) {
                meta.setCustomModelData(itemConfig.getCustomModelData());
            }
            
            // 设置不可破坏
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            
            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            plugin.getLogger().warning("创建比赛物品失败: " + e.getMessage());
            return null;
        }
    }
    
    // 获取比赛材质
    private String getCompetitionMaterial(String type) {
        switch (type) {
            case "AMOUNT":
                return "cooked_cod";
            case "TOTAL_VALUE":
                return "gold_ingot";
            case "SINGLE_VALUE":
                return "nether_star";
            default:
                return "fishing_rod";
        }
    }
    
    // 获取比赛奖励字符串
    private String getCompetitionRewardString(CompetitionConfig config) {
        Map<Integer, List<String>> rewards = config.getRewards();
        Map<Integer, List<String>> rewardDisplayInfo = config.getRewardDisplayInfo();
        StringBuilder rewardStr = new StringBuilder();
        
        // 只显示第一名的奖励
        if (rewardDisplayInfo.containsKey(1)) {
            List<String> displayInfo = rewardDisplayInfo.get(1);
            if (!displayInfo.isEmpty()) {
                // 使用配置的奖励显示信息
                for (String infoLine : displayInfo) {
                    rewardStr.append(infoLine);
                    break; // 只取第一行
                }
            }
        } else if (rewards.containsKey(1)) {
            // 如果没有配置显示信息，使用命令解析
            List<String> commands = rewards.get(1);
            rewardStr.append("第1名:");
            for (String command : commands) {
                if (command.startsWith("/eco give")) {
                    String[] parts = command.split(" ");
                    if (parts.length >= 4) {
                        rewardStr.append(" " + parts[3]).append("金币");
                    }
                } else if (command.startsWith("/give")) {
                    String[] parts = command.split(" ");
                    if (parts.length >= 3) {
                        rewardStr.append(" " + parts[2]);
                    }
                }
            }
        }
        
        return rewardStr.toString();
    }
    
    // 处理鱼钩材质物品
    private void handleHookMaterialItems(Inventory gui, GUIMenuLoader.MenuConfig.MenuItem item, Player player, int page) {
        // 获取所有鱼钩配置
        Map<String, Object> hookConfigs = config.getHookConfigs();
        List<String> hookNames = new ArrayList<>(hookConfigs.keySet());
        
        // 每页显示的鱼钩数量
        int itemsPerPage = 28; // 4行7列
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, hookNames.size());
        
        // 初始化或获取玩家的槽位到鱼钩ID映射
        UUID playerId = player.getUniqueId();
        String guiKey = "hook_material_page_" + page;
        Map<String, Map<Integer, String>> playerMap = slotToHookMap.computeIfAbsent(playerId, k -> new HashMap<>());
        Map<Integer, String> slotMap = playerMap.computeIfAbsent(guiKey, k -> new HashMap<>());
        slotMap.clear(); // 清空当前页面的映射
        
        // 遍历鱼钩物品槽位
        List<Integer> slots = item.getSlots();
        int hookIndex = startIndex;
        
        for (int slot : slots) {
            if (hookIndex < endIndex && slot >= 0 && slot < gui.getSize()) {
                String hookName = hookNames.get(hookIndex);
                if (hookName != null && !hookName.isEmpty()) {
                    try {
                        // 从配置创建鱼钩物品
                        ItemStack hookItem = createHookDisplayItemFromConfig(item, player, hookName);
                        gui.setItem(slot, hookItem);
                        // 记录槽位到鱼钩ID的映射
                        slotMap.put(slot, hookName);
                    } catch (Exception e) {
                        plugin.getLogger().warning("创建鱼钩展示物品失败: " + hookName + " - " + e.getMessage());
                    }
                }
                hookIndex++;
            }
        }
    }
    
    // 从配置创建鱼钩展示物品
    private ItemStack createHookDisplayItemFromConfig(GUIMenuLoader.MenuConfig.MenuItem itemConfig, Player player, String hookName) {
        // 获取鱼钩配置
        String displayName = config.getHookDisplayName(hookName);
        String description = config.getHookDescription(hookName);
        String rarity = config.getHookRarity(hookName);
        double price = config.getHookVaultPrice(hookName);
        
        // 检查玩家是否已拥有此鱼钩
        boolean isOwned = config.hasHookMaterialPermission(player, hookName);
        boolean isEquipped = hookName.equals(plugin.getDB().getPlayerHookMaterial(player.getUniqueId().toString()));
        
        // 获取材质
        Material material = config.getHookMaterial(hookName);
        if (material == null) {
            material = XSeriesUtil.getMaterial("STICK");
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // 设置显示名称
        String displayNameConfig = itemConfig.getDisplayName();
        // 检查是否是国际化键值（以i18n:开头）
        if (displayNameConfig.startsWith("i18n:")) {
            String key = displayNameConfig.substring(5);
            displayNameConfig = messageManager.getMessageWithoutPrefix(player, key, displayNameConfig);
        }
        displayNameConfig = displayNameConfig.replace("%hook_name%", displayName);
        displayNameConfig = ChatColor.translateAlternateColorCodes('&', displayNameConfig);
        meta.setDisplayName(displayNameConfig);
        
        // 设置lore
        List<String> lore = new ArrayList<>();
        for (String line : itemConfig.getLore()) {
            // 检查是否是国际化键值（以i18n:开头）
            if (line.startsWith("i18n:")) {
                String key = line.substring(5);
                line = messageManager.getMessageWithoutPrefix(player, key, line);
            }
            String replacedLine = line;
            replacedLine = replacedLine.replace("%hook_name%", displayName);
            replacedLine = replacedLine.replace("%hook_level%", getHookLevelByRarity(rarity));
            replacedLine = replacedLine.replace("%hook_price%", String.format("%.2f", price));
            replacedLine = replacedLine.replace("%hook_durability%", messageManager.getMessageWithoutPrefix("hook_durability_infinite", "无限"));
            replacedLine = replacedLine.replace("%hook_effect%", description);
            
            // 添加状态信息占位符
            if (replacedLine.contains("%hook_status%")) {
                if (isEquipped) {
                    replacedLine = replacedLine.replace("%hook_status%", messageManager.getMessageWithoutPrefix("hook_status_equipped", "✓ Currently Equipped"));
                } else if (isOwned) {
                    replacedLine = replacedLine.replace("%hook_status%", messageManager.getMessageWithoutPrefix("hook_status_owned", "Owned"));
                } else {
                    replacedLine = replacedLine.replace("%hook_status%", messageManager.getMessageWithoutPrefix("hook_status_not_owned", "Not Owned"));
                }
            }
            
            if (replacedLine.contains("%hook_action%")) {
                if (isEquipped) {
                    replacedLine = replacedLine.replace("%hook_action%", messageManager.getMessageWithoutPrefix("hook_action_equipped", "Already Equipped"));
                } else if (isOwned) {
                    replacedLine = replacedLine.replace("%hook_action%", messageManager.getMessageWithoutPrefix("hook_action_equip", "Click to Equip"));
                } else {
                    replacedLine = replacedLine.replace("%hook_action%", messageManager.getMessageWithoutPrefix("hook_action_buy", "Click to Buy"));
                }
            }
            
            replacedLine = ChatColor.translateAlternateColorCodes('&', replacedLine);
            lore.add(replacedLine);
        }
        
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        
        return item;
    }
    
    // 根据稀有度获取等级
    private String getHookLevelByRarity(String rarity) {
        switch (rarity.toLowerCase()) {
            case "common":
            case "初级":
                return "1";
            case "uncommon":
            case "中级":
                return "11";
            case "rare":
            case "高级":
                return "21";
            case "epic":
            case "稀有":
                return "31";
            case "legendary":
            case "传说":
                return "41";
            default:
                return "1";
        }
    }
    
    // 处理鱼类图鉴物品
    private void handleFishDexItems(Inventory gui, GUIMenuLoader.MenuConfig.MenuItem item, Player player, int page) {
        // 获取所有鱼类配置
        FileConfiguration fishConfig = config.getFishConfig();
        List<String> allFishNames = new ArrayList<>();
        
        // 防止空指针，先检查配置节点是否存在
        if (fishConfig.isConfigurationSection("fish")) {
            allFishNames.addAll(fishConfig.getConfigurationSection("fish").getKeys(false));
        }
        
        // 每页显示的鱼类数量
        int itemsPerPage = 28; // 4行7列
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allFishNames.size());
        
        // 遍历鱼类物品槽位
        List<Integer> slots = item.getSlots();
        int fishIndex = startIndex;
        
        for (int slot : slots) {
            if (fishIndex < endIndex && slot >= 0 && slot < gui.getSize()) {
                String fishName = allFishNames.get(fishIndex);
                if (fishName != null && !fishName.isEmpty()) {
                    try {
                        ItemStack fishItem = createFishDisplayItemFromConfig(item, fishConfig, fishName, player);
                        gui.setItem(slot, fishItem);
                    } catch (Exception e) {
                        plugin.getLogger().warning("创建鱼类展示物品失败: " + fishName + " - " + e.getMessage());
                    }
                }
                fishIndex++;
            }
        }
    }
    
    // 从配置创建鱼类展示物品
    private ItemStack createFishDisplayItemFromConfig(GUIMenuLoader.MenuConfig.MenuItem itemConfig, FileConfiguration fishConfig, String fishName, Player player) {
        // 获取玩家的钓鱼记录
        DB dbManager = plugin.getDB();
        Map<String, Object> fishStats = dbManager.getPlayerFishStats(player.getUniqueId().toString(), fishName);
        int caughtCount = (int) fishStats.get("caughtCount");
        double maxSize = (double) fishStats.get("maxSize");
        
        // 获取鱼的基本信息
        String fishDisplayName = fishConfig.getString("fish." + fishName + ".display-name", fishName);
        String materialName = fishConfig.getString("fish." + fishName + ".material", "COD");
        
        // 根据是否钓到过设置不同材质
        Material material = Material.getMaterial(materialName);
        if (material == null) {
            material = XSeriesUtil.getMaterial("COD");
        }
        
        if (caughtCount == 0) {
            material = XSeriesUtil.getMaterial("BLACK_WOOL"); // 没钓到过，显示黑色羊毛
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // 根据解锁状态设置不同的显示信息
        if (caughtCount > 0) {
            // 已解锁鱼类
            if (itemConfig.getUnlocked() != null) {
                // 设置显示名称
                String displayNameConfig = itemConfig.getUnlocked().getDisplayName();
                // 检查是否是国际化键值（以i18n:开头）
                if (displayNameConfig.startsWith("i18n:")) {
                    String key = displayNameConfig.substring(5);
                    displayNameConfig = messageManager.getMessageWithoutPrefix(player, key, displayNameConfig);
                }
                displayNameConfig = displayNameConfig.replace("%fish_name%", fishDisplayName);
                displayNameConfig = ChatColor.translateAlternateColorCodes('&', displayNameConfig);
                meta.setDisplayName(displayNameConfig);
                
                // 设置lore
                List<String> lore = new ArrayList<>();
                for (String line : itemConfig.getUnlocked().getLore()) {
                    // 检查是否是国际化键值（以i18n:开头）
                    if (line.startsWith("i18n:")) {
                        String key = line.substring(5);
                        line = messageManager.getMessageWithoutPrefix(player, key, line);
                    }
                    String replacedLine = line;
                    replacedLine = replacedLine.replace("%fish_name%", fishDisplayName);
                    
                    // 等级信息
                    Object levelObj = fishConfig.get("fish." + fishName + ".level");
                    if (levelObj != null) {
                        String levelStr = "";
                        if (levelObj instanceof List) {
                            List<?> rawList = (List<?>) levelObj;
                            for (Object levelItem : rawList) {
                                if (levelItem instanceof Map) {
                                    Map<?, ?> levelMap = (Map<?, ?>) levelItem;
                                    for (Map.Entry<?, ?> entry : levelMap.entrySet()) {
                                        try {
                                            String levelName = entry.getKey().toString();
                                            String weight = entry.getValue().toString();
                                            levelStr += levelName + "(" + weight + "%), ";
                                        } catch (Exception e) {
                                            // 格式错误，跳过
                                        }
                                    }
                                }
                            }
                            if (!levelStr.isEmpty()) {
                                levelStr = levelStr.substring(0, levelStr.length() - 2);
                            }
                        } else if (levelObj instanceof String) {
                            levelStr = levelObj.toString();
                        } else if (levelObj instanceof Map) {
                            Map<?, ?> levelMap = (Map<?, ?>) levelObj;
                            for (Map.Entry<?, ?> entry : levelMap.entrySet()) {
                                try {
                                    String levelName = entry.getKey().toString();
                                    String weight = entry.getValue().toString();
                                    levelStr = levelName + "(" + weight + "%)";
                                    break;
                                } catch (Exception e) {
                                    // 格式错误，跳过
                                }
                            }
                        }
                        replacedLine = replacedLine.replace("%fish_level%", levelStr);
                    } else {
                        replacedLine = replacedLine.replace("%fish_level%", "无");
                    }
                    
                    // 价值信息
                    double value = fishConfig.getDouble("fish." + fishName + ".value", 0);
                    replacedLine = replacedLine.replace("%fish_value%", String.valueOf(value));
                    
                    // 大小信息
                    int minSize = fishConfig.getInt("fish." + fishName + ".min-size", 0);
                    int configMaxSize = fishConfig.getInt("fish." + fishName + ".max-size", 0);
                    replacedLine = replacedLine.replace("%fish_min_size%", String.valueOf(minSize));
                    replacedLine = replacedLine.replace("%fish_max_size%", String.valueOf(configMaxSize));
                    
                    // 已钓到次数
                    replacedLine = replacedLine.replace("%fish_caught%", String.valueOf(caughtCount));
                    
                    // 最大尺寸
                    replacedLine = replacedLine.replace("%fish_max_caught_size%", String.format("%.1f", maxSize));
                    
                    // 转换颜色代码并添加到lore
                    replacedLine = ChatColor.translateAlternateColorCodes('&', replacedLine);
                    lore.add(replacedLine);
                }
                meta.setLore(lore);
            }
        } else {
            // 未解锁鱼类
            if (itemConfig.getLocked() != null) {
                // 设置显示名称
                String displayNameConfig = itemConfig.getLocked().getDisplayName();
                // 检查是否是国际化键值（以i18n:开头）
                if (displayNameConfig.startsWith("i18n:")) {
                    String key = displayNameConfig.substring(5);
                    displayNameConfig = messageManager.getMessageWithoutPrefix(player, key, displayNameConfig);
                }
                displayNameConfig = ChatColor.translateAlternateColorCodes('&', displayNameConfig);
                meta.setDisplayName(displayNameConfig);
                
                // 设置lore
                List<String> lore = new ArrayList<>();
                for (String line : itemConfig.getLocked().getLore()) {
                    // 检查是否是国际化键值（以i18n:开头）
                    if (line.startsWith("i18n:")) {
                        String key = line.substring(5);
                        line = messageManager.getMessageWithoutPrefix(player, key, line);
                    }
                    line = ChatColor.translateAlternateColorCodes('&', line);
                    lore.add(line);
                }
                meta.setLore(lore);
            }
        }
        
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        
        // 设置Custom Model Data
        int customModelData = fishConfig.getInt("fish." + fishName + ".custom-model-data", -1);
        if (customModelData != -1 && caughtCount > 0) {
            meta.setCustomModelData(customModelData);
        }
        
        item.setItemMeta(meta);
        
        return item;
    }
    
    // 从配置创建物品
    private ItemStack createMenuItemFromConfig(GUIMenuLoader.MenuConfig.MenuItem itemConfig, Player player, int page) {
        try {
            // 解析材质
            Material material = parseMaterial(itemConfig.getMaterial());
            if (material == null) {
                material = XSeriesUtil.getMaterial("STONE");
            }
            
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            
            // 设置显示名称
            String displayName = itemConfig.getDisplayName();
            // 检查是否是国际化键值（以i18n:开头）
            if (displayName.startsWith("i18n:")) {
                String key = displayName.substring(5);
                displayName = messageManager.getMessageWithoutPrefix(player, key, displayName);
            }
            displayName = replacePlaceholders(displayName, player, page, itemConfig);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            
            // 设置 lore
            List<String> lore = new ArrayList<>();
            for (String line : itemConfig.getLore()) {
                // 检查是否是国际化键值（以i18n:开头）
                if (line.startsWith("i18n:")) {
                    String key = line.substring(5);
                    line = messageManager.getMessageWithoutPrefix(player, key, line);
                }
                line = replacePlaceholders(line, player, page, itemConfig);
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
            
            // 设置 Custom Model Data
            if (itemConfig.hasCustomModelData()) {
                meta.setCustomModelData(itemConfig.getCustomModelData());
            }
            
            // 设置不可破坏
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            
            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            plugin.getLogger().warning("创建菜单物品失败: " + e.getMessage());
            return null;
        }
    }
    
    // 替换占位符
    private String replacePlaceholders(String text, Player player, int page, GUIMenuLoader.MenuConfig.MenuItem itemConfig) {
        // 基本占位符
        text = text.replace("%player_name%", player.getName());
        text = text.replace("%page%", String.valueOf(page + 1));
        text = text.replace("%player%", player.getName());
        text = text.replace("%p", player.getName());
        
        // 钓鱼记录占位符
        if (itemConfig.getId().equals("total_fish_caught") || itemConfig.getId().equals("rare_fish_caught") || itemConfig.getId().equals("legendary_fish_caught")) {
            FishRecord record = plugin.getDB().getPlayerFishRecord(player.getUniqueId().toString());
            text = text.replace("%total_fish_caught%", String.valueOf(record.getTotalFishCaught()));
            text = text.replace("%rare_fish_caught%", String.valueOf(record.getRareFishCaught()));
            text = text.replace("%legendary_fish_caught%", String.valueOf(record.getLegendaryFishCaught()));
        }
        
        // 比赛占位符 - 这里需要在处理比赛物品时单独处理
        // 因为比赛物品有多个，每个都有不同的占位符值
        
        return text;
    }
    
    // 解析材质
    private Material parseMaterial(String materialStr) {
        if (materialStr == null || materialStr.isEmpty()) {
            return null;
        }
        
        // 处理特殊材质格式
        if (materialStr.startsWith("head-")) {
            // 玩家头颅，暂时返回PLAYER_HEAD
            return XSeriesUtil.getMaterial("PLAYER_HEAD");
        } else if (materialStr.startsWith("basehead-")) {
            // 自定义头颅，暂时返回PLAYER_HEAD
            return XSeriesUtil.getMaterial("PLAYER_HEAD");
        }
        
        // 尝试解析普通材质
        try {
            // 首先尝试使用XSeries解析
            Material material = XSeriesUtil.parseMaterial(materialStr);
            if (material != null) {
                return material;
            }
            
            // 如果XSeries解析失败，尝试使用XSeriesUtil.getMaterial
            material = XSeriesUtil.getMaterial(materialStr);
            if (material != null) {
                return material;
            }
            
            // 如果都失败，尝试使用大写形式
            material = XSeriesUtil.parseMaterial(materialStr.toUpperCase());
            if (material != null) {
                return material;
            }
            
            // 最后尝试使用大写形式的getMaterial
            return XSeriesUtil.getMaterial(materialStr.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }
    

    
    // 获取菜单加载器
    public GUIMenuLoader getMenuLoader() {
        return menuLoader;
    }
    
    // 获取动作处理器
    public GUIAction getActionHandler() {
        return actionHandler;
    }
    
    // 获取GUI监听器
    public me.kkfish.listeners.GUIListener getGUI() {
        return guiListener;
    }
    

    

    
    // 处理玩家退出事件，清理GUI相关资源
    public void handlePlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        
        // 清理玩家相关的GUI状态
        fishDexPages.remove(playerId);
        hookMaterialPages.remove(playerId);
        hookSortMethods.remove(playerId);
        hookSearchQueries.remove(playerId);
        slotToHookMap.remove(playerId);
    }
    
    // 根据槽位获取鱼钩ID
    public String getHookIdFromSlot(Player player, int slot, int page) {
        UUID playerId = player.getUniqueId();
        String guiKey = "hook_material_page_" + page;
        
        Map<String, Map<Integer, String>> playerMap = slotToHookMap.get(playerId);
        if (playerMap != null) {
            Map<Integer, String> slotMap = playerMap.get(guiKey);
            if (slotMap != null) {
                return slotMap.get(slot);
            }
        }
        
        return null;
    }
    
    // 获取玩家当前的鱼钩材质页面
    public int getCurrentHookMaterialPage(Player player) {
        return hookMaterialPages.getOrDefault(player.getUniqueId(), 0);
    }
    
    /**
     * 清理指定类型的GUI缓存
     */
    public void clearGuiCache(GUIType type) {
        String prefix = type + ":";
        for (Iterator<Map.Entry<String, Inventory>> iterator = guiCache.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<String, Inventory> entry = iterator.next();
            if (entry.getKey().startsWith(prefix)) {
                iterator.remove();
            }
        }
    }
    
    // 创建主菜单界面
    private Inventory createMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(
            new GUIHolder(GUIType.MAIN_MENU), 
            27, 
            getGUITitle(GUIType.MAIN_MENU, 0)
        );
        
        // 填充背景
        fillBackground(gui);
        
        // 创建主界面按钮
        // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
        ItemStack hookMaterialButton = createMenuItem(XSeriesUtil.getMaterial("FISHING_ROD"), "Hook Material Selection", 
            "Select different hook materials");
        ItemStack fishDexButton = createMenuItem(XSeriesUtil.getMaterial("ENCHANTED_BOOK"), "Fish Dex", 
            "View all fish information");
        ItemStack fishRecordButton = createMenuItem(XSeriesUtil.getMaterial("PAPER"), "Fishing Record", 
            "View your fishing records");
        ItemStack helpButton = createMenuItem(XSeriesUtil.getMaterial("BOOK"), "Help Guide", 
            "Learn how to use the fishing system");
        ItemStack competitionButton = createMenuItem(XSeriesUtil.getMaterial("NETHER_STAR"), messageManager.getMessageWithoutPrefix("gui_competition_category_text", "钓鱼比赛"), 
            messageManager.getMessageWithoutPrefix("gui_competition_category_desc", "查看钓鱼比赛分类和奖励"));
        
        // 设置按钮位置，根据SlotMapping中的定义
        gui.setItem(SlotMapping.MainMenu.HOOK_MATERIAL_SLOT, hookMaterialButton); // 10号槽位
        gui.setItem(SlotMapping.MainMenu.FISH_DEX_SLOT, fishDexButton); // 12号槽位
        gui.setItem(SlotMapping.MainMenu.FISH_RECORD_SLOT, fishRecordButton); // 14号槽位
        gui.setItem(SlotMapping.MainMenu.HELP_GUI_SLOT, helpButton); // 16号槽位
        gui.setItem(SlotMapping.MainMenu.COMPETITION_SLOT, competitionButton); // 22号槽位
        
        return gui;
    }
    
    // 创建鱼钩材质选择界面
    private Inventory createHookMaterialSelection(Player player) {
        UUID playerId = player.getUniqueId();
        int currentPage = hookMaterialPages.getOrDefault(playerId, 0);
        return createHookMaterialSelection(player, currentPage);
    }
    
    // 创建指定页面的鱼钩材质选择界面 - 全新设计
    private Inventory createHookMaterialSelection(Player player, int page) {
        UUID playerId = player.getUniqueId();
        int currentPage = page;
        
        // 使用固定的6行界面，提供最佳视觉体验
        int inventorySize = 54;
        
        // 创建GUI
        Inventory gui = Bukkit.createInventory(
            new GUIHolder(GUIType.HOOK_MATERIAL, currentPage), 
            inventorySize, 
            getGUITitle(GUIType.HOOK_MATERIAL, currentPage)
        );
        
        // 获取当前材质
        String currentMaterial = plugin.getDB().getPlayerHookMaterial(playerId.toString());
        if (currentMaterial == null) currentMaterial = "wood";
        
        // 获取每页显示的鱼钩数量和是否启用翻页
        int itemsPerPage = 28; // 6行界面中显示28个鱼钩
        boolean paginationEnabled = true; // 强制启用分页以适应新布局
        
        // 获取所有鱼钩配置
        Map<String, Object> allHooks = config.getHookConfigs();
        
        // 获取当前选择的排序方式
        String currentSortBy = getHookSortBy(player);
        
        // 过滤出应该在GUI中显示的鱼钩
        List<String> visibleHookNames = new ArrayList<>();
        for (String hookName : allHooks.keySet()) {
            if (!config.hasHookMaterialPermission(player, hookName)) {
                continue;
            }
            
            // 应用搜索过滤
            String searchQuery = getHookSearchQuery(player);
            if (searchQuery.isEmpty() || matchesSearchQuery(hookName, searchQuery)) {
                visibleHookNames.add(hookName);
            }
        }
        
        // 如果没有搜索结果，可以添加一个提示信息
        if (visibleHookNames.isEmpty() && !getHookSearchQuery(player).isEmpty()) {
            // 这里会在GUI中显示提示信息
        }
        
        // 应用排序
        if (currentSortBy.equals("按名称排序")) {
            visibleHookNames.sort((a, b) -> {
                String nameA = config.getHookDisplayName(a);
                String nameB = config.getHookDisplayName(b);
                return ChatColor.stripColor(nameA).compareToIgnoreCase(ChatColor.stripColor(nameB));
            });
        } else if (currentSortBy.equals("按等级排序")) {
            visibleHookNames.sort((a, b) -> {
                String rarityA = config.getHookRarity(a);
                String rarityB = config.getHookRarity(b);
                
                // 定义稀有度顺序：legendary/传说 > epic/稀有 > rare/高级 > uncommon/中级 > common/初级
                Map<String, Integer> rarityOrder = new HashMap<>();
                rarityOrder.put("legendary", 5);
                rarityOrder.put("传说", 5);
                rarityOrder.put("epic", 4);
                rarityOrder.put("稀有", 4);
                rarityOrder.put("rare", 3);
                rarityOrder.put("高级", 3);
                rarityOrder.put("uncommon", 2);
                rarityOrder.put("中级", 2);
                rarityOrder.put("common", 1);
                rarityOrder.put("初级", 1);
                rarityOrder.put("低级", 1);
                
                int levelA = 0;
                int levelB = 0;
                
                if (rarityA != null) {
                    for (Map.Entry<String, Integer> entry : rarityOrder.entrySet()) {
                        if (rarityA.contains(entry.getKey())) {
                            levelA = entry.getValue();
                            break;
                        }
                    }
                }
                
                if (rarityB != null) {
                    for (Map.Entry<String, Integer> entry : rarityOrder.entrySet()) {
                        if (rarityB.contains(entry.getKey())) {
                            levelB = entry.getValue();
                            break;
                        }
                    }
                }
                
                return Integer.compare(levelB, levelA); // 降序排列
            });
        } else if (currentSortBy.equals("按价格排序")) {
            visibleHookNames.sort((a, b) -> {
                double priceA = config.getHookVaultPrice(a);
                double priceB = config.getHookVaultPrice(b);
                return Double.compare(priceB, priceA); // 降序排列
            });
        }
        
        // 获取总页数
        int totalPages = (int) Math.ceil((double) visibleHookNames.size() / itemsPerPage);
        totalPages = Math.max(totalPages, 1); // 至少1页
        
        // 确保当前页在有效范围内
        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1;
            hookMaterialPages.put(playerId, currentPage);
        }
        
        // 设置现代风格的背景和边框
        fillModernBackground(gui);
        
        // 添加搜索和排序功能
        addSearchAndSortButtons(gui);
        
        // 计算当前页显示的鱼钩
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, visibleHookNames.size());
        List<String> currentPageHooks = visibleHookNames.subList(startIndex, endIndex);
        
        // 添加当前页的鱼钩，采用更美观的网格布局
        int slotIndex = 19; // 从第二行第二列开始
        for (String hookName : currentPageHooks) {
            if (!config.isHookVisibleInGui(hookName)) {
                continue;
            }
            
            // 获取鱼钩配置
            Material material = config.getHookMaterial(hookName);
            
            // 直接从hooks.yml获取显示名称和描述
            String displayName = config.getHookDisplayName(hookName);
            String description = config.getHookDescription(hookName);
            
            // 转换颜色代码
            displayName = ChatColor.translateAlternateColorCodes('&', displayName);
            description = ChatColor.translateAlternateColorCodes('&', description);
            
            // 创建全新设计的鱼钩物品
            ItemStack hookItem = createNewHookMaterialItem(
                hookName, 
                material, 
                displayName, 
                description, 
                currentMaterial.equals(hookName),
                player
            );
            
            // 添加到GUI - 使用更优化的网格布局
            if (slotIndex < 45 && slotIndex % 9 != 0 && slotIndex % 9 != 8) {
                gui.setItem(slotIndex, hookItem);
                slotIndex++;
                // 跳到下一行避免边界
                if (slotIndex % 9 == 0) slotIndex += 2;
            }
        }
        
        // 添加全新设计的分页和导航控件
        addModernNavigationControls(gui, currentPage, totalPages);
        
        return gui;
    }
    
    /**
     * 检查鱼钩名称是否匹配搜索查询
     */
    private boolean matchesSearchQuery(String hookName, String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        
        String displayName = config.getHookDisplayName(hookName);
        String description = config.getHookDescription(hookName);
        String rarity = config.getHookRarity(hookName);
        
        // 移除颜色代码并转换为小写进行比较
        query = ChatColor.stripColor(query).toLowerCase();
        
        if (displayName != null) {
            displayName = ChatColor.stripColor(displayName).toLowerCase();
            if (displayName.contains(query)) {
                return true;
            }
        }
        
        if (description != null) {
            description = ChatColor.stripColor(description).toLowerCase();
            if (description.contains(query)) {
                return true;
            }
        }
        
        if (rarity != null) {
            rarity = ChatColor.stripColor(rarity).toLowerCase();
            if (rarity.contains(query)) {
                return true;
            }
        }
        
        return false;
    }
    
    // 创建现代风格的背景和边框
    private void fillModernBackground(Inventory gui) {
        // 创建渐变背景效果
        for (int i = 0; i < gui.getSize(); i++) {
            // 顶部和底部使用深蓝色玻璃
            if (i < 9 || i >= 45) {
                gui.setItem(i, createBackgroundItem(XSeriesUtil.getMaterial("BLUE_STAINED_GLASS_PANE"), " "));
            }
            // 左右边框使用浅蓝色玻璃
            else if (i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, createBackgroundItem(XSeriesUtil.getMaterial("LIGHT_BLUE_STAINED_GLASS_PANE"), " "));
            }
            // 中间区域使用海晶灯作为发光背景
            else {
                gui.setItem(i, createBackgroundItem(XSeriesUtil.getMaterial("SEA_LANTERN"), ""));
            }
        }
        
        // 添加装饰性元素
        // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
        gui.setItem(4, createMenuItem(XSeriesUtil.getMaterial("FISHING_ROD"), messageManager.getMessageWithoutPrefix("gui_hook_material_title", "高级鱼钩系统"), messageManager.getMessageWithoutPrefix("gui_hook_material_desc", "选择最适合你的钓鱼利器")));
    }
    
    // 创建背景物品
    private ItemStack createBackgroundItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }
    
    // 添加搜索和排序按钮
    private void addSearchAndSortButtons(Inventory gui) {
        // 搜索按钮
        gui.setItem(3, createMenuItem(XSeriesUtil.getMaterial("COMPASS"), 
            messageManager.getMessageWithoutPrefix("gui_search", "§b搜索"), 
            messageManager.getMessageWithoutPrefix("gui_search_desc", "§7寻找特定材质的鱼钩")));
        
        // 取消搜索按钮
        gui.setItem(4, createMenuItem(XSeriesUtil.getMaterial("BARRIER"), 
            messageManager.getMessageWithoutPrefix("gui_cancel_search", "取消搜索"), 
            messageManager.getMessageWithoutPrefix("gui_cancel_search_desc", "清除当前的搜索条件")));
        
        // 排序按钮 - 显示当前排序方式
        gui.setItem(5, createMenuItem(XSeriesUtil.getMaterial("HOPPER"), 
            messageManager.getMessageWithoutPrefix("gui_sort_method", "§b排序方式"), 
            messageManager.getMessageWithoutPrefix("gui_sort_method_lore", "§7点击切换排序方式")));
    }
    
    // 添加现代风格的导航控件
    private void addModernNavigationControls(Inventory gui, int currentPage, int totalPages) {
        // 返回按钮
        gui.setItem(49, createBackButton());
        
        // 首页按钮
        if (currentPage > 0) {
            gui.setItem(45, createMenuItem(XSeriesUtil.getMaterial("RED_BED"), 
                messageManager.getMessageWithoutPrefix("gui_first_page", "§a首页"), 
                messageManager.getMessageWithoutPrefix("gui_first_page_lore", "§7返回第一页")));
        } else {
            gui.setItem(45, createBackgroundItem(XSeriesUtil.getMaterial("GRAY_STAINED_GLASS_PANE"), 
                messageManager.getMessageWithoutPrefix("gui_first_page_disabled", "§c首页")));
        }
        
        // 上一页按钮
        if (currentPage > 0) {
            gui.setItem(46, createPreviousPageButton());
        } else {
            gui.setItem(46, createBackgroundItem(XSeriesUtil.getMaterial("GRAY_STAINED_GLASS_PANE"), 
                "§cPrevious Page"));
        }
        
        // 页码指示器
        gui.setItem(47, createModernPageIndicator(currentPage + 1, totalPages));
        
        // 下一页按钮
        if (currentPage < totalPages - 1) {
            gui.setItem(48, createNextPageButton());
        } else {
            gui.setItem(48, createBackgroundItem(XSeriesUtil.getMaterial("GRAY_STAINED_GLASS_PANE"), 
                "§cNext Page"));
        }
        
        // 末页按钮
        if (currentPage < totalPages - 1) {
            gui.setItem(50, createMenuItem(XSeriesUtil.getMaterial("GREEN_BED"), 
                messageManager.getMessageWithoutPrefix("gui_last_page", "§a末页"), 
                messageManager.getMessageWithoutPrefix("gui_last_page_lore", "§7跳转到最后一页")));
        } else {
            gui.setItem(50, createBackgroundItem(XSeriesUtil.getMaterial("GRAY_STAINED_GLASS_PANE"), 
                messageManager.getMessageWithoutPrefix("gui_last_page_disabled", "§c末页")));
        }
        
        // 快速跳转按钮
        gui.setItem(51, createMenuItem(XSeriesUtil.getMaterial("BOOK"), 
            messageManager.getMessageWithoutPrefix("gui_jump_to_page", "§b快速跳转"), 
            messageManager.getMessageWithoutPrefix("gui_jump_to_page_lore", "§7输入页码直接跳转")));
        
        // 刷新按钮
        gui.setItem(53, createMenuItem(XSeriesUtil.getMaterial("BLAZE_POWDER"), 
            messageManager.getMessageWithoutPrefix("gui_refresh_list", "§e刷新列表"), 
            messageManager.getMessageWithoutPrefix("gui_refresh_list_lore", "§7刷新鱼钩显示列表")));
    }
    
    // 创建现代风格的页码指示器
    private ItemStack createModernPageIndicator(int currentPage, int totalPages) {
        ItemStack item = new ItemStack(XSeriesUtil.getMaterial("PAPER"));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messageManager.getMessageWithoutPrefix("gui_modern_page_indicator", "§6§l第 %s / %s 页", currentPage, totalPages));
        
        List<String> lore = new ArrayList<>();
        lore.add(messageManager.getMessageWithoutPrefix("gui_modern_page_indicator_total_hooks", "§7总鱼钩数量: %s-", 
            (totalPages > 0 ? (totalPages - 1) * 28 + 1 : 0)));
        lore.add(messageManager.getMessageWithoutPrefix("gui_modern_page_indicator_click_navigate", "§7点击按钮导航页面"));
        
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        
        return item;
    }
    
    // 生成可用的槽位列表（避开边界和控制按钮区域）
    private List<Integer> generateAvailableSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        int inventorySize = rows * 9;
        
        // 对于边界和控制按钮区域外的槽位
        for (int i = 0; i < inventorySize; i++) {
            // 排除第一行和最后一行
            if (i < 9 || i >= inventorySize - 9) {
                continue;
            }
            
            // 排除左右边界（每行的第一个和最后一个槽位）
            if (i % 9 == 0 || i % 9 == 8) {
                continue;
            }
            
            // 排除控制按钮区域（最后一行的中间位置）
            if (rows > 1 && i >= inventorySize - 18 && i < inventorySize - 9 && 
                (i % 9 == 4 || i % 9 == 3 || i % 9 == 5)) {
                continue;
            }
            
            slots.add(i);
        }
        
        return slots;
    }
    
    // 创建上一页按钮
    private ItemStack createPreviousPageButton() {
        return createMenuItem(
            XSeriesUtil.getMaterial("ARROW"),
            "§aPrevious Page",
            "§7Click to view previous page"
        );
    }
    
    // 创建下一页按钮
    private ItemStack createNextPageButton() {
        return createMenuItem(
            XSeriesUtil.getMaterial("ARROW"),
            "§aNext Page",
            "§7Click to view next page"
        );
    }
    
    // 创建页码指示器
    private ItemStack createPageIndicator(int currentPage, int totalPages) {
        ItemStack item = new ItemStack(XSeriesUtil.getMaterial("PAPER"));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(messageManager.getMessageWithoutPrefix("gui_page_indicator", "§6第 %s / %s 页", currentPage, totalPages));
        
        List<String> lore = new ArrayList<>();
        lore.add(messageManager.getMessageWithoutPrefix("gui_page_indicator_lore", "§7点击左右箭头翻页"));
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    // 创建鱼类图鉴页面
    private Inventory createFishDexPage(Player player, int page) {
        Inventory gui = Bukkit.createInventory(
            new GUIHolder(GUIType.FISH_DEX), 
            54, 
            getGUITitle(GUIType.FISH_DEX, page)
        );
        
        // 填充背景
        fillBorderBackground(gui);
        
        // 获取所有鱼类配置
        FileConfiguration fishConfig = config.getFishConfig();
        List<String> allFishNames = new ArrayList<>();
        
        // 防止空指针，先检查配置节点是否存在
        if (fishConfig.isConfigurationSection("fish")) {
            allFishNames.addAll(fishConfig.getConfigurationSection("fish").getKeys(false));
        } else {
            // 如果没有鱼类配置，添加一个提示
            // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
            ItemStack noFishItem = createMenuItem(XSeriesUtil.getMaterial("BARRIER"), messageManager.getMessageWithoutPrefix("gui_error_no_fish_config", "暂无鱼类配置"), messageManager.getMessageWithoutPrefix("gui_error_fish_config_desc", "请检查配置文件"));
            gui.setItem(22, noFishItem);
        }
        
        // 每页显示36个鱼类（排除最后一行的按钮）
        int itemsPerPage = 36;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allFishNames.size());
        
        // 添加鱼类展示物品（只放在非边界区域）
        int slotIndex = 0; // 当前处理的鱼类索引
        for (int row = 1; row <= 4; row++) { // 只使用中间4行（跳过顶部和底部）
            for (int col = 1; col <= 7; col++) { // 只使用中间7列（跳过两侧）
                if (slotIndex < endIndex - startIndex) {
                    String fishName = allFishNames.get(startIndex + slotIndex);
                    // 防御性编程，确保fishName不为null且配置有效
                    if (fishName != null && !fishName.isEmpty()) {
                        try {
                            ItemStack fishItem = createFishDisplayItem(fishConfig, fishName, player);
                            gui.setItem(row * 9 + col, fishItem);
                        } catch (Exception e) {
                            // 出现异常时创建一个错误提示物品
                            // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
                            ItemStack errorItem = createMenuItem(XSeriesUtil.getMaterial("BARRIER"), messageManager.getMessageWithoutPrefix("gui_error_data", "数据错误"), messageManager.getMessageWithoutPrefix("gui_error_data_desc", "此鱼类数据可能有误"));
                            gui.setItem(row * 9 + col, errorItem);
                            plugin.getLogger().warning("创建鱼类展示物品失败: " + fishName + " - " + e.getMessage());
                        }
                    }
                    slotIndex++;
                }
            }
        }
        
        // 创建页面控制按钮，使用正确的槽位ID
        if (page > 0) {
            ItemStack prevButton = createMenuItem(XSeriesUtil.getMaterial("ARROW"), 
                messageManager.getMessageWithoutPrefix("gui_prev_page", "§a上一页"), 
                messageManager.getMessageWithoutPrefix("gui_prev_page_desc", "§7查看上一页"));
            gui.setItem(SlotMapping.FishDex.PREVIOUS_PAGE_SLOT, prevButton);
        }
        
        if (endIndex < allFishNames.size()) {
            ItemStack nextButton = createMenuItem(XSeriesUtil.getMaterial("ARROW"), 
                messageManager.getMessageWithoutPrefix("gui_next_page", "§a下一页"), 
                messageManager.getMessageWithoutPrefix("gui_next_page_desc", "§7查看下一页"));
            gui.setItem(SlotMapping.FishDex.NEXT_PAGE_SLOT, nextButton);
        }
        
        // 创建返回按钮，使用正确的槽位ID
        gui.setItem(SlotMapping.FishDex.BACK_BUTTON_SLOT, createBackButton());
        
        // 保存当前页面
        fishDexPages.put(player.getUniqueId(), page);
        
        return gui;
    }
    
    // 创建钓鱼记录界面
    private Inventory createFishRecord(Player player) {
        Inventory gui = Bukkit.createInventory(
            new GUIHolder(GUIType.FISH_RECORD), 
            27, 
            getGUITitle(GUIType.FISH_RECORD, 0)
        );
        
        // 填充背景
        fillBackground(gui);
        
        // 添加返回按钮
        gui.setItem(SlotMapping.SimpleGUI.BACK_BUTTON_SLOT, createBackButton());
        
        // 检查数据库管理器是否可用
        DB dbManager = plugin.getDB();
        if (dbManager == null || !dbManager.isDatabaseAvailable() || !dbManager.isInitialized()) {
            // 数据库不可用时显示错误信息
            ItemStack errorItem = createMenuItem(
                XSeriesUtil.getMaterial("BARRIER"), 
                messageManager.getMessageWithoutPrefix("gui_error_database", "数据库不可用"), 
                messageManager.getMessageWithoutPrefix("gui_error_database_desc", "无法加载钓鱼记录数据")
            );
            gui.setItem(13, errorItem);
            return gui;
        }
        
        // 从数据库获取钓鱼记录
        FishRecord record = dbManager.getPlayerFishRecord(player.getUniqueId().toString());
        
        // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
        ItemStack totalFishCaught = createMenuItem(XSeriesUtil.getMaterial("FISHING_ROD"), messageManager.getMessageWithoutPrefix("gui_fish_record_total", "总钓鱼数"), 
            String.format(messageManager.getMessageWithoutPrefix("gui_fish_record_total_lore", "%s 条"), record.getTotalFishCaught()));
        gui.setItem(10, totalFishCaught);
        
        // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
        ItemStack rareFish = createMenuItem(XSeriesUtil.getMaterial("ENCHANTED_BOOK"), messageManager.getMessageWithoutPrefix("gui_fish_record_rare", "稀有鱼"), 
            String.format(messageManager.getMessageWithoutPrefix("gui_fish_record_rare_lore", "%s 条"), record.getRareFishCaught()));
        gui.setItem(13, rareFish);
        
        // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
        ItemStack legendaryFish = createMenuItem(XSeriesUtil.getMaterial("NETHER_STAR"), messageManager.getMessageWithoutPrefix("gui_fish_record_legendary", "传说鱼"), 
            String.format(messageManager.getMessageWithoutPrefix("gui_fish_record_legendary_lore", "%s 条"), record.getLegendaryFishCaught()));
        gui.setItem(16, legendaryFish);
        
        return gui;
    }
    
    // 创建帮助指南界面
    private Inventory createHelpGUI(Player player) {
        Inventory gui = Bukkit.createInventory(
            new GUIHolder(GUIType.HELP_GUI), 
            27, 
            getGUITitle(GUIType.HELP_GUI, 0)
        );
        
        // 填充背景
        fillBackground(gui);
        
        // 添加返回按钮
        gui.setItem(SlotMapping.SimpleGUI.BACK_BUTTON_SLOT, createBackButton());
        
        // 添加帮助信息
        List<String> helpLore = Arrays.asList(
            "Fishing Tutorial:",
            "1. Hold fishing rod and press shift to start charging",
            "2. Release to cast line and wait for bite",
            "3. Keep green bar covering fish after bite",
            "",
            "Notes:",
            "- Don't move while fishing",
            "- Different waters have different fish",
            "- Using specific baits increases chance of catching rare fish"
        );
        
        ItemStack helpItem = createMenuItem(XSeriesUtil.getMaterial("BOOK"), "Fishing Guide", 
            helpLore.toArray(new String[0]));
        gui.setItem(13, helpItem);
        
        return gui;
    }
    
    // 处理鱼类图鉴翻页
    public void handleFishDexPage(Player player, boolean next) {
        int currentPage = fishDexPages.getOrDefault(player.getUniqueId(), 0);
        
        // 获取所有鱼类配置
        FileConfiguration fishConfig = config.getFishConfig();
        List<String> allFishNames = new ArrayList<>();
        if (fishConfig.isConfigurationSection("fish")) {
            allFishNames.addAll(fishConfig.getConfigurationSection("fish").getKeys(false));
        }
        
        // 计算总页数（每页显示28个）
        int itemsPerPage = 28;
        int totalPages = (int) Math.ceil((double) allFishNames.size() / itemsPerPage);
        totalPages = Math.max(totalPages, 1); // 至少1页
        
        // 直接打开新页面，不需要关闭当前GUI
        if (next) {
            // 检查是否还有下一页
            if (currentPage + 1 < totalPages) {
                fishDexPages.put(player.getUniqueId(), currentPage + 1);
                openGUI(player, GUIType.FISH_DEX, currentPage + 1);
            }
        } else if (currentPage > 0) {
            fishDexPages.put(player.getUniqueId(), currentPage - 1);
            openGUI(player, GUIType.FISH_DEX, currentPage - 1);
        }
    }
    
    // 创建卖出界面
    private Inventory createSellGUI(Player player) {
        Inventory gui = Bukkit.createInventory(
            new GUIHolder(GUIType.SELL_GUI), 
            54, 
            getGUITitle(GUIType.SELL_GUI, 0)
        );
        
        // 填充边框
        fillBorderBackground(gui);
        
        // 添加关闭按钮
        ItemStack closeButton = createMenuItem(
            XSeriesUtil.getMaterial("BARRIER"), 
            "§c关闭", 
            "§7关闭界面",
            "§7关闭后会自动售卖",
            "§7放在界面中的鱼",
            "§7无法卖出的鱼会被退还"
        );
        gui.setItem(SlotMapping.SellGUI.BACK_BUTTON_SLOT, closeButton);
        
        return gui;
    }
    
    // 打开卖出界面
    public void openSellGUI(Player player) {
        // 检查价格系统是否启用
        if (!plugin.getCustomConfig().isPriceEnabled()) {
            player.sendMessage(messageManager.getMessage("economy_not_enabled", "§c经济系统未启用，无法使用卖出功能！"));
            return;
        }
        openGUI(player, GUIType.SELL_GUI);
    }

    // 处理鱼钩材质选择界面翻页
    public void handleHookMaterialPage(Player player, boolean next) {
        int currentPage = hookMaterialPages.getOrDefault(player.getUniqueId(), 0);
        
        // 获取所有鱼钩配置
        Map<String, Object> allHooks = config.getHookConfigs();
        
        // 过滤出应该在GUI中显示的鱼钩
        List<String> visibleHookNames = new ArrayList<>();
        for (String hookName : allHooks.keySet()) {
            if (!config.isHookVisibleInGui(hookName)) {
                continue;
            }
            
            // 应用搜索过滤
            String searchQuery = getHookSearchQuery(player);
            if (searchQuery.isEmpty() || matchesSearchQuery(hookName, searchQuery)) {
                visibleHookNames.add(hookName);
            }
        }
        
        // 计算总页数（每页显示28个）
        int itemsPerPage = 28;
        int totalPages = (int) Math.ceil((double) visibleHookNames.size() / itemsPerPage);
        totalPages = Math.max(totalPages, 1); // 至少1页
        
        // 直接打开新页面，不需要关闭当前GUI
        if (next) {
            // 检查是否还有下一页
            if (currentPage + 1 < totalPages) {
                hookMaterialPages.put(player.getUniqueId(), currentPage + 1);
                openGUI(player, GUIType.HOOK_MATERIAL, currentPage + 1);
            }
        } else if (currentPage > 0) {
            hookMaterialPages.put(player.getUniqueId(), currentPage - 1);
            openGUI(player, GUIType.HOOK_MATERIAL, currentPage - 1);
        }
    }
    
    // 设置玩家鱼钩材质
    public void setPlayerHookMaterial(Player player, String materialType) {
        UUID playerId = player.getUniqueId();
        
        // 移除materialType中的颜色代码，只保留实际的hook配置id
        String cleanMaterialType = materialType.replaceAll("§[0-9a-fA-Fk-oK-OrR]", "");
        
        // 检查玩家是否有权限使用该鱼钩材质
        if (!plugin.getCustomConfig().hasHookMaterialPermission(player, cleanMaterialType)) {
            player.sendMessage(messageManager.getMessage("hook_no_permission", "§c你没有权限使用这个鱼钩材质！"));
            return;
        }
        
        // 获取数据库管理器
        DB dbManager = plugin.getDB();
        if (dbManager == null) {
            player.sendMessage(messageManager.getMessage("database_unavailable", "§c数据库不可用，无法保存鱼钩材质设置。"));
            return;
        }
        
        try {
            // 不再需要购买检查，直接设置鱼钩材质
            dbManager.setPlayerHookMaterial(playerId.toString(), cleanMaterialType);
        } catch (Exception e) {
            // 捕获数据库操作异常并向玩家显示错误消息
            player.sendMessage(messageManager.getMessage("database_unavailable", "§c数据库操作失败，无法保存鱼钩材质设置。"));
            return;
        }
        
        // 同时更新FishingManager中的内存材质（转换字符串为Material类型）
        Material material = getMaterialFromType(cleanMaterialType);
        plugin.getFish().setPlayerHookMaterial(player, material);
        
        // 获取正确的显示名称
        String displayName = config.getHookDisplayName(cleanMaterialType);
        // 将颜色代码从 § 转换回 &，以避免messageManager.getMessage()方法重复转换
        displayName = displayName.replace('§', '&');
        // 发送设置成功消息，使用正确的显示名称
        player.sendMessage(messageManager.getMessage("hook_material_set", "§a成功设置鱼钩材质为: %s", displayName));
        
        // 重新打开材质选择界面，刷新显示
        openGUI(player, GUIType.HOOK_MATERIAL);
    }
    
    // 将字符串材质类型转换为Material枚举
    private Material getMaterialFromType(String type) {
        // 如果type为null，返回默认材质
        if (type == null) {
            return XSeriesUtil.getMaterial("OAK_LOG");
        }
        
        // 首先尝试从配置中获取材质
        try {
            Material configMaterial = config.getHookMaterial(type);
            if (configMaterial != null) {
                return configMaterial;
            }
        } catch (Exception e) {
            // 忽略异常，继续使用后备方案
        }
        
        // 如果配置中没有找到，使用默认的硬编码映射作为后备
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
            // 羊毛颜色材质
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
                // 尝试直接使用XSeriesUtil.getMaterial获取材质
                try {
                    Material material = XSeriesUtil.getMaterial(type);
                    if (material != null) {
                        return material;
                    }
                } catch (Exception e) {
                    // 忽略异常，继续使用后备方案
                }
                
                // 最后尝试将材质类型转换为大写并移除下划线，然后获取材质
                try {
                    String normalizedType = type.toUpperCase().replace("_", "");
                    Material material = XSeriesUtil.getMaterial(normalizedType);
                    if (material != null) {
                        return material;
                    }
                } catch (Exception e) {
                    // 忽略异常，返回默认木质鱼钩
                }
                return XSeriesUtil.getMaterial("OAK_LOG");
        }
    }
    
    // 重载版本的设置鱼钩材质方法（兼容旧代码）
    public void setPlayerHookMaterial(Player player, Material material, boolean refresh) {
        // 从材质类型获取材质名称
        String materialType = "wood"; // 默认值
        if (material == Material.STICK || material == Material.OAK_LOG) {
            materialType = "wood";
        } else if (material == Material.COBBLESTONE || material == Material.STONE) {
            materialType = "stone";
        } else if (material == Material.IRON_INGOT) {
            materialType = "iron";
        } else if (material == Material.GOLD_INGOT) {
            materialType = "gold";
        } else if (material == Material.DIAMOND) {
            materialType = "diamond";
        }
        
        setPlayerHookMaterial(player, materialType);
    }
    
    // 创建鱼钩材质物品
    private ItemStack createHookMaterialItem(String type, Material material, String name, String description, boolean isSelected) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // 如果名称为"未知鱼钩"，直接使用类型名称作为显示名称，避免依赖语言文件
        if (name.equals("未知鱼钩")) {
            meta.setDisplayName("&6" + type);
        } else {
            meta.setDisplayName(name);
        }
        
        List<String> lore = new ArrayList<>();
        // 如果有描述则添加，否则不添加
        if (!description.isEmpty()) {
            lore.add(description);
        }
        
        // 检查鱼钩是否需要购买
        double vaultPrice = config.getHookVaultPrice(type);
        int pointsPrice = config.getHookPointsPrice(type);
        
        if (config.isHookNeedPurchase(type)) {
            // 添加购买信息，使用canPurchaseWithXXX方法检查是否可以使用该货币购买
            if (config.canPurchaseWithVault(type)) {
                // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
                lore.add(messageManager.getMessageWithoutPrefix("hook_purchase_money", "左键花费 %s 金币购买", String.format("%.2f", vaultPrice)));
            }
            if (config.canPurchaseWithPoints(type)) {
                // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
                lore.add(messageManager.getMessageWithoutPrefix("hook_purchase_points", "右键花费 %d 点卷购买", pointsPrice));
            }
        } else {
            // 不是购买物品，显示选择信息
            lore.add(messageManager.getMessageWithoutPrefix("gui_click_to_switch", "§7点击切换到此材质"));
        }
        
        if (isSelected) {
            lore.add("§a当前已选择");
        }
        
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        
        return item;
    }
    
    // 创建返回按钮
    private ItemStack createBackButton() {
        return createMenuItem(Material.BARRIER, 
            "§cBack", 
            "§7Return to previous menu");
    }
    
    // 创建菜单项
    private ItemStack createMenuItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        
        if (lore != null && lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    // 创建全新设计的鱼钩材质物品
    private ItemStack createNewHookMaterialItem(String type, Material material, String name, String description, boolean isSelected, Player player) {
        // 创建物品
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // 设置显示名称，添加等级和品质标识
            String rarityColor = getRarityColor(type);
            String levelIndicator = getLevelIndicator(type);
            meta.setDisplayName(rarityColor + "§l" + levelIndicator + name);
            
            // 添加描述
            List<String> lore = new ArrayList<>();
            
            // 显示鱼钩类型和ID
            lore.add("§7类型: " + type.replace('_', ' '));
            lore.add("§7ID: hook_" + type);
            lore.add("");
            
            // 添加描述文本，保留原有格式
            if (description != null && !description.isEmpty()) {
                lore.add("§f" + description);
                lore.add("");
            }
            
            // 添加性能指标
            addPerformanceStats(lore, type);
            
            // 添加装饰线
            lore.add("§7§m-------------------");
            
            // 检查是否已购买
            boolean isPurchased = config.hasHookMaterialPermission(player, type);
            
            // 检查是否需要购买
            if (config.isHookNeedPurchase(type)) {
                if (isPurchased) {
                    // 已购买，显示装备提示
                    lore.add("§a已拥有此鱼钩");
                    lore.add(messageManager.getMessageWithoutPrefix("gui_click_to_equip", "§e点击装备"));
                } else {
                    // 未购买，显示价格和购买提示
                    double vaultPrice = config.getHookVaultPrice(type);
                    int pointsPrice = config.getHookPointsPrice(type);
                    
                    // 检查Vault经济插件是否可用
                    boolean vaultAvailable = plugin.getEconomy() != null;
                    
                    // 检查PlayerPoints插件是否可用
                    boolean pointsAvailable = plugin.getPlayerPointsAPI() != null;
                    
                    if (config.canPurchaseWithVault(type) && vaultAvailable) {
                        // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
                        lore.add(messageManager.getMessageWithoutPrefix("hook_purchase_money", "左键花费 %s 金币购买", String.format("%.2f", vaultPrice)));
                    }
                    if (config.canPurchaseWithPoints(type) && pointsAvailable) {
                        // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
                        lore.add(messageManager.getMessageWithoutPrefix("hook_purchase_points", "右键花费 %d 点卷购买", pointsPrice));
                    }
                    
                    // 显示余额信息
                    if (config.canPurchaseWithVault(type) && vaultAvailable) {
                        try {
                            double balance = plugin.getEconomy().getBalance(player);
                            if (balance < vaultPrice) {
                                lore.add("§c金币不足！还需 " + String.format("%.2f", vaultPrice - balance) + " 金币");
                            } else {
                                lore.add("§a金币充足，可以购买");
                            }
                        } catch (Exception e) {
                            // 如果获取余额失败，不显示余额信息
                        }
                    }
                    
                    // 如果没有可用的购买方式，显示提示
                    if ((!config.canPurchaseWithVault(type) || !vaultAvailable) &&
                        (!config.canPurchaseWithPoints(type) || !pointsAvailable)) {
                        lore.add(messageManager.getMessageWithoutPrefix("gui_no_purchase_method", "§c当前暂无可用的购买方式"));
                    }
                }
            } else {
                // 免费鱼钩，直接显示装备提示
                lore.add("§a免费获得");
                lore.add("§e点击装备");
            }
            
            // 如果是当前选择的材质，添加高亮标记
            if (isSelected) {
                lore.add("§7§m-------------------");
                lore.add("§a§l✓ 当前已装备");
                
                // 添加发光效果
                addGlowEffect(item);
            }
            
            // 设置lore
            meta.setLore(lore);
            
            // 设置物品不可破坏，避免意外破坏
            meta.setUnbreakable(true);
            
            // 隐藏不需要的属性
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
            
            // 应用meta到物品
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    // 获取鱼钩稀有度颜色
    private String getRarityColor(String hookType) {
        // 根据鱼钩类型返回不同的稀有度颜色
        switch(hookType.toLowerCase()) {
            case "wood":
            case "stone":
                return "§7";
            case "iron":
            case "gold":
                return "§6";
            case "diamond":
            case "emerald":
                return "§b";
            case "netherite":
                return "§c";
            case "light_blue_wool":
            case "blue_wool":
            case "green_wool":
            case "purple_wool":
                return "§d";
            default:
                return "§f";
        }
    }
    
    // 获取等级指示器
    private String getLevelIndicator(String hookType) {
        // 根据鱼钩类型返回等级指示器
        switch(hookType.toLowerCase()) {
            case "wood":
                return "[新手] ";
            case "stone":
                return "[入门] ";
            case "iron":
                return "[进阶] ";
            case "gold":
                return "[稀有] ";
            case "diamond":
            case "emerald":
                return "[史诗] ";
            case "netherite":
                return "[传说] ";
            case "light_blue_wool":
            case "blue_wool":
            case "green_wool":
            case "purple_wool":
                return "[特殊] ";
            default:
                return "";
        }
    }
    
    // 添加性能指标
    private void addPerformanceStats(List<String> lore, String hookType) {
        // 这里可以从配置中读取实际性能数据，或者根据类型生成示例数据
        double catchRate = 1.0; // 默认捕获率
        double durability = 1.0; // 默认耐用性
        double specialChance = 0.0; // 特殊捕获几率
        
        // 根据鱼钩类型设置性能数据
        switch(hookType.toLowerCase()) {
            case "wood":
                catchRate = 1.0;
                durability = 1.0;
                specialChance = 0.0;
                break;
            case "stone":
                catchRate = 1.1;
                durability = 1.5;
                specialChance = 0.01;
                break;
            case "iron":
                catchRate = 1.2;
                durability = 2.0;
                specialChance = 0.02;
                break;
            case "gold":
                catchRate = 1.3;
                durability = 1.0;
                specialChance = 0.05;
                break;
            case "diamond":
                catchRate = 1.5;
                durability = 3.0;
                specialChance = 0.08;
                break;
            case "emerald":
                catchRate = 1.4;
                durability = 2.5;
                specialChance = 0.1;
                break;
            case "netherite":
                catchRate = 1.8;
                durability = 5.0;
                specialChance = 0.15;
                break;
            case "light_blue_wool":
            case "blue_wool":
            case "green_wool":
            case "purple_wool":
                catchRate = 1.6;
                durability = 2.0;
                specialChance = 0.12;
                break;
        }
        
        // 添加性能指标到lore
        lore.add("§b捕获率: " + String.format("%.1f", catchRate) + "x");
        lore.add("§a耐用性: " + String.format("%.1f", durability) + "x");
        lore.add("§d特殊捕获: " + String.format("%.1f", specialChance * 100) + "%");
        lore.add("");
    }
    
    // 添加发光效果
    private void addGlowEffect(ItemStack item) {
        // 创建一个临时的发光效果
        // 注意：移除直接添加附魔的代码，避免与实际附魔系统冲突
        // 保留此方法但不添加实际附魔，后续可以通过ProtocolLib实现真正的发光效果
    }
    
    // 创建鱼类展示物品～(≧∇≦)/~
    private ItemStack createFishDisplayItem(FileConfiguration fishConfig, String fishName, Player player) {
        String displayName = fishConfig.getString("fish." + fishName + ".display-name", fishName);
        String materialName = fishConfig.getString("fish." + fishName + ".material", "COD");
        Material material = Material.getMaterial(materialName);
        if (material == null) {
            material = Material.COD;
        }
        
        // 获取玩家的钓鱼记录
        DB dbManager = plugin.getDB();
        Map<String, Object> fishStats = dbManager.getPlayerFishStats(player.getUniqueId().toString(), fishName);
        int caughtCount = (int) fishStats.get("caughtCount");
        double maxSize = (double) fishStats.get("maxSize");
        
        // 根据是否钓到过设置不同材质
        if (caughtCount == 0) {
            material = Material.BLACK_WOOL; // 没钓到过，显示黑色羊毛
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
        
        // 添加鱼类信息lore
        List<String> lore = new ArrayList<>();
        
        // 添加季节信息（如果安装了季节插件）
        if (plugin.isRealisticSeasonsEnabled() && plugin.getCustomConfig().isSeasonalFishingEnabled()) {
            MessageManager messageManager = plugin.getMessageManager();
            if (fishConfig.contains("fish." + fishName + ".seasons")) {
                List<String> seasons = fishConfig.getStringList("fish." + fishName + ".seasons");
                if (!seasons.isEmpty() && !seasons.contains("all")) {
                    // 使用messageManager获取文本，不再硬编码颜色代码
                    StringBuilder seasonText = new StringBuilder(messageManager.getMessageWithoutPrefix("gui_season_display", "出现季节: "));
                    boolean first = true;
                    for (String season : seasons) {
                        if (!first) {
                            seasonText.append("、");
                        }
                        // 使用国际化的季节名称
                        String seasonKey = "season." + season.toLowerCase();
                        String seasonName = messageManager.getMessageWithoutPrefix(seasonKey, season);
                        seasonText.append(seasonName);
                        first = false;
                    }
                    lore.add(seasonText.toString());
                } else if (seasons.contains("all")) {
                    String allSeasonsText = messageManager.getMessageWithoutPrefix("season.all", "四季出现");
                    lore.add("§7" + allSeasonsText);
                }
            }
        }
        
        // 添加等级信息 ～(≧∇≦)/~
        Object levelObj = fishConfig.get("fish." + fishName + ".level");
        boolean hasValidLevel = false;
        
        if (levelObj != null) {
            String levelStr = "§7等级: ";
            
            if (levelObj instanceof List) {
                // 处理列表格式的等级配置 (如 - common:70)
                List<?> rawList = (List<?>) levelObj;
                for (Object levelItem : rawList) {
                    if (levelItem instanceof Map) {
                        Map<?, ?> levelMap = (Map<?, ?>) levelItem;
                        for (Map.Entry<?, ?> entry : levelMap.entrySet()) {
                            try {
                                String levelName = entry.getKey().toString();
                                String weight = entry.getValue().toString();
                                levelStr += levelName + "(" + weight + "%), ";
                                hasValidLevel = true;
                            } catch (Exception e) {
                                // 格式错误，跳过
                            }
                        }
                    }
                }
                
                // 如果有有效等级信息，移除末尾的逗号
                if (hasValidLevel && levelStr.length() > 8) {
                    lore.add(levelStr.substring(0, levelStr.length() - 2));
                }
            } else if (levelObj instanceof String) {
                // 处理字符串格式的等级配置
                lore.add("§7等级: " + levelObj.toString());
                hasValidLevel = true;
            } else if (levelObj instanceof Map) {
                // 处理单Map格式的等级配置
                Map<?, ?> levelMap = (Map<?, ?>) levelObj;
                for (Map.Entry<?, ?> entry : levelMap.entrySet()) {
                    try {
                        String levelName = entry.getKey().toString();
                        String weight = entry.getValue().toString();
                        levelStr += levelName + "(" + weight + "%)";
                        hasValidLevel = true;
                        break; // 单Map只取第一个
                    } catch (Exception e) {
                        // 格式错误，跳过
                    }
                }
                
                if (hasValidLevel) {
                    lore.add(levelStr);
                }
            }
        }
        
        // 如果没有有效的等级配置，添加默认提示
        if (!hasValidLevel && levelObj != null) {
            // 尝试直接显示原始配置内容
            lore.add(messageManager.getMessageWithoutPrefix("fish_level_label", "&7Level: ") + levelObj.toString());
        } else if (levelObj == null) {
            lore.add(messageManager.getMessageWithoutPrefix("fish_level_label", "&7Level: ") + messageManager.getMessageWithoutPrefix("fish_level_no_info", "No level info"));
        }
        
        // 添加价值信息
        double value = fishConfig.getDouble("fish." + fishName + ".value", 0);
        lore.add(messageManager.getMessageWithoutPrefix("fish_dex_value_label", "&7Value: ") + value + messageManager.getMessageWithoutPrefix("fish_dex_unit_coins", " coins"));
        
        // 添加描述信息
        String description = fishConfig.getString("fish." + fishName + ".description", "");
        if (!description.isEmpty()) {
            // 转换颜色代码
            description = ChatColor.translateAlternateColorCodes('&', description);
            // 处理描述中的换行符
            if (description.contains("\n")) {
                String[] lines = description.split("\\n");
                for (String line : lines) {
                    lore.add("§7" + line);
                }
            } else {
                lore.add("§7" + description);
            }
        }
        
        // 添加玩家钓鱼记录信息
        if (caughtCount > 0) {
            lore.add(messageManager.getMessageWithoutPrefix("fish_dex_record_title_owned", "&a━━━━━━━Fish Record━━━━━━━"));
            lore.add(messageManager.getMessageWithoutPrefix("fish_dex_record_caught", "&aCaught: ") + caughtCount + messageManager.getMessageWithoutPrefix("fish_dex_record_times", " times"));
            lore.add(messageManager.getMessageWithoutPrefix("fish_dex_record_max_size", "&aMax Size: ") + String.format("%.1f", maxSize) + messageManager.getMessageWithoutPrefix("fish_dex_unit_cm", " cm"));
        } else {
            lore.add(messageManager.getMessageWithoutPrefix("fish_dex_record_title_unowned", "&c━━━━━━━Fish Record━━━━━━━"));
            lore.add(messageManager.getMessageWithoutPrefix("fish_dex_record_not_caught", "&cNot caught yet~"));
            lore.add(messageManager.getMessageWithoutPrefix("fish_dex_record_keep_trying", "&cKeep trying!"));
        }
        
        // 添加尺寸信息
        int minSize = fishConfig.getInt("fish." + fishName + ".min-size", 0);
        int configMaxSize = fishConfig.getInt("fish." + fishName + ".max-size", 0);
        lore.add(messageManager.getMessageWithoutPrefix("fish_dex_size_label", "&7Size: ") + minSize + "-" + configMaxSize + messageManager.getMessageWithoutPrefix("fish_dex_unit_cm", " cm"));
        
        // 设置lore
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        
        // 设置发光效果
        boolean enchantGlow = fishConfig.getBoolean("fish." + fishName + ".enchant-glow", false);
        if (enchantGlow) {
            // 不再添加默认附魔，保持与原版钓鱼附魔兼容
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        
        // 设置Custom Model Data（如果玩家已经钓到过该鱼）
        if (caughtCount > 0) {
            int customModelData = fishConfig.getInt("fish." + fishName + ".custom-model-data", -1);
            if (customModelData != -1) {
                meta.setCustomModelData(customModelData);
            }
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    // 获取材质的显示名称
    private String getMaterialDisplayName(String materialType) {
        switch(materialType.toLowerCase()) {
            case "wood": return "木质鱼钩";
            case "stone": return "石质鱼钩";
            case "iron": return "铁质鱼钩";
            case "gold": return "金质鱼钩";
            case "diamond": return "钻石鱼钩";
            default: return materialType;
        }
    }
    
    // 填充完整背景
    private void fillBackground(Inventory gui) {
        ItemStack background = createBackgroundItem();
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, background);
        }
    }
    
    // 填充边界背景（顶部、底部和两侧）
    private void fillBorderBackground(Inventory gui) {
        ItemStack background = createBackgroundItem();
        
        // 填充顶部和底部
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, background);
            gui.setItem(gui.getSize() - 9 + i, background);
        }
        
        // 填充两侧
        for (int i = 1; i < 5; i++) {
            gui.setItem(i * 9, background);
            gui.setItem(i * 9 + 8, background);
        }
    }
    
    // 创建背景物品
    private ItemStack createBackgroundItem() {
        ItemStack item = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE); // 浅蓝色玻璃，清清凉凉的感觉～
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" "); // 空名称
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }
    
    // 获取插件实例
    public kkfish getPlugin() {
        return plugin;
    }
    
    // 获取鱼类图鉴页面映射（供GUI使用）
    public Map<UUID, Integer> getFishDexPages() {
        return fishDexPages;
    }
    
    // 获取玩家的搜索关键词
    public String getHookSearchQuery(Player player) {
        return hookSearchQueries.getOrDefault(player.getUniqueId(), "");
    }
    
    // 设置玩家的搜索关键词
    public void setHookSearchQuery(Player player, String query) {
        if (query == null || query.isEmpty()) {
            hookSearchQueries.remove(player.getUniqueId());
        } else {
            hookSearchQueries.put(player.getUniqueId(), query);
        }
    }
    
    // 快捷方法：打开主菜单
    public void openMainMenu(Player player) {
        openGUI(player, GUIType.MAIN_MENU);
    }
    
    // 快捷方法：打开鱼钩材质选择
    public void openHookMaterial(Player player) {
        openGUI(player, GUIType.HOOK_MATERIAL);
    }
    
    // 快捷方法：打开鱼类图鉴
    public void openFishDex(Player player) {
        // 确保使用正确的GUI类型和页面参数打开鱼类图鉴
        openGUI(player, GUIType.FISH_DEX, 0);
    }
    
    // 快捷方法：打开钓鱼记录
    public void openFishRecord(Player player) {
        openGUI(player, GUIType.FISH_RECORD);
    }
    
    // 快捷方法：打开帮助指南
    public void openHelp(Player player) {
        // 直接打开帮助指南GUI，无需特殊权限
        openGUI(player, GUIType.HELP_GUI);
    }
    
    // 快捷方法：打开比赛分类
    public void openCompetitionCategory(Player player) {
        openGUI(player, GUIType.COMPETITION_CATEGORY);
    }
    
    // 快捷方法：打开奖励预览
    public void openRewardPreview(Player player, String competitionId) {
        openGUI(player, GUIType.REWARD_PREVIEW, getCompetitionIndex(competitionId));
    }
    
    // 创建比赛分类GUI
    private Inventory createCompetitionCategoryGUI(Player player) {
        Inventory gui = Bukkit.createInventory(
            new GUIHolder(GUIType.COMPETITION_CATEGORY), 
            27, 
            getGUITitle(GUIType.COMPETITION_CATEGORY, 0)
        );
        
        // 填充背景
        fillBackground(gui);
        
        // 添加返回按钮，使用正确的槽位ID
        gui.setItem(SlotMapping.CompetitionCategory.BACK_BUTTON_SLOT, createBackButton());
        
        // 获取CompetitionManager
        Compete compete = plugin.getCompete();
        Collection<CompetitionConfig> competitionConfigs = compete.getCompetitionConfigs();
        
        // 添加比赛分类按钮
        int slot = 10; // 开始位置
        for (CompetitionConfig config : competitionConfigs) {
            
            // 创建比赛类型按钮
            ItemStack competitionButton = createCompetitionButton(config);
            gui.setItem(slot, competitionButton);
            slot++;
            
            // 控制布局，每行显示3个
            if (slot == 13) slot = 14; // 跳过中间位置
            if (slot > 16) break; // 超出范围停止添加
        }
        
        return gui;
    }
    
    // 创建奖励预览GUI
    private Inventory createRewardPreviewGUI(Player player, int competitionIndex) {
        Inventory gui = Bukkit.createInventory(
            new GUIHolder(GUIType.REWARD_PREVIEW), 
            54, 
            getGUITitle(GUIType.REWARD_PREVIEW, 0)
        );
        
        // 填充背景
        fillBorderBackground(gui);
        
        // 添加返回按钮
        gui.setItem(49, createBackButton());
        
        // 获取CompetitionManager
        Compete compete = plugin.getCompete();
        Collection<CompetitionConfig> competitionConfigs = compete.getCompetitionConfigs();
        
        // 获取对应的比赛配置
        CompetitionConfig config = getCompetitionByIndex(competitionConfigs, competitionIndex);
        if (config == null) {
            // 显示错误信息
            // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
            ItemStack errorItem = createMenuItem(Material.BARRIER, messageManager.getMessageWithoutPrefix("gui_error_competition_config", "比赛配置不存在"), messageManager.getMessageWithoutPrefix("gui_error_competition_config_desc", "请检查比赛配置"));
            gui.setItem(22, errorItem);
            return gui;
        }
        
        // 显示比赛信息
        ItemStack competitionInfo = createCompetitionInfoItem(config);
        gui.setItem(4, competitionInfo);
        
        // 显示奖励列表
        Map<Integer, List<String>> rewards = config.getRewards();
        int slot = 10; // 开始位置
        
        for (Map.Entry<Integer, List<String>> entry : rewards.entrySet()) {
            int rank = entry.getKey();
            List<String> commands = entry.getValue();
            
            ItemStack rewardItem = createRewardItem(rank, commands);
            gui.setItem(slot, rewardItem);
            slot++;
            
            // 控制布局
            if (slot % 9 == 8) slot += 2; // 跳过边缘位置
        }
        
        return gui;
    }
    
    // 创建比赛按钮
    private ItemStack createCompetitionButton(CompetitionConfig config) {
        Material material = Material.FISHING_ROD;
        
        // 根据比赛类型设置不同的材质
        switch (config.getType()) {
            case "AMOUNT":
                material = Material.COOKED_COD;
                break;
            case "TOTAL_VALUE":
                material = Material.GOLD_INGOT;
                break;
            case "SINGLE_VALUE":
                material = Material.NETHER_STAR;
                break;
            default:
                // 自定义比赛类型
                material = Material.ENCHANTED_BOOK;
                break;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // 设置显示名称
        String displayName = config.getName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = config.getId();
        }
        // 使用不带前缀的消息方法，确保正确处理颜色代码
        meta.setDisplayName(messageManager.getMessageWithoutPrefix("gui_competition_display_name", displayName, displayName));
        
        // 设置lore
        List<String> lore = new ArrayList<>();
        // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
        lore.add(String.format(messageManager.getMessageWithoutPrefix("gui_competition_info_type", "比赛类型: %s"), getCompetitionTypeDisplayName(config.getType())));
        lore.add(String.format(messageManager.getMessageWithoutPrefix("gui_competition_info_duration", "持续时间: %s分钟"), config.getDuration()));
        lore.add(String.format(messageManager.getMessageWithoutPrefix("gui_competition_info_min_players", "报名人数: %s人"), config.getMinPlayers()));
        lore.add(String.format(messageManager.getMessageWithoutPrefix("gui_competition_info_schedule", "比赛时间: %s"), config.getSchedule()));
        lore.add("Click to view reward details");
        
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        
        return item;
    }
    
    // 创建比赛信息物品
    private ItemStack createCompetitionInfoItem(CompetitionConfig config) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        
        // 设置显示名称
        String displayName = config.getName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = config.getId();
        }
        // 让messageManager自动处理颜色代码，不再硬编码
        meta.setDisplayName(messageManager.getMessage("gui_competition_display_name", "§c" + displayName, displayName));
        
        // 设置lore
        List<String> lore = new ArrayList<>();
        // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
        lore.add(String.format(messageManager.getMessageWithoutPrefix("gui_competition_info_id", "比赛ID: %s"), config.getId()));
        lore.add(String.format(messageManager.getMessageWithoutPrefix("gui_competition_info_type", "比赛类型: %s"), getCompetitionTypeDisplayName(config.getType())));
        lore.add(String.format(messageManager.getMessageWithoutPrefix("gui_competition_info_duration", "持续时间: %s分钟"), config.getDuration()));
        lore.add(String.format(messageManager.getMessageWithoutPrefix("gui_competition_info_min_players", "报名人数: %s人"), config.getMinPlayers()));
        lore.add(String.format(messageManager.getMessageWithoutPrefix("gui_competition_info_schedule", "比赛时间: %s"), config.getSchedule()));
        
        // 添加其他信息
        if (config.getDisplayConfig().isScoreboardEnabled()) {
            lore.add(messageManager.getMessageWithoutPrefix("gui_competition_info_scoreboard_enabled", "计分板: 已启用"));
        } else {
            lore.add(messageManager.getMessageWithoutPrefix("gui_competition_info_scoreboard_disabled", "计分板: 已禁用"));
        }
        
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        
        return item;
    }
    
    // 创建奖励物品
    private ItemStack createRewardItem(int rank, List<String> commands) {
        Material material = Material.CHEST;
        
        // 根据排名设置不同的材质
        if (rank == 1) {
            material = Material.GOLD_BLOCK;
        } else if (rank == 2) {
            material = Material.IRON_BLOCK;
        } else if (rank == 3) {
            material = Material.STONE;
        }
        
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        // 设置显示名称
        String rankName = String.format(messageManager.getMessageWithoutPrefix("gui_competition_rank_reward", "第%s名奖励"), rank);
        // 让messageManager自动处理颜色代码，不再硬编码
        meta.setDisplayName(messageManager.getMessage("gui_competition_reward_display_name", "§a" + rankName, rankName));
        
        // 设置lore
        List<String> lore = new ArrayList<>();
        // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
        lore.add(messageManager.getMessageWithoutPrefix("gui_competition_reward_content", "奖励内容:"));
        
        for (String command : commands) {
            // 移除命令前缀（如/tell、/give等），只显示奖励内容
            String rewardText = command.replace("%player%", "玩家");
            
            // 简单处理常见的命令格式，提取奖励内容
            if (rewardText.startsWith("/give")) {
                // 提取物品名称和数量
                String[] parts = rewardText.split(" ");
                if (parts.length >= 3) {
                    String itemName = parts[2];
                    String amount = "1";
                    if (parts.length >= 4) {
                        amount = parts[3];
                    }
                    rewardText = "物品: " + itemName + " x" + amount;
                }
            } else if (rewardText.startsWith("/eco give")) {
                // 提取金币数量
                String[] parts = rewardText.split(" ");
                if (parts.length >= 4) {
                    String amount = parts[3];
                    rewardText = "金币: " + amount;
                }
            }
            
            // 直接使用普通文本，不再添加额外的颜色代码，让messageManager自动处理
            lore.add(String.format(messageManager.getMessageWithoutPrefix("gui_competition_reward_item", "- %s"), rewardText));
        }
        
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        
        return item;
    }
    
    // 获取比赛类型的显示名称
    private String getCompetitionTypeDisplayName(String type) {
        switch (type) {
            case "AMOUNT":
                return messageManager.getMessageWithoutPrefix("gui_competition_type_amount", "AMOUNT");
            case "TOTAL_VALUE":
                return messageManager.getMessageWithoutPrefix("gui_competition_type_total_value", "TOTAL_VALUE");
            case "SINGLE_VALUE":
                return messageManager.getMessageWithoutPrefix("gui_competition_type_single_value", "SINGLE_VALUE");
            case "POINTS_ONLY":
                return messageManager.getMessageWithoutPrefix("gui_competition_type_points_only", "POINTS_ONLY");
            default:
                // 直接返回类型名称
                return type;
        }
    }
    
    // 根据索引获取比赛配置
    private CompetitionConfig getCompetitionByIndex(Collection<CompetitionConfig> competitionConfigs, int index) {
        // 如果索引为-1，返回第一个比赛配置
        if (index == -1 && !competitionConfigs.isEmpty()) {
            return competitionConfigs.iterator().next();
        }
        
        int currentIndex = 0;
        for (CompetitionConfig config : competitionConfigs) {
            if (currentIndex == index) {
                return config;
            }
            currentIndex++;
        }
        return null;
    }
    
    // 获取比赛配置的索引
    private int getCompetitionIndex(String competitionId) {
        Compete compete = plugin.getCompete();
        Collection<CompetitionConfig> competitionConfigs = compete.getCompetitionConfigs();
        
        int index = 0;
        for (CompetitionConfig config : competitionConfigs) {
            if (config.getId().equals(competitionId)) {
                return index;
            }
            index++;
        }
        return -1;
    }
}
