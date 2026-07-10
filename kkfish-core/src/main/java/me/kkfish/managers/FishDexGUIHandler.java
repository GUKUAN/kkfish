package me.kkfish.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.kkfish.gui.GUIMenuLoader;
import me.kkfish.integrations.CustomItemHook;
import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;
import me.kkfish.utils.XSeriesUtil;

/**
 * 鱼类图鉴 GUI 处理器：负责图鉴物品生成、分页及玩家图鉴页码状态管理。
 *
 * <p>通过 {@code plugin.getGUI()} 回调 {@link GUI#openGUI} 实现界面刷新。
 */
public class FishDexGUIHandler {
    private final kkfish plugin;
    private final Config config;
    private final DB db;
    private final MessageManager messageManager;

    // 存储玩家当前的鱼类图鉴页面
    private final Map<UUID, Integer> fishDexPages = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> fishItemCache = new ConcurrentHashMap<>();

    public FishDexGUIHandler(kkfish plugin, Config config, DB db, MessageManager messageManager) {
        this.plugin = plugin;
        this.config = config;
        this.db = db;
        this.messageManager = messageManager;
        buildFishItemCache();
    }

    public void buildFishItemCache() {
        fishItemCache.clear();
        FileConfiguration fishConfig = config.getFishConfig();
        if (!fishConfig.isConfigurationSection("fish")) return;

        for (String fishName : fishConfig.getConfigurationSection("fish").getKeys(false)) {
            try {
                String materialName = fishConfig.getString("fish." + fishName + ".material", "COD");
                ItemStack item;
                if (CustomItemHook.isCustomItemStr(materialName)) {
                    item = CustomItemHook.createItemStack(materialName, 1);
                } else {
                    Material material = XSeriesUtil.parseMaterial(materialName);
                    if (material == null) material = XSeriesUtil.getMaterial("COD");
                    item = new ItemStack(material);
                }
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setUnbreakable(true);
                    meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                    item.setItemMeta(meta);
                }
                fishItemCache.put(fishName, item);
            } catch (Exception e) {
                // 跳过构建失败的鱼
            }
        }
    }

    /**
     * 获取鱼类图鉴页面映射（供 GUIListener 使用）
     */
    public Map<UUID, Integer> getFishDexPages() {
        return fishDexPages;
    }

    /**
     * 处理鱼类图鉴物品
     */
    public void handleFishDexItems(Inventory gui, GUIMenuLoader.MenuConfig.MenuItem item, Player player, int page) {
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
                        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.gui_create_fish_item_failed", "§e创建鱼类展示物品失败: " + fishName + " - " + e.getMessage(), fishName, e.getMessage()));
                    }
                }
                fishIndex++;
            }
        }
    }

    /**
     * 从配置创建鱼类展示物品
     */
    public ItemStack createFishDisplayItemFromConfig(GUIMenuLoader.MenuConfig.MenuItem itemConfig, FileConfiguration fishConfig, String fishName, Player player) {
        // 获取玩家的钓鱼记录
        DB dbManager = plugin.getDB();
        Map<String, Object> fishStats = dbManager.getPlayerFishStats(player.getUniqueId().toString(), fishName);
        int caughtCount = ((Number) fishStats.get("caughtCount")).intValue();
        double maxSize = ((Number) fishStats.get("maxSize")).doubleValue();

        // 获取鱼的基本信息
        String fishDisplayName = fishConfig.getString("fish." + fishName + ".display-name", fishName);

        // 从缓存获取底座物品
        ItemStack baseItem = fishItemCache.get(fishName);
        ItemStack item = baseItem != null ? baseItem.clone() : new ItemStack(XSeriesUtil.getMaterial("COD"));
        ItemMeta meta = item.getItemMeta();

        // 根据是否钓到过设置不同材质
        if (caughtCount == 0) {
            item.setType(XSeriesUtil.getMaterial("BLACK_WOOL"));
        } else {
            String materialName = fishConfig.getString("fish." + fishName + ".material", "COD");
            if (CustomItemHook.isCustomItemStr(materialName)) {
                ItemStack iaItem = CustomItemHook.createItemStack(materialName, 1);
                if (iaItem != null) {
                    item.setType(iaItem.getType());
                    if (iaItem.hasItemMeta()) {
                        // IA物品可能有自定义model data等，保留到item上
                        ItemMeta iaMeta = iaItem.getItemMeta();
                        if (iaMeta.hasCustomModelData()) {
                            meta.setCustomModelData(iaMeta.getCustomModelData());
                        }
                    }
                }
            } else {
                Material material = XSeriesUtil.parseMaterial(materialName);
                if (material != null) item.setType(material);
            }
        }

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
                displayNameConfig = CustomItemHook.replaceFontImages(displayNameConfig);
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
                    replacedLine = CustomItemHook.replaceFontImages(replacedLine);
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
                displayNameConfig = CustomItemHook.replaceFontImages(displayNameConfig);
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
                    line = CustomItemHook.replaceFontImages(line);
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

    /**
     * 处理鱼类图鉴翻页
     */
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
                plugin.getGUI().openGUI(player, GUI.GUIType.FISH_DEX, currentPage + 1);
            }
        } else if (currentPage > 0) {
            fishDexPages.put(player.getUniqueId(), currentPage - 1);
            plugin.getGUI().openGUI(player, GUI.GUIType.FISH_DEX, currentPage - 1);
        }
    }

    /**
     * 处理玩家退出事件，清理鱼类图鉴 GUI 相关状态
     */
    public void handlePlayerQuit(Player player) {
        fishDexPages.remove(player.getUniqueId());
    }
}
