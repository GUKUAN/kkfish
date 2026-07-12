package me.kkfish.managers;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import me.kkfish.gui.GUIMenuLoader;
import me.kkfish.handlers.GUIAction;
import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;

/**
 * GUI 协调器。
 *
 * <p>原 2644 行的 God Class 已拆分为 4 个专职组件：
 * <ul>
 *   <li>{@link GUICommons} — 配置化 GUI 创建、菜单项填充、材质解析等通用工具</li>
 *   <li>{@link HookMaterialGUIHandler} — 鱼钩材质选择界面的物品生成、分页、搜索、排序及材质设置</li>
 *   <li>{@link FishDexGUIHandler} — 鱼类图鉴物品生成与分页</li>
 *   <li>{@link CompetitionGUIHandler} — 比赛分类与奖励预览物品生成</li>
 * </ul>
 *
 * <p>本类仅保留 {@link GUIType} 枚举、公共 API 委派、跨 Handler 清理协调及共享访问器。
 */
public class GUI {
    private final kkfish plugin;
    private final MessageManager messageManager;
    private final Config config;
    private final DB db;
    private final me.kkfish.listeners.GUIListener guiListener;

    // GUI菜单加载器
    private final GUIMenuLoader menuLoader;

    // GUI动作处理器
    private final GUIAction actionHandler;

    // 拆分后的组件
    private final GUICommons commons;
    private final HookMaterialGUIHandler hookMaterialHandler;
    private final RodShopGUIHandler rodShopHandler;
    private final FishDexGUIHandler fishDexHandler;
    private final CompetitionGUIHandler competitionHandler;

    // GUI类型枚举，便于识别和管理
    public enum GUIType {
        MAIN_MENU,
        HOOK_MATERIAL,
        ROD_SHOP,
        FISH_DEX,
        FISH_RECORD,
        HELP_GUI,
        COMPETITION_CATEGORY,
        REWARD_PREVIEW,
        SELL_GUI
    }

    public GUI(kkfish plugin) {
        this.plugin = plugin;
        this.messageManager = MessageManager.getInstance(plugin);
        this.config = plugin.getCustomConfig();
        this.db = plugin.getDB();

        // 创建GUI菜单加载器
        this.menuLoader = new GUIMenuLoader(plugin);

        // 创建GUI动作处理器
        this.actionHandler = new GUIAction(plugin);

        // 初始化拆分后的组件
        this.commons = new GUICommons(plugin, config, db, messageManager, menuLoader);
        this.hookMaterialHandler = new HookMaterialGUIHandler(plugin, config, db, messageManager, menuLoader);
        this.rodShopHandler = new RodShopGUIHandler(plugin, config, db, messageManager, menuLoader);
        this.fishDexHandler = new FishDexGUIHandler(plugin, config, db, messageManager);
        this.competitionHandler = new CompetitionGUIHandler(plugin, config, messageManager);

        // 注入 Handler 引用，使 GUICommons.fillMenuItems 能分派到各 Handler
        this.commons.setHookMaterialHandler(this.hookMaterialHandler);
        this.commons.setRodShopHandler(this.rodShopHandler);
        this.commons.setFishDexHandler(this.fishDexHandler);
        this.commons.setCompetitionHandler(this.competitionHandler);

        // 注册GUI事件监听器
        this.guiListener = new me.kkfish.listeners.GUIListener(this);
    }

    public void reloadMenuConfigs() {
        menuLoader.loadAllMenus();
        guiListener.reloadDisplayNameMap();
    }

    public DB getDB() {
        return plugin.getDB();
    }

    // ==================== GUI 打开 ====================

    public void openGUI(Player player, GUIType type) {
        openGUI(player, type, 0);
    }

    public void openGUI(Player player, GUIType type, int page) {
        if (type == GUIType.SELL_GUI && !canUseSellEconomy()) {
            player.sendMessage(messageManager.getMessage(player, "economy_not_enabled", "§c经济系统未启用，无法使用卖出功能！"));
            return;
        }

        String menuName = type.name().toLowerCase();
        menuName = menuName.replace("_", "_");

        if (type == GUIType.MAIN_MENU) {
            menuName = "main_menu";
        } else if (type == GUIType.HOOK_MATERIAL) {
            menuName = "hook_material";
        } else if (type == GUIType.ROD_SHOP) {
            menuName = "rod_shop";
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

        Inventory gui = commons.createConfiguredGUI(player, menuName, page);

        if (gui != null) {
            player.openInventory(gui);
        }
    }

    // ==================== 快捷打开方法 ====================

    public void openMainMenu(Player player) {
        openGUI(player, GUIType.MAIN_MENU);
    }

    public void openHookMaterial(Player player) {
        openGUI(player, GUIType.HOOK_MATERIAL);
    }

    public void openFishDex(Player player) {
        // 确保使用正确的GUI类型和页面参数打开鱼类图鉴
        openGUI(player, GUIType.FISH_DEX, 0);
    }

    public void openFishRecord(Player player) {
        openGUI(player, GUIType.FISH_RECORD);
    }

    public void openHelp(Player player) {
        // 直接打开帮助指南GUI，无需特殊权限
        openGUI(player, GUIType.HELP_GUI);
    }

    public void openCompetitionCategory(Player player) {
        openGUI(player, GUIType.COMPETITION_CATEGORY);
    }

    public void openRewardPreview(Player player, String competitionId) {
        openGUI(player, GUIType.REWARD_PREVIEW, competitionHandler.getCompetitionIndex(competitionId));
    }

    public void openSellGUI(Player player) {
        // 检查价格系统是否启用
        if (!canUseSellEconomy()) {
            player.sendMessage(messageManager.getMessage("economy_not_enabled", "§c经济系统未启用，无法使用卖出功能！"));
            return;
        }
        openGUI(player, GUIType.SELL_GUI);
    }

    private boolean canUseSellEconomy() {
        return plugin.getCustomConfig().isPriceEnabled()
                && plugin.getEconomyService() != null
                && plugin.getEconomyService().isEconomyEnabled();
    }

    // ==================== 鱼钩材质委派 ====================

    public void setHookSortBy(Player player, String sortBy) {
        hookMaterialHandler.setHookSortBy(player, sortBy);
    }

    public String getHookSortBy(Player player) {
        return hookMaterialHandler.getHookSortBy(player);
    }

    public String getHookSearchQuery(Player player) {
        return hookMaterialHandler.getHookSearchQuery(player);
    }

    public void setHookSearchQuery(Player player, String query) {
        hookMaterialHandler.setHookSearchQuery(player, query);
    }

    public void handleHookMaterialPage(Player player, boolean next) {
        hookMaterialHandler.handleHookMaterialPage(player, next);
    }

    public void setPlayerHookMaterial(Player player, String materialType) {
        hookMaterialHandler.setPlayerHookMaterial(player, materialType);
    }

    public void setPlayerHookMaterial(Player player, Material material, boolean refresh) {
        hookMaterialHandler.setPlayerHookMaterial(player, material, refresh);
    }

    public String getHookIdFromSlot(Player player, int slot, int page) {
        return hookMaterialHandler.getHookIdFromSlot(player, slot, page);
    }

    public int getCurrentHookMaterialPage(Player player) {
        return hookMaterialHandler.getCurrentHookMaterialPage(player);
    }

    // ==================== 鱼竿商店委派 ====================

    public void openRodShop(Player player) {
        openGUI(player, GUIType.ROD_SHOP);
    }

    public void handleRodShopPage(Player player, boolean next) {
        rodShopHandler.handleRodShopPage(player, next);
    }

    public String getRodIdFromSlot(Player player, int slot, int page) {
        return rodShopHandler.getRodIdFromSlot(player, slot, page);
    }

    public int getCurrentRodShopPage(Player player) {
        return rodShopHandler.getCurrentRodShopPage(player);
    }

    public RodShopGUIHandler getRodShopHandler() {
        return rodShopHandler;
    }

    // ==================== 鱼类图鉴委派 ====================

    public void handleFishDexPage(Player player, boolean next) {
        fishDexHandler.handleFishDexPage(player, next);
    }

    // ==================== 玩家退出清理 ====================

    public void handlePlayerQuit(Player player) {
        fishDexHandler.handlePlayerQuit(player);
        hookMaterialHandler.handlePlayerQuit(player);
        rodShopHandler.handlePlayerQuit(player);
    }

    // ==================== 访问器 ====================

    public kkfish getPlugin() {
        return plugin;
    }

    public GUIMenuLoader getMenuLoader() {
        return menuLoader;
    }

    public GUIAction getActionHandler() {
        return actionHandler;
    }

    public me.kkfish.listeners.GUIListener getGUI() {
        return guiListener;
    }

    public Map<UUID, Integer> getFishDexPages() {
        return fishDexHandler.getFishDexPages();
    }
}
