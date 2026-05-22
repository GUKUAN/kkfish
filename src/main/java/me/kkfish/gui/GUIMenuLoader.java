package me.kkfish.gui;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import me.kkfish.kkfish;

public class GUIMenuLoader {
    private final kkfish plugin;
    private final Logger logger;
    private final Map<String, MenuConfig> menuConfigs = new HashMap<>();
    private final File guiFolder;
    
    public GUIMenuLoader(kkfish plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.guiFolder = new File(plugin.getDataFolder(), "gui");
        if (!guiFolder.exists()) {
            guiFolder.mkdirs();
        }
        loadAllMenus();
    }
    
    public void loadAllMenus() {
        menuConfigs.clear();
        
        loadBuiltInMenus();
        
        loadCustomMenus();
        
        plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("log.menu_loaded", "Loaded %s menu configurations", menuConfigs.size()));
    }
    
    private void loadBuiltInMenus() {
        String[] menuNames = {
            "main_menu",
            "fish_dex",
            "hook_material",
            "fish_record",
            "help_gui",
            "competition_category",
            "reward_preview",
            "sell_gui"
        };
        
        for (String menuName : menuNames) {
            File menuFile = new File(guiFolder, menuName + ".yml");
            if (!menuFile.exists()) {
                plugin.saveResource("gui/" + menuName + ".yml", false);
            }
            loadMenu(menuName);
        }
    }
    
    private void loadCustomMenus() {
        File[] files = guiFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String menuName = file.getName().replace(".yml", "");
                loadMenu(menuName);
            }
        }
    }
    
    private void loadMenu(String menuName) {
        File menuFile = new File(guiFolder, menuName + ".yml");
        if (menuFile.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(menuFile);
            MenuConfig menuConfig = new MenuConfig(menuName, config);
            menuConfigs.put(menuName, menuConfig);
        }
    }
    
    public MenuConfig getMenuConfig(String menuName) {
        return menuConfigs.get(menuName);
    }
    
    public boolean hasMenuConfig(String menuName) {
        return menuConfigs.containsKey(menuName);
    }
    
    public static class MenuConfig {
        private final String name;
        private final FileConfiguration config;
        private final Map<String, MenuItem> items = new HashMap<>();
        
        public MenuConfig(String name, FileConfiguration config) {
            this.name = name;
            this.config = config;
            loadItems();
        }
        
        private void loadItems() {
            ConfigurationSection itemsSection = config.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String itemId : itemsSection.getKeys(false)) {
                    ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                    if (itemSection != null) {
                        MenuItem item = new MenuItem(itemId, itemSection);
                        items.put(itemId, item);
                    }
                }
            }
        }
        
        public String getMenuTitle() {
            return config.getString("menu_title", "GUI Menu");
        }
        
        public int getSize() {
            return config.getInt("size", 54);
        }
        
        public int getUpdateInterval() {
            return config.getInt("update_interval", 1);
        }
        
        public Map<String, MenuItem> getItems() {
            return items;
        }
        
        public MenuItem getItem(String itemId) {
            return items.get(itemId);
        }
        
        public static class MenuItem {
            private final String id;
            private final String material;
            private final List<Integer> slots;
            private final String displayName;
            private final List<String> lore;
            private final List<String> leftClickActions;
            private final List<String> rightClickActions;
            private final int customModelData;
            private final ItemState unlocked;
            private final ItemState locked;
            
            public MenuItem(String id, ConfigurationSection section) {
                this.id = id;
                this.material = section.getString("material", "STONE");
                this.slots = parseSlots(section);
                this.displayName = section.getString("display_name", id);
                this.lore = section.getStringList("lore");
                List<String> tempLeftActions = section.getStringList("left_click_actions");
                List<String> tempRightActions = section.getStringList("right_click_actions");
                if (tempLeftActions.isEmpty()) {
                    tempLeftActions = section.getStringList("left_click_commands");
                }
                if (tempRightActions.isEmpty()) {
                    tempRightActions = section.getStringList("right_click_commands");
                }
                this.leftClickActions = tempLeftActions;
                this.rightClickActions = tempRightActions;
                this.customModelData = section.getInt("custom_model_data", -1);
                
                this.unlocked = loadItemState(section.getConfigurationSection("unlocked"));
                this.locked = loadItemState(section.getConfigurationSection("locked"));
            }
            
            private ItemState loadItemState(ConfigurationSection section) {
                if (section == null) {
                    return null;
                }
                String displayName = section.getString("display_name", "");
                List<String> lore = section.getStringList("lore");
                return new ItemState(displayName, lore);
            }
            
            private List<Integer> parseSlots(ConfigurationSection section) {
                List<Integer> slots = new java.util.ArrayList<>();
                
                if (section.contains("slot")) {
                    slots.add(section.getInt("slot"));
                }
                
                if (section.contains("slots")) {
                    List<String> slotRanges = section.getStringList("slots");
                    for (String range : slotRanges) {
                        if (range.contains("-")) {
                            String[] parts = range.split("-");
                            if (parts.length == 2) {
                                try {
                                    int start = Integer.parseInt(parts[0]);
                                    int end = Integer.parseInt(parts[1]);
                                    for (int i = start; i <= end; i++) {
                                        slots.add(i);
                                    }
                                } catch (NumberFormatException e) {
                                }
                            }
                        } else {
                            try {
                                slots.add(Integer.parseInt(range));
                            } catch (NumberFormatException e) {
                            }
                        }
                    }
                }
                
                return slots;
            }
            
            public String getId() {
                return id;
            }
            
            public String getMaterial() {
                return material;
            }
            
            public List<Integer> getSlots() {
                return slots;
            }
            
            public String getDisplayName() {
                return displayName;
            }
            
            public List<String> getLore() {
                return lore;
            }
            
            public List<String> getLeftClickActions() {
                return leftClickActions;
            }
            
            public List<String> getRightClickActions() {
                return rightClickActions;
            }
            
            public int getCustomModelData() {
                return customModelData;
            }
            
            public boolean hasCustomModelData() {
                return customModelData != -1;
            }
            
            public ItemState getUnlocked() {
                return unlocked;
            }
            
            public ItemState getLocked() {
                return locked;
            }
            
            public static class ItemState {
                private final String displayName;
                private final List<String> lore;
                
                public ItemState(String displayName, List<String> lore) {
                    this.displayName = displayName;
                    this.lore = lore;
                }
                
                public String getDisplayName() {
                    return displayName;
                }
                
                public List<String> getLore() {
                    return lore;
                }
            }
        }
    }
}
