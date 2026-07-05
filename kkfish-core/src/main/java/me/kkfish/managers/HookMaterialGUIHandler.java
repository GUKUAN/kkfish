package me.kkfish.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.kkfish.gui.GUIMenuLoader;
import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;
import me.kkfish.utils.XSeriesUtil;

/**
 * 鱼钩材质 GUI 处理器：负责鱼钩材质选择界面的物品生成、分页、搜索、排序及材质设置。
 *
 * <p>持有玩家维度的鱼钩 GUI 状态（页码、排序方式、搜索关键词、槽位映射）。
 * 通过 {@code plugin.getGUI()} 回调 {@link GUI#openGUI} 实现界面刷新。
 */
public class HookMaterialGUIHandler {
    private final kkfish plugin;
    private final Config config;
    private final DB db;
    private final MessageManager messageManager;
    private final GUIMenuLoader menuLoader;

    private final Map<UUID, Integer> hookMaterialPages = new ConcurrentHashMap<>();
    private final Map<UUID, String> hookSortMethods = new ConcurrentHashMap<>();
    private final Map<UUID, String> hookSearchQueries = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Map<Integer, String>>> slotToHookMap = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> hookItemCache = new ConcurrentHashMap<>();

    public HookMaterialGUIHandler(kkfish plugin, Config config, DB db, MessageManager messageManager, GUIMenuLoader menuLoader) {
        this.plugin = plugin;
        this.config = config;
        this.db = db;
        this.messageManager = messageManager;
        this.menuLoader = menuLoader;
        buildHookItemCache();
    }

    public void buildHookItemCache() {
        hookItemCache.clear();
        GUIMenuLoader.MenuConfig menuConfig = menuLoader.getMenuConfig("hook_material");
        if (menuConfig == null) return;

        Map<String, Object> hookConfigs = config.getHookConfigs();
        for (String hookName : hookConfigs.keySet()) {
            try {
                ItemStack baseItem = buildBaseHookItem(menuConfig, hookName);
                if (baseItem != null) {
                    hookItemCache.put(hookName, baseItem);
                }
            } catch (Exception e) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.hook_cache_build_failed", "鱼钩缓存构建失败: " + hookName, hookName));
            }
        }
    }

    private ItemStack buildBaseHookItem(GUIMenuLoader.MenuConfig menuConfig, String hookName) {
        // 生成不含玩家状态的底座物品
        String displayName = config.getHookDisplayName(hookName);
        String rarity = config.getHookRarity(hookName);
        double price = config.getHookVaultPrice(hookName);

        Material material = config.getHookMaterial(hookName);
        if (material == null) {
            material = XSeriesUtil.getMaterial("STICK");
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String displayNameConfig = menuConfig.getItems().values().iterator().next().getDisplayName();
        if (displayNameConfig.startsWith("i18n:")) {
            displayNameConfig = messageManager.getMessageWithoutPrefix(displayNameConfig.substring(5), displayNameConfig);
        }
        displayNameConfig = displayNameConfig.replace("%hook_name%", displayName);
        displayNameConfig = ChatColor.translateAlternateColorCodes('&', displayNameConfig);
        meta.setDisplayName(displayNameConfig);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 排序与搜索 ====================

    public void setHookSortBy(Player player, String sortBy) {
        hookSortMethods.put(player.getUniqueId(), sortBy);
    }

    public String getHookSortBy(Player player) {
        return hookSortMethods.getOrDefault(player.getUniqueId(), messageManager.getMessageWithoutPrefix("sort_by_name", "按名称排序"));
    }

    public String getHookSearchQuery(Player player) {
        return hookSearchQueries.getOrDefault(player.getUniqueId(), "");
    }

    public void setHookSearchQuery(Player player, String query) {
        if (query == null || query.isEmpty()) {
            hookSearchQueries.remove(player.getUniqueId());
        } else {
            hookSearchQueries.put(player.getUniqueId(), query);
        }
    }

    /**
     * 检查鱼钩名称是否匹配搜索查询
     */
    public boolean matchesSearchQuery(String hookName, String query) {
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

    // ==================== 物品生成 ====================

    /**
     * 处理鱼钩材质物品
     */
    public void handleHookMaterialItems(Inventory gui, GUIMenuLoader.MenuConfig.MenuItem item, Player player, int page) {
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
                        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.gui_create_hook_item_failed", "§e创建鱼钩展示物品失败: " + hookName + " - " + e.getMessage(), hookName, e.getMessage()));
                    }
                }
                hookIndex++;
            }
        }
    }

    /**
     * 从配置创建鱼钩展示物品
     */
    public ItemStack createHookDisplayItemFromConfig(GUIMenuLoader.MenuConfig.MenuItem itemConfig, Player player, String hookName) {
        // 检查玩家是否已拥有此鱼钩
        boolean isOwned = config.hasHookMaterialPermission(player, hookName);
        boolean isEquipped = hookName.equals(plugin.getDB().getPlayerHookMaterial(player.getUniqueId().toString()));

        // 从缓存获取底座物品
        ItemStack baseItem = hookItemCache.get(hookName);
        ItemStack item;
        if (baseItem != null) {
            item = baseItem.clone();
        } else {
            item = buildBaseHookItem(menuLoader.getMenuConfig("hook_material"), hookName);
            if (item == null) return null;
        }
        ItemMeta meta = item.getItemMeta();
        String baseDisplayName = meta.getDisplayName();

        // 获取鱼钩配置数据用于lore
        String displayName = config.getHookDisplayName(hookName);
        String description = config.getHookDescription(hookName);
        String rarity = config.getHookRarity(hookName);
        double price = config.getHookVaultPrice(hookName);

        // 设置lore（含玩家状态信息）
        List<String> lore = new ArrayList<>();
        for (String line : itemConfig.getLore()) {
            if (line.startsWith("i18n:")) {
                line = messageManager.getMessageWithoutPrefix(player, line.substring(5), line);
            }
            String replacedLine = line;
            replacedLine = replacedLine.replace("%hook_name%", displayName);
            replacedLine = replacedLine.replace("%hook_level%", getHookLevelByRarity(rarity));
            replacedLine = replacedLine.replace("%hook_price%", String.format("%.2f", price));
            replacedLine = replacedLine.replace("%hook_durability%", messageManager.getMessageWithoutPrefix("hook_durability_infinite", "无限"));
            replacedLine = replacedLine.replace("%hook_effect%", description);

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

        meta.setDisplayName(baseDisplayName);
        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * 根据稀有度获取等级
     */
    public String getHookLevelByRarity(String rarity) {
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

    // ==================== 分页 ====================

    /**
     * 处理鱼钩材质选择界面翻页
     */
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
                plugin.getGUI().openGUI(player, GUI.GUIType.HOOK_MATERIAL, currentPage + 1);
            }
        } else if (currentPage > 0) {
            hookMaterialPages.put(player.getUniqueId(), currentPage - 1);
            plugin.getGUI().openGUI(player, GUI.GUIType.HOOK_MATERIAL, currentPage - 1);
        }
    }

    // ==================== 材质设置 ====================

    /**
     * 设置玩家鱼钩材质
     */
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
        Material material = me.kkfish.utils.MaterialResolver.getMaterialFromType(cleanMaterialType, config);
        plugin.getFish().setPlayerHookMaterial(player, material);

        // 获取正确的显示名称
        String displayName = config.getHookDisplayName(cleanMaterialType);
        // 将颜色代码从 § 转换回 &，以避免messageManager.getMessage()方法重复转换
        displayName = displayName.replace('§', '&');
        // 发送设置成功消息，使用正确的显示名称
        player.sendMessage(messageManager.getMessage("hook_material_set", "§a成功设置鱼钩材质为: %s", displayName));

        // 重新打开材质选择界面，刷新显示
        plugin.getGUI().openGUI(player, GUI.GUIType.HOOK_MATERIAL);
    }

    /**
     * 重载版本的设置鱼钩材质方法（兼容旧代码）
     */
    public void setPlayerHookMaterial(Player player, Material material, boolean refresh) {
        // 从材质类型获取材质名称
        String materialType = "wood";
        if (material == XSeriesUtil.getMaterial("STICK") || material == XSeriesUtil.getMaterial("OAK_LOG")) {
            materialType = "wood";
        } else if (material == XSeriesUtil.getMaterial("COBBLESTONE") || material == XSeriesUtil.getMaterial("STONE")) {
            materialType = "stone";
        } else if (material == XSeriesUtil.getMaterial("IRON_INGOT")) {
            materialType = "iron";
        } else if (material == XSeriesUtil.getMaterial("GOLD_INGOT")) {
            materialType = "gold";
        } else if (material == XSeriesUtil.getMaterial("DIAMOND")) {
            materialType = "diamond";
        }

        setPlayerHookMaterial(player, materialType);
    }

    // ==================== 槽位与页面查询 ====================

    /**
     * 根据槽位获取鱼钩ID
     */
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

    /**
     * 获取玩家当前的鱼钩材质页面
     */
    public int getCurrentHookMaterialPage(Player player) {
        return hookMaterialPages.getOrDefault(player.getUniqueId(), 0);
    }

    // ==================== 清理 ====================

    /**
     * 处理玩家退出事件，清理鱼钩材质 GUI 相关状态
     */
    public void handlePlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        hookMaterialPages.remove(playerId);
        hookSortMethods.remove(playerId);
        hookSearchQueries.remove(playerId);
        slotToHookMap.remove(playerId);
    }
}
