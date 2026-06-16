package me.kkfish.managers;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.kkfish.gui.GUIHolder;
import me.kkfish.gui.GUIMenuLoader;
import me.kkfish.gui.FishRecord;
import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;
import me.kkfish.utils.XSeriesUtil;

/**
 * GUI 共享工具：负责创建配置化 GUI、填充菜单项、解析材质等通用逻辑。
 *
 * <p>通过 setter 注入各业务 Handler，以便 {@link #fillMenuItems} 能分派到
 * 对应的 Handler 处理动态物品（鱼钩、图鉴、比赛、奖励）。
 */
public class GUICommons {
    private final kkfish plugin;
    private final Config config;
    private final DB db;
    private final MessageManager messageManager;
    private final GUIMenuLoader menuLoader;

    private HookMaterialGUIHandler hookMaterialHandler;
    private FishDexGUIHandler fishDexHandler;
    private CompetitionGUIHandler competitionHandler;

    public GUICommons(kkfish plugin, Config config, DB db, MessageManager messageManager, GUIMenuLoader menuLoader) {
        this.plugin = plugin;
        this.config = config;
        this.db = db;
        this.messageManager = messageManager;
        this.menuLoader = menuLoader;
    }

    public void setHookMaterialHandler(HookMaterialGUIHandler handler) {
        this.hookMaterialHandler = handler;
    }

    public void setFishDexHandler(FishDexGUIHandler handler) {
        this.fishDexHandler = handler;
    }

    public void setCompetitionHandler(CompetitionGUIHandler handler) {
        this.competitionHandler = handler;
    }

    public Inventory createConfiguredGUI(Player player, String menuName, int page) {
        if (!menuLoader.hasMenuConfig(menuName)) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.gui_menu_not_found", "§e菜单配置不存在: " + menuName, menuName));
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

        GUI.GUIType guiType;
        try {
            guiType = GUI.GUIType.valueOf(menuName.toUpperCase().replace("_", "_"));
        } catch (IllegalArgumentException e) {
            kkfish.log("§e" + "无效的GUI类型: " + menuName);
            return null;
        }

        Inventory gui = Bukkit.createInventory(
            new GUIHolder(guiType, page),
            size,
            ChatColor.translateAlternateColorCodes('&', title)
        );

        fillMenuItems(gui, menuConfig, player, page);

        return gui;
    }

    public void fillMenuItems(Inventory gui, GUIMenuLoader.MenuConfig menuConfig, Player player, int page) {
        for (GUIMenuLoader.MenuConfig.MenuItem item : menuConfig.getItems().values()) {
            if (item.getId().equals("fish_dex_items")) {
                fishDexHandler.handleFishDexItems(gui, item, player, page);
            } else if (item.getId().equals("hook_material_items")) {
                hookMaterialHandler.handleHookMaterialItems(gui, item, player, page);
            } else if (item.getId().equals("competition_items")) {
                competitionHandler.handleCompetitionItems(gui, item, player, page);
            } else if (item.getId().equals("reward_items")) {
                competitionHandler.handleRewardItems(gui, item, player, page);
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

    public ItemStack createMenuItemFromConfig(GUIMenuLoader.MenuConfig.MenuItem itemConfig, Player player, int page) {
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
            java.util.List<String> lore = new java.util.ArrayList<>();
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
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.gui_create_menu_item_failed", "§e创建菜单物品失败: " + e.getMessage(), e.getMessage()));
            return null;
        }
    }

    public String replacePlaceholders(String text, Player player, int page, GUIMenuLoader.MenuConfig.MenuItem itemConfig) {
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

    /**
     * 解析材质字符串为 Material 枚举，支持 head- / basehead- 前缀及大小写容错。
     * <p>静态方法，供各 Handler 共享调用。
     */
    public static Material parseMaterial(String materialStr) {
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

    public String getGUITitle(GUI.GUIType type, int page) {
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
}
