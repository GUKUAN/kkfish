package me.kkfish.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
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
 * 鱼竿商店 GUI 处理器：负责鱼竿购买界面的物品生成、分页、搜索、排序及购买逻辑。
 *
 * <p>持有玩家维度的鱼竿商店 GUI 状态（页码、排序方式、搜索关键词、槽位映射）。
 * 通过 {@code plugin.getGUI()} 回调 {@link GUI#openGUI} 实现界面刷新。
 */
public class RodShopGUIHandler {
    private final kkfish plugin;
    private final Config config;
    private final DB db;
    private final MessageManager messageManager;
    private final GUIMenuLoader menuLoader;

    private final Map<UUID, Integer> rodShopPages = new ConcurrentHashMap<>();
    private final Map<UUID, String> rodSortMethods = new ConcurrentHashMap<>();
    private final Map<UUID, String> rodSearchQueries = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Map<Integer, String>>> slotToRodMap = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> rodItemCache = new ConcurrentHashMap<>();

    public RodShopGUIHandler(kkfish plugin, Config config, DB db, MessageManager messageManager, GUIMenuLoader menuLoader) {
        this.plugin = plugin;
        this.config = config;
        this.db = db;
        this.messageManager = messageManager;
        this.menuLoader = menuLoader;
        buildRodItemCache();
    }

    /**
     * 构建鱼竿物品缓存，预加载所有鱼竿的底座展示物品
     */
    public void buildRodItemCache() {
        rodItemCache.clear();
        GUIMenuLoader.MenuConfig menuConfig = menuLoader.getMenuConfig("rod_shop");
        if (menuConfig == null) return;

        FileConfiguration rodConfig = config.getRodConfig();
        if (rodConfig == null || !rodConfig.contains("rods")) return;

        ConfigurationSection rodsSection = rodConfig.getConfigurationSection("rods");
        if (rodsSection == null) return;

        for (String rodName : rodsSection.getKeys(false)) {
            try {
                ItemStack baseItem = buildBaseRodItem(menuConfig, rodName);
                if (baseItem != null) {
                    rodItemCache.put(rodName, baseItem);
                }
            } catch (Exception e) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.rod_cache_build_failed", "鱼竿缓存构建失败: " + rodName, rodName));
            }
        }
    }

    /**
     * 生成不含玩家状态的底座物品
     */
    private ItemStack buildBaseRodItem(GUIMenuLoader.MenuConfig menuConfig, String rodName) {
        FileConfiguration rodConfig = config.getRodConfig();
        String displayName = rodConfig.getString("rods." + rodName + ".display-name", rodName);
        String materialStr = rodConfig.getString("rods." + rodName + ".material", "FISHING_ROD");
        int customModelData = rodConfig.getInt("rods." + rodName + ".custom-model-data", -1);

        // 优先尝试 IA 自定义物品
        ItemStack item;
        if (CustomItemHook.isCustomItemStr(materialStr)) {
            item = CustomItemHook.createItemStack(materialStr, 1);
        } else {
            Material material = XSeriesUtil.parseMaterial(materialStr);
            if (material == null) {
                material = XSeriesUtil.getMaterial("FISHING_ROD");
            }
            item = new ItemStack(material);
        }
        ItemMeta meta = item.getItemMeta();

        // 从菜单配置获取显示名称模板
        String displayNameConfig = menuConfig.getItems().values().iterator().next().getDisplayName();
        if (displayNameConfig.startsWith("i18n:")) {
            displayNameConfig = messageManager.getMessageWithoutPrefix(displayNameConfig.substring(5), displayNameConfig);
        }
        displayNameConfig = displayNameConfig.replace("%rod_name%", displayName);
        displayNameConfig = ChatColor.translateAlternateColorCodes('&', displayNameConfig);
        displayNameConfig = CustomItemHook.replaceFontImages(displayNameConfig);
        meta.setDisplayName(displayNameConfig);

        // 设置自定义模型数据
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== 排序与搜索 ====================

    public void setRodSortBy(Player player, String sortBy) {
        rodSortMethods.put(player.getUniqueId(), sortBy);
    }

    public String getRodSortBy(Player player) {
        return rodSortMethods.getOrDefault(player.getUniqueId(), messageManager.getMessageWithoutPrefix("sort_by_name", "按名称排序"));
    }

    public String getRodSearchQuery(Player player) {
        return rodSearchQueries.getOrDefault(player.getUniqueId(), "");
    }

    public void setRodSearchQuery(Player player, String query) {
        if (query == null || query.isEmpty()) {
            rodSearchQueries.remove(player.getUniqueId());
        } else {
            rodSearchQueries.put(player.getUniqueId(), query);
        }
    }

    /**
     * 检查鱼竿名称是否匹配搜索查询
     */
    public boolean matchesSearchQuery(String rodName, String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }

        FileConfiguration rodConfig = config.getRodConfig();
        String displayName = rodConfig.getString("rods." + rodName + ".display-name", rodName);

        // 移除颜色代码并转换为小写进行比较
        query = ChatColor.stripColor(query).toLowerCase();

        if (displayName != null) {
            displayName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', displayName)).toLowerCase();
            if (displayName.contains(query)) {
                return true;
            }
        }

        // 也按鱼竿ID搜索
        if (rodName.toLowerCase().contains(query)) {
            return true;
        }

        return false;
    }

    // ==================== 物品生成 ====================

    /**
     * 处理鱼竿商店物品填充
     */
    public void handleRodShopItems(Inventory gui, GUIMenuLoader.MenuConfig.MenuItem item, Player player, int page) {
        FileConfiguration rodConfig = config.getRodConfig();
        if (rodConfig == null || !rodConfig.contains("rods")) return;

        // 获取所有鱼竿名称
        ConfigurationSection rodsSection = rodConfig.getConfigurationSection("rods");
        if (rodsSection == null) return;

        List<String> rodNames = new ArrayList<>(rodsSection.getKeys(false));

        // 每页显示的鱼竿数量
        int itemsPerPage = 28; // 4行7列
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, rodNames.size());

        // 初始化或获取玩家的槽位到鱼竿ID映射
        UUID playerId = player.getUniqueId();
        String guiKey = "rod_shop_page_" + page;
        Map<String, Map<Integer, String>> playerMap = slotToRodMap.computeIfAbsent(playerId, k -> new HashMap<>());
        Map<Integer, String> slotMap = playerMap.computeIfAbsent(guiKey, k -> new HashMap<>());
        slotMap.clear(); // 清空当前页面的映射

        // 遍历鱼竿物品槽位
        List<Integer> slots = item.getSlots();
        int rodIndex = startIndex;

        for (int slot : slots) {
            if (rodIndex < endIndex && slot >= 0 && slot < gui.getSize()) {
                String rodName = rodNames.get(rodIndex);
                if (rodName != null && !rodName.isEmpty()) {
                    try {
                        // 从配置创建鱼竿物品
                        ItemStack rodItem = createRodDisplayItemFromConfig(item, player, rodName);
                        gui.setItem(slot, rodItem);
                        // 记录槽位到鱼竿ID的映射
                        slotMap.put(slot, rodName);
                    } catch (Exception e) {
                        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.gui_create_rod_item_failed", "§e创建鱼竿展示物品失败: " + rodName + " - " + e.getMessage(), rodName, e.getMessage()));
                    }
                }
                rodIndex++;
            }
        }
    }

    /**
     * 从配置创建鱼竿展示物品（含玩家状态信息）
     */
    public ItemStack createRodDisplayItemFromConfig(GUIMenuLoader.MenuConfig.MenuItem itemConfig, Player player, String rodName) {
        FileConfiguration rodConfig = config.getRodConfig();

        // 读取鱼竿配置数据
        String displayName = rodConfig.getString("rods." + rodName + ".display-name", rodName);
        double vaultPrice = rodConfig.getDouble("rods." + rodName + ".price.vault", 0.0);
        int pointsPrice = rodConfig.getInt("rods." + rodName + ".price.points", 0);
        int durability = rodConfig.getInt("rods." + rodName + ".durability", 0);
        double difficulty = rodConfig.getDouble("rods." + rodName + ".difficulty", 1.0);

        // 从缓存获取底座物品
        ItemStack baseItem = rodItemCache.get(rodName);
        ItemStack item;
        if (baseItem != null) {
            item = baseItem.clone();
        } else {
            item = buildBaseRodItem(menuLoader.getMenuConfig("rod_shop"), rodName);
            if (item == null) return null;
        }
        ItemMeta meta = item.getItemMeta();
        String baseDisplayName = meta.getDisplayName();

        // 设置lore（含玩家状态信息）
        List<String> lore = new ArrayList<>();
        for (String line : itemConfig.getLore()) {
            if (line.startsWith("i18n:")) {
                line = messageManager.getMessageWithoutPrefix(player, line.substring(5), line);
            }
            String replacedLine = line;
            replacedLine = replacedLine.replace("%rod_name%", displayName);
            replacedLine = replacedLine.replace("%rod_difficulty%", String.format("%.1f", difficulty));
            replacedLine = replacedLine.replace("%rod_durability%", durability > 0 ? String.valueOf(durability) : messageManager.getMessageWithoutPrefix("rod_durability_infinite", "无限"));

            // 价格占位符
            if (vaultPrice > 0) {
                replacedLine = replacedLine.replace("%rod_vault_price%", String.format("%.2f", vaultPrice));
            } else {
                replacedLine = replacedLine.replace("%rod_vault_price%", messageManager.getMessageWithoutPrefix("rod_price_free", "免费"));
            }
            if (pointsPrice > 0) {
                replacedLine = replacedLine.replace("%rod_points_price%", String.valueOf(pointsPrice));
            } else {
                replacedLine = replacedLine.replace("%rod_points_price%", messageManager.getMessageWithoutPrefix("rod_price_free", "免费"));
            }

            // 购买状态
            if (replacedLine.contains("%rod_status%")) {
                boolean hasPrice = vaultPrice > 0 || pointsPrice > 0;
                if (!hasPrice) {
                    replacedLine = replacedLine.replace("%rod_status%", messageManager.getMessageWithoutPrefix("rod_status_free", "免费鱼竿"));
                } else {
                    replacedLine = replacedLine.replace("%rod_status%", messageManager.getMessageWithoutPrefix("rod_status_purchasable", "可购买"));
                }
            }

            // 购买操作提示
            if (replacedLine.contains("%rod_action%")) {
                boolean hasPrice = vaultPrice > 0 || pointsPrice > 0;
                if (!hasPrice) {
                    replacedLine = replacedLine.replace("%rod_action%", messageManager.getMessageWithoutPrefix("rod_action_free", "免费获取"));
                } else {
                    replacedLine = replacedLine.replace("%rod_action%", messageManager.getMessageWithoutPrefix("rod_action_buy", "左键购买(金币) 右键购买(点券)"));
                }
            }

            replacedLine = ChatColor.translateAlternateColorCodes('&', replacedLine);
            replacedLine = CustomItemHook.replaceFontImages(replacedLine);
            lore.add(replacedLine);
        }

        meta.setDisplayName(baseDisplayName);
        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    // ==================== 分页 ====================

    /**
     * 处理鱼竿商店界面翻页
     */
    public void handleRodShopPage(Player player, boolean next) {
        int currentPage = rodShopPages.getOrDefault(player.getUniqueId(), 0);

        FileConfiguration rodConfig = config.getRodConfig();
        if (rodConfig == null || !rodConfig.contains("rods")) return;

        ConfigurationSection rodsSection = rodConfig.getConfigurationSection("rods");
        if (rodsSection == null) return;

        // 获取所有鱼竿并应用搜索过滤
        List<String> visibleRodNames = new ArrayList<>();
        for (String rodName : rodsSection.getKeys(false)) {
            // 应用搜索过滤
            String searchQuery = getRodSearchQuery(player);
            if (searchQuery.isEmpty() || matchesSearchQuery(rodName, searchQuery)) {
                visibleRodNames.add(rodName);
            }
        }

        // 计算总页数（每页显示28个）
        int itemsPerPage = 28;
        int totalPages = (int) Math.ceil((double) visibleRodNames.size() / itemsPerPage);
        totalPages = Math.max(totalPages, 1); // 至少1页

        // 直接打开新页面，不需要关闭当前GUI
        if (next) {
            // 检查是否还有下一页
            if (currentPage + 1 < totalPages) {
                rodShopPages.put(player.getUniqueId(), currentPage + 1);
                plugin.getGUI().openGUI(player, GUI.GUIType.ROD_SHOP, currentPage + 1);
            }
        } else if (currentPage > 0) {
            rodShopPages.put(player.getUniqueId(), currentPage - 1);
            plugin.getGUI().openGUI(player, GUI.GUIType.ROD_SHOP, currentPage - 1);
        }
    }

    // ==================== 购买逻辑 ====================

    /**
     * 处理鱼竿购买（左键金币，右键点券）
     */
    public void handleRodPurchase(Player player, String rodName) {
        FileConfiguration rodConfig = config.getRodConfig();
        if (rodConfig == null || !rodConfig.contains("rods." + rodName)) {
            player.sendMessage(messageManager.getMessage("rod_not_found", "§c鱼竿不存在！"));
            return;
        }

        double vaultPrice = rodConfig.getDouble("rods." + rodName + ".price.vault", 0.0);
        int pointsPrice = rodConfig.getInt("rods." + rodName + ".price.points", 0);
        boolean hasVaultPrice = vaultPrice > 0;
        boolean hasPointsPrice = pointsPrice > 0;

        // 没有配置价格的鱼竿视为免费
        if (!hasVaultPrice && !hasPointsPrice) {
            giveRodToPlayer(player, rodName);
            return;
        }

        player.sendMessage(messageManager.getMessage("rod_purchase_hint", "§e请左键点击使用金币购买，右键点击使用点券购买！"));
    }

    /**
     * 处理鱼竿购买（区分点击类型）
     */
    public void handleRodPurchase(Player player, String rodName, boolean isLeftClick) {
        FileConfiguration rodConfig = config.getRodConfig();
        if (rodConfig == null || !rodConfig.contains("rods." + rodName)) {
            player.sendMessage(messageManager.getMessage("rod_not_found", "§c鱼竿不存在！"));
            return;
        }

        double vaultPrice = rodConfig.getDouble("rods." + rodName + ".price.vault", 0.0);
        int pointsPrice = rodConfig.getInt("rods." + rodName + ".price.points", 0);
        boolean hasVaultPrice = vaultPrice > 0;
        boolean hasPointsPrice = pointsPrice > 0;

        // 没有配置价格的鱼竿视为免费
        if (!hasVaultPrice && !hasPointsPrice) {
            giveRodToPlayer(player, rodName);
            return;
        }

        boolean purchaseSuccess = false;

        if (isLeftClick && hasVaultPrice) {
            // 金币购买
            net.milkbowl.vault.economy.Economy economy = plugin.getEconomy();
            if (economy == null) {
                player.sendMessage(messageManager.getMessage("rod_purchase_vault_unavailable", "§c经济系统未启用，无法使用金币购买！"));
                return;
            }

            double balance = economy.getBalance(player);
            if (balance < vaultPrice) {
                player.sendMessage(messageManager.getMessage("rod_purchase_insufficient_vault", "§c金币不足！还需要 %.2f 金币", vaultPrice - balance));
                return;
            }

            net.milkbowl.vault.economy.EconomyResponse response = economy.withdrawPlayer(player, vaultPrice);
            if (!response.transactionSuccess()) {
                player.sendMessage(messageManager.getMessage("rod_purchase_deduct_failed", "§c扣款失败，请稍后再试。"));
                return;
            }

            purchaseSuccess = true;
        } else if (!isLeftClick && hasPointsPrice) {
            // 点券购买
            org.black_ixx.playerpoints.PlayerPointsAPI pointsAPI = plugin.getPlayerPointsAPI();
            if (pointsAPI == null) {
                player.sendMessage(messageManager.getMessage("rod_purchase_points_unavailable", "§c点券系统未启用，无法使用点券购买！"));
                return;
            }

            int currentPoints = pointsAPI.look(player.getUniqueId());
            if (currentPoints < pointsPrice) {
                player.sendMessage(messageManager.getMessage("rod_purchase_insufficient_points", "§c点券不足！还需要 %d 点券", pointsPrice - currentPoints));
                return;
            }

            if (!pointsAPI.take(player.getUniqueId(), pointsPrice)) {
                player.sendMessage(messageManager.getMessage("rod_purchase_deduct_failed", "§c扣款失败，请稍后再试。"));
                return;
            }

            purchaseSuccess = true;
        } else {
            // 点击方式与可用价格不匹配
            if (isLeftClick && !hasVaultPrice) {
                player.sendMessage(messageManager.getMessage("rod_purchase_no_vault_price", "§c此鱼竿不支持金币购买，请右键使用点券购买！"));
            } else {
                player.sendMessage(messageManager.getMessage("rod_purchase_no_points_price", "§c此鱼竿不支持点券购买，请左键使用金币购买！"));
            }
            return;
        }

        if (purchaseSuccess) {
            giveRodToPlayer(player, rodName);
        }
    }

    /**
     * 给予玩家鱼竿物品
     */
    private void giveRodToPlayer(Player player, String rodName) {
        try {
            // 使用 GiveCommandHandler 创建鱼竿物品
            ItemStack rodItem = plugin.getCmd().getGiveHandler().createRodItem(rodName);
            if (rodItem == null) {
                player.sendMessage(messageManager.getMessage("rod_create_failed", "§c创建鱼竿物品失败！"));
                return;
            }

            // 添加到玩家背包
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(rodItem);
            if (!leftover.isEmpty()) {
                // 背包满了，掉落物品
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }

            String displayName = config.getRodConfig().getString("rods." + rodName + ".display-name", rodName);
            displayName = displayName.replace('§', '&');
            player.sendMessage(messageManager.getMessage("rod_purchase_success", "§a成功获得鱼竿: %s！", displayName));

            // 刷新GUI
            plugin.getGUI().openGUI(player, GUI.GUIType.ROD_SHOP);
        } catch (Exception e) {
            kkfish.log("§e" + messageManager.getMessageWithoutPrefix("log.rod_give_failed", "给予鱼竿失败: ") + e.getMessage());
            player.sendMessage(messageManager.getMessage("rod_give_failed", "§c给予鱼竿失败，请联系管理员！"));
        }
    }

    // ==================== 槽位与页面查询 ====================

    /**
     * 根据槽位获取鱼竿ID
     */
    public String getRodIdFromSlot(Player player, int slot, int page) {
        UUID playerId = player.getUniqueId();
        String guiKey = "rod_shop_page_" + page;

        Map<String, Map<Integer, String>> playerMap = slotToRodMap.get(playerId);
        if (playerMap != null) {
            Map<Integer, String> slotMap = playerMap.get(guiKey);
            if (slotMap != null) {
                return slotMap.get(slot);
            }
        }

        return null;
    }

    /**
     * 获取玩家当前的鱼竿商店页面
     */
    public int getCurrentRodShopPage(Player player) {
        return rodShopPages.getOrDefault(player.getUniqueId(), 0);
    }

    // ==================== 清理 ====================

    /**
     * 处理玩家退出事件，清理鱼竿商店 GUI 相关状态
     */
    public void handlePlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        rodShopPages.remove(playerId);
        rodSortMethods.remove(playerId);
        rodSearchQueries.remove(playerId);
        slotToRodMap.remove(playerId);
    }
}
