package me.kkfish.listeners;

import org.bukkit.Bukkit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import net.milkbowl.vault.economy.Economy;
import me.kkfish.kkfish;
import me.kkfish.managers.Config;
import me.kkfish.managers.GUI;
import me.kkfish.misc.MessageManager;
import me.kkfish.managers.Compete;
import me.kkfish.competition.CompetitionConfig;
import me.kkfish.gui.GUIMenuLoader;
import me.kkfish.gui.SlotMapping;
import me.kkfish.gui.GUIHolder;
import me.kkfish.utils.XSeriesUtil;
import java.util.function.Consumer;

public class GUIListener implements Listener {
    
    private static final Material AIR_MATERIAL = XSeriesUtil.parseMaterial("AIR");
    private static final Material LIGHT_BLUE_GLASS = XSeriesUtil.getMaterial("LIGHT_BLUE_STAINED_GLASS_PANE");
    private final kkfish plugin;
    private final me.kkfish.managers.GUI guiManager;
    
    private final Map<String, String> displayNameToHookNameMap = new ConcurrentHashMap<>();
    
    public GUIListener(me.kkfish.managers.GUI guiManager) {
        this.plugin = guiManager.getPlugin();
        this.guiManager = guiManager;
        
        initializeDisplayNameToHookNameMap();
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    private void initializeDisplayNameToHookNameMap() {
        displayNameToHookNameMap.clear();
        
        Config config = guiManager.getPlugin().getCustomConfig();
        
        Map<String, Object> hookConfigs = config.getHookConfigs();
        
        for (String hookName : hookConfigs.keySet()) {
            String displayName = config.getHookDisplayName(hookName);
            String strippedDisplayName = ChatColor.stripColor(displayName);
            
            displayNameToHookNameMap.put(strippedDisplayName, hookName);
            
            String cleanName = strippedDisplayName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "");
            if (!cleanName.isEmpty() && !displayNameToHookNameMap.containsKey(cleanName)) {
                displayNameToHookNameMap.put(cleanName, hookName);
            }
            
            String noBracketsName = strippedDisplayName.replaceAll("\\[.*?\\]", "").trim();
            if (!noBracketsName.isEmpty() && !displayNameToHookNameMap.containsKey(noBracketsName)) {
                displayNameToHookNameMap.put(noBracketsName, hookName);
            }
        }
        
        if (config.isDebugMode()) {
            config.debugLog("显示名称到鱼钩配置名称映射初始化完成，映射数量: " + displayNameToHookNameMap.size());
        }
    }
    
    public void reloadDisplayNameMap() {
        initializeDisplayNameToHookNameMap();
    }
    
    public String getHookNameFromDisplayName(String displayName) {
        String hookName = displayNameToHookNameMap.get(displayName);
        
        if (hookName == null) {
            String noBracketsName = displayName.replaceAll("\\[.*?\\]", "").trim();
            hookName = displayNameToHookNameMap.get(noBracketsName);
        }
        
        if (hookName == null) {
            String cleanName = displayName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "");
            hookName = displayNameToHookNameMap.get(cleanName);
        }
        
        return hookName;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (!(holder instanceof GUIHolder)) {
            return;
        }
        
        GUIHolder guiHolder = (GUIHolder) holder;
        GUI.GUIType guiType = guiHolder.getType();
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            if (guiType == GUI.GUIType.SELL_GUI) {
                GUIMenuLoader.MenuConfig menuConfig = guiManager.getMenuLoader().getMenuConfig("sell_gui");
                Set<Integer> configuredSlots = new HashSet<>();
                
                if (menuConfig != null) {
                    for (GUIMenuLoader.MenuConfig.MenuItem item : menuConfig.getItems().values()) {
                        configuredSlots.addAll(item.getSlots());
                    }
                }
                
                int clickedSlot = event.getRawSlot();
                if (!configuredSlots.contains(clickedSlot)) {
                    event.setCancelled(false);
                } else {
                    event.setCancelled(true);
                }
            } else {
                event.setCancelled(true);
            }
        } else {
            if (guiType != GUI.GUIType.SELL_GUI) {
                if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }
        
        if (clickedItem == null || clickedItem.getType() == AIR_MATERIAL) {
            return;
        }
        
        Material lightBlueGlass = LIGHT_BLUE_GLASS;
        if (lightBlueGlass != null && clickedItem.getType() == lightBlueGlass && 
            clickedItem.hasItemMeta() && 
            clickedItem.getItemMeta().getDisplayName().equals(" ")) {
            if (guiType != GUI.GUIType.HOOK_MATERIAL) {
                if (guiType == GUI.GUIType.FISH_DEX) {
                    int currentPage = guiManager.getFishDexPages().getOrDefault(player.getUniqueId(), 0);
                    guiManager.openGUI(player, guiType, currentPage);
                } else if (guiType == GUI.GUIType.REWARD_PREVIEW || guiType == GUI.GUIType.SELL_GUI) {
                } else {
                    guiManager.openGUI(player, guiType);
                }
                return;
            }
        }
        
        handleGUIClick(player, event);
    }
    
    private void handleGUIClick(Player player, InventoryClickEvent event) {
        GUIHolder holder = (GUIHolder) event.getInventory().getHolder();
        if (holder == null) return;
        
        GUI.GUIType type = holder.getType();
        int slot = event.getRawSlot();
        
        if (handleConfiguredActions(player, event, type, slot)) {
            return;
        }
        
        switch(type) {
            case MAIN_MENU:
                handleMainMenuClickBySlot(player, slot);
                break;
            case HOOK_MATERIAL:
                handleHookMaterialClickBySlot(player, slot, holder.getPage());
                break;
            case FISH_DEX:
                handleFishDexClickBySlot(player, slot, holder.getPage());
                break;
            case FISH_RECORD:
            case HELP_GUI:
                handleSimpleGUIClickBySlot(player, slot, type);
                break;
            case SELL_GUI:
                break;
            case COMPETITION_CATEGORY:
                handleCompetitionCategoryClickBySlot(player, slot);
                break;
            case REWARD_PREVIEW:
                handleRewardPreviewClickBySlot(player, slot);
                break;
        }
    }
    
    private boolean handleConfiguredActions(Player player, InventoryClickEvent event, GUI.GUIType type, int slot) {
        String menuName = getMenuNameFromType(type);
        if (menuName == null) {
            return false;
        }
        
        if (!guiManager.getMenuLoader().hasMenuConfig(menuName)) {
            return false;
        }
        
        GUIMenuLoader.MenuConfig menuConfig = guiManager.getMenuLoader().getMenuConfig(menuName);
        if (menuConfig == null) {
            return false;
        }
        
        GUIMenuLoader.MenuConfig.MenuItem clickedItem = findItemBySlot(menuConfig, slot);
        if (clickedItem == null) {
            return false;
        }
        
        java.util.List<String> actions = event.isLeftClick() ? clickedItem.getLeftClickActions() : clickedItem.getRightClickActions();
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        
        guiManager.getActionHandler().handleActions(player, actions, event);
        return true;
    }
    
    private GUIMenuLoader.MenuConfig.MenuItem findItemBySlot(GUIMenuLoader.MenuConfig menuConfig, int slot) {
        for (GUIMenuLoader.MenuConfig.MenuItem item : menuConfig.getItems().values()) {
            if (item.getSlots().contains(slot)) {
                return item;
            }
        }
        return null;
    }
    
    private String getMenuNameFromType(GUI.GUIType type) {
        switch(type) {
            case MAIN_MENU:
                return "main_menu";
            case HOOK_MATERIAL:
                return "hook_material";
            case FISH_DEX:
                return "fish_dex";
            case FISH_RECORD:
                return "fish_record";
            case HELP_GUI:
                return "help_gui";
            case COMPETITION_CATEGORY:
                return "competition_category";
            case REWARD_PREVIEW:
                return "reward_preview";
            case SELL_GUI:
                return "sell_gui";
            default:
                return null;
        }
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (!(holder instanceof GUIHolder)) {
            return;
        }

        GUIHolder guiHolder = (GUIHolder) holder;
        if (guiHolder.getType() == GUI.GUIType.SELL_GUI) {
            GUIMenuLoader.MenuConfig menuConfig = guiManager.getMenuLoader().getMenuConfig("sell_gui");
            Set<Integer> configuredSlots = new HashSet<>();
            if (menuConfig != null) {
                for (GUIMenuLoader.MenuConfig.MenuItem item : menuConfig.getItems().values()) {
                    configuredSlots.addAll(item.getSlots());
                }
            }

            int topSize = event.getView().getTopInventory().getSize();
            for (int slot : event.getRawSlots()) {
                if (slot < topSize && configuredSlots.contains(slot)) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        for (int slot : event.getRawSlots()) {
            if (slot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof GUIHolder) {
            GUIHolder guiHolder = (GUIHolder) holder;
            GUI.GUIType guiType = guiHolder.getType();
            
            if (guiType == GUI.GUIType.SELL_GUI) {
                Player player = (Player) event.getPlayer();
                Inventory inventory = event.getInventory();
                
                int totalValue = 0;
                int soldCount = 0;
                int refundCount = 0;
                
                GUIMenuLoader.MenuConfig menuConfig = guiManager.getMenuLoader().getMenuConfig("sell_gui");
                Set<Integer> configuredSlots = new HashSet<>();
                
                if (menuConfig != null) {
                    for (GUIMenuLoader.MenuConfig.MenuItem item : menuConfig.getItems().values()) {
                        configuredSlots.addAll(item.getSlots());
                    }
                }
                
                for (int i = 0; i < inventory.getSize(); i++) {
                    if (configuredSlots.contains(i)) {
                        continue;
                    }
                    
                    ItemStack item = inventory.getItem(i);
                    if (item != null && item.getType() != AIR_MATERIAL) {
                        int value = sellFishItem(player, item);
                        if (value > 0) {
                            totalValue += value;
                            soldCount++;
                            inventory.setItem(i, null);
                        } else {
                            player.getInventory().addItem(item).values().forEach(remaining ->
                                    player.getWorld().dropItemNaturally(player.getLocation(), remaining));
                            refundCount++;
                            inventory.setItem(i, null);
                        }
                    }
                }
                
                MessageManager messageManager = guiManager.getPlugin().getMessageManager();
                if (soldCount > 0) {
                    player.sendMessage(messageManager.getMessage("sell_success", "§a成功卖出 &e%s &a条鱼，获得 &e%s &a金币", soldCount, totalValue));
                }
                if (refundCount > 0) {
                    player.sendMessage(messageManager.getMessage("sell_refund", "§c无法卖出 &e%s &c个物品，已退还到背包", refundCount));
                }
                if (soldCount == 0 && refundCount == 0) {
                    player.sendMessage(messageManager.getMessage("sell_empty", "§e卖出界面中没有可处理的物品"));
                }
                
                return;
            }
        }
        
        if (holder instanceof GUIHolder) {
            GUIHolder guiHolder = (GUIHolder) holder;
            if (guiHolder.getType() == GUI.GUIType.HOOK_MATERIAL) {
                if (event.getPlayer() instanceof Player) {
                    Player player = (Player) event.getPlayer();
                    guiManager.setHookSearchQuery(player, null);
                }
            }
        }
    }
    
    private int sellFishItem(Player player, ItemStack item) {
        try {
            String fishUUIDStr = null;
            int value = 0;
            
            Object uuidObj = me.kkfish.utils.NBTUtil.getNBTData(item, "fish_uuid");
            if (uuidObj != null) {
                fishUUIDStr = uuidObj.toString();
                
                if (fishUUIDStr != null) {
                    value = kkfish.getInstance().getDB().getFishValueByUUID(fishUUIDStr);
                }
            }
            
            if (value <= 0 && item.hasItemMeta()) {
                try {
                    ItemMeta meta = item.getItemMeta();
                    PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    NamespacedKey uuidKey = new NamespacedKey(kkfish.getInstance(), "fish_uuid");
                    
                    if (pdc.has(uuidKey, PersistentDataType.STRING)) {
                        fishUUIDStr = pdc.get(uuidKey, PersistentDataType.STRING);
                        
                        if (fishUUIDStr != null) {
                            value = kkfish.getInstance().getDB().getFishValueByUUID(fishUUIDStr);
                        }
                    }
                } catch (Exception e) {
                    kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.sell_fish_pdc_error", "Failed to read fish PDC data: ") + e.getMessage());
                }
            }
            
            String fishName = getItemNameFromItem(item);
            boolean hasItemRewards = fishName != null && plugin.getCustomConfig().getItemValue().hasItemRewards(fishName);
            
            if (hasItemRewards) {
                List<ItemStack> itemRewards = plugin.getCustomConfig().getItemValue().getItemRewards(fishName);
                for (ItemStack reward : itemRewards) {
                    player.getInventory().addItem(reward);
                }
            }
            
            if (value > 0 && plugin.getCustomConfig().isEconomyEnabled()) {
                Economy economy = plugin.getEconomy();
                if (economy != null) {
                    economy.depositPlayer(player, value);
                    if (fishUUIDStr != null) {
                        plugin.getDB().removeFishUUIDValue(fishUUIDStr);
                    }
                    return value;
                }
            }

            if (hasItemRewards) {
                return value > 0 ? value : 1;
            }
            
        } catch (Exception e) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.gui_listener_sell_fish_failed", "§eFailed to sell fish item: " + e.getMessage(), e.getMessage()));
        }
        
        return 0;
    }
    
    private String getItemNameFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return null;
        }
        
        String displayName = ChatColor.stripColor(meta.getDisplayName());
        return displayName;
    }
    
    private void handleMainMenuClickBySlot(Player player, int slot) {
        if (SlotMapping.MainMenu.FUNCTIONAL_SLOTS.contains(slot)) {
            switch (slot) {
                case SlotMapping.MainMenu.HOOK_MATERIAL_SLOT:
                    guiManager.openGUI(player, GUI.GUIType.HOOK_MATERIAL);
                    break;
                case SlotMapping.MainMenu.FISH_DEX_SLOT:
                    guiManager.openGUI(player, GUI.GUIType.FISH_DEX);
                    break;
                case SlotMapping.MainMenu.FISH_RECORD_SLOT:
                    guiManager.openGUI(player, GUI.GUIType.FISH_RECORD);
                    break;
                case SlotMapping.MainMenu.HELP_GUI_SLOT:
                    guiManager.openHelp(player);
                    break;
                case SlotMapping.MainMenu.COMPETITION_SLOT:
                    guiManager.openCompetitionCategory(player);
                    break;
            }
        }
    }
    
    private void handleHookMaterialClickBySlot(Player player, int slot, int currentPage) {
        if (SlotMapping.HookMaterial.CONTROL_SLOTS.contains(slot)) {
            if (slot == SlotMapping.HookMaterial.BACK_BUTTON_SLOT) {
                guiManager.openGUI(player, GUI.GUIType.MAIN_MENU);
            } else if (slot == SlotMapping.HookMaterial.PREVIOUS_PAGE_SLOT) {
                guiManager.handleHookMaterialPage(player, false);
            } else if (slot == SlotMapping.HookMaterial.NEXT_PAGE_SLOT) {
                guiManager.handleHookMaterialPage(player, true);
            }
        } else if (SlotMapping.HookMaterial.isItemDisplaySlot(slot)) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            ItemStack clickedItem = inventory.getItem(slot);
            
            if (clickedItem != null && clickedItem.getType() != AIR_MATERIAL && clickedItem.hasItemMeta()) {
                String displayName = clickedItem.getItemMeta().getDisplayName();
                
                String strippedName = ChatColor.stripColor(displayName);
                
                String hookName = displayNameToHookNameMap.get(strippedName);
                
                if (hookName == null) {
                    String noBracketsName = strippedName.replaceAll("\\[.*?\\]", "").trim();
                    hookName = displayNameToHookNameMap.get(noBracketsName);
                }
                
                if (hookName == null) {
                    String cleanName = strippedName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "");
                    hookName = displayNameToHookNameMap.get(cleanName);
                }
                
                if (hookName != null) {
                    guiManager.setPlayerHookMaterial(player, hookName);
                } else {
                    Config config = guiManager.getPlugin().getCustomConfig();
                    Map<String, Object> hookConfigs = config.getHookConfigs();
                    
                    for (String configHookName : hookConfigs.keySet()) {
                        String configDisplayName = config.getHookDisplayName(configHookName);
                        String strippedConfigName = ChatColor.stripColor(configDisplayName);
                        
                        if (strippedName.contains(strippedConfigName) || strippedConfigName.contains(strippedName)) {
                            guiManager.setPlayerHookMaterial(player, configHookName);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    private String getCategoryFromSlot(int slot) {
        switch (slot) {
            case SlotMapping.HookMaterial.CATEGORY_BASIC_SLOT:
                return "basic";
            case SlotMapping.HookMaterial.CATEGORY_INTERMEDIATE_SLOT:
                return "intermediate";
            case SlotMapping.HookMaterial.CATEGORY_ADVANCED_SLOT:
                return "advanced";
            case SlotMapping.HookMaterial.CATEGORY_RARE_SLOT:
                return "rare";
            case SlotMapping.HookMaterial.CATEGORY_LEGENDARY_SLOT:
                return "legendary";
            default:
                return "all";
        }
    }
    
    private String getSortByFromSlot(int slot) {
        switch (slot) {
            case SlotMapping.HookMaterial.SORT_LEVEL_SLOT:
                return "level";
            case SlotMapping.HookMaterial.SORT_PRICE_SLOT:
                return "price";
            default:
                return "name";
        }
    }
    
    private void handleFishDexClickBySlot(Player player, int slot, int currentPage) {
        if (SlotMapping.FishDex.CONTROL_SLOTS.contains(slot)) {
            if (slot == SlotMapping.FishDex.BACK_BUTTON_SLOT) {
                guiManager.openGUI(player, GUI.GUIType.MAIN_MENU);
            } else if (slot == SlotMapping.FishDex.PREVIOUS_PAGE_SLOT) {
                guiManager.handleFishDexPage(player, false);
            } else if (slot == SlotMapping.FishDex.NEXT_PAGE_SLOT) {
                guiManager.handleFishDexPage(player, true);
            }
        }
    }
    
    private void handleSimpleGUIClickBySlot(Player player, int slot, GUI.GUIType type) {
        if (slot == SlotMapping.SimpleGUI.BACK_BUTTON_SLOT) {
            guiManager.openGUI(player, GUI.GUIType.MAIN_MENU);
        }
    }
    
    private void handleCompetitionCategoryClickBySlot(Player player, int slot) {
        if (slot == SlotMapping.CompetitionCategory.BACK_BUTTON_SLOT) {
            guiManager.openGUI(player, GUI.GUIType.MAIN_MENU);
            return;
        }
        
        if (SlotMapping.CompetitionCategory.isItemDisplaySlot(slot)) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            ItemStack clickedItem = inventory.getItem(slot);
            
            if (clickedItem != null && clickedItem.getType() != AIR_MATERIAL && clickedItem.hasItemMeta()) {
                Compete compete = guiManager.getPlugin().getCompete();
                Collection<CompetitionConfig> competitionConfigs = compete.getCompetitionConfigs();
                
                String itemName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());
                
                for (CompetitionConfig config : competitionConfigs) {
                    String configName = config.getName();
                    
                    if (configName == null || configName.isEmpty()) {
                        configName = config.getId();
                    }
                    
                    if (itemName.contains(configName)) {
                        guiManager.openRewardPreview(player, config.getId());
                        return;
                    }
                }
            }
        }
    }
    
    private void handleRewardPreviewClickBySlot(Player player, int slot) {
        if (slot == SlotMapping.RewardPreview.BACK_BUTTON_SLOT) {
            guiManager.openCompetitionCategory(player);
            return;
        }
    }
}
