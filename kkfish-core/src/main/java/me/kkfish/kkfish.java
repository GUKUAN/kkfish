package me.kkfish;

import java.util.UUID;

import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.*;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;

import net.milkbowl.vault.economy.Economy;
import me.kkfish.managers.Fish;
import me.kkfish.managers.DB;
import me.kkfish.managers.GUI;
import me.kkfish.managers.Cmd;
import me.kkfish.managers.Config;
import me.kkfish.managers.Compete;
import me.kkfish.listeners.Fishing;
import me.kkfish.listeners.ItemCraft;
import me.kkfish.handlers.AuraSkills;
import me.kkfish.misc.SoundManager;
import me.kkfish.misc.MessageManager;
import me.kkfish.misc.DependencyManager;
import me.kkfish.misc.minigame.MinigameManager;
import me.kkfish.bootstrap.RootService;
import me.kkfish.economy.EconomyService;
import me.kkfish.events.EventBus;
import me.kkfish.integrations.SeasonsService;
import me.kkfish.platform.VersionService;
import me.kkfish.player.PlayerContextStore;
import me.kkfish.scheduler.SchedulerProvider;
import java.util.concurrent.ConcurrentHashMap;

public class kkfish extends JavaPlugin {

    private static kkfish instance;
    private Fish fish;
    private Config config;
    private SoundManager soundManager;
    private Cmd cmd;
    private DB db;
    private MessageManager messageManager;
    private Economy economy;
    private PlayerPointsAPI playerPointsAPI;
    private EconomyService economyService;
    private GUI gui;
    private AuraSkills auraSkills;
    private Fishing fishingListener;
    private Compete compete;
    private Object realisticSeasons = null;
    private SeasonsService seasonsService;
    private ItemCraft itemCraft;
    private MinigameManager minigameManager;
    private PlayerContextStore playerContextStore;

    private int majorVersion = 0;
    private int minorVersion = 0;
    private me.kkfish.utils.EntityBatchProcessor entityBatchProcessor;
    private SchedulerProvider scheduler;
    private VersionService versionService;
    private RootService rootService;

    private final ConcurrentHashMap<UUID, Boolean> playerFishingMode = new ConcurrentHashMap<>();

    @Override
    public void onLoad() {
        instance = this;

        // MessageManager 必须在 Config 之前初始化，因为 Config 构造时会调用 kkfish.log()
        messageManager = MessageManager.getInstance(this);
        messageManager.completeAllLanguageFiles();

        config = new Config(this);

        config.checkAndAddMissingConfigs();
        
        DependencyManager dependencyManager = new DependencyManager(this);
        if (!dependencyManager.checkAndDownloadDependencies()) {
            kkfish.log("§c" + messageManager.getMessageWithoutPrefix("dependency_download_failed_all", "Dependency download failed, plugin may not work properly!"));
        }
        dependencyManager.loadDependencies();
        
        config.initializeItemValue();
        
        versionService = new VersionService();
        versionService.logDetection(this);
        majorVersion = versionService.getMajorVersion();
        minorVersion = versionService.getMinorVersion();
    }

    @Override
    public void onEnable() {
        rootService = new RootService(this);
        rootService.startup();
        
        // 从 RootService 同步字段，保持向后兼容的 getter
        scheduler = rootService.getScheduler();
        economyService = rootService.getEconomyService();
        economy = economyService != null ? economyService.getEconomy() : null;
        playerPointsAPI = economyService != null ? economyService.getPlayerPointsAPI() : null;
        seasonsService = rootService.getSeasonsService();
        db = rootService.getDb();
        gui = rootService.getGui();
        minigameManager = rootService.getMinigameManager();
        fish = rootService.getFish();
        cmd = rootService.getCmd();
        auraSkills = rootService.getAuraSkills();
        compete = rootService.getCompete();
        fishingListener = rootService.getFishingListener();
        itemCraft = rootService.getItemCraft();
        entityBatchProcessor = rootService.getEntityBatchProcessor();
    }

    @Override
    public void onDisable() {
        if (rootService != null) {
            rootService.close();
        }
    }

    public static kkfish getInstance() {
        return instance;
    }

    public SchedulerProvider getScheduler() {
        return scheduler;
    }

    public static void log(String message) {
        MessageManager mm = getInstance().messageManager;
        if (mm == null) {
            // MessageManager 尚未初始化，直接输出无前缀消息
            org.bukkit.Bukkit.getConsoleSender().sendMessage(
                ChatColor.translateAlternateColorCodes('&', message));
            return;
        }
        String prefix = mm.getPrefix();
        String fullMsg = ChatColor.translateAlternateColorCodes('&', prefix.replace('§', '&') + message);
        org.bukkit.Bukkit.getConsoleSender().sendMessage(fullMsg);
    }
    
    public Compete getCompete() {
        return compete;
    }

    public Fish getFish() {
        return fish;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public Cmd getCmd() {
        return cmd;
    }

    public DB getDB() {
        return db;
    }

    public AuraSkills getAuraSkills() {
        return auraSkills;
    }
    
    public MessageManager getMessageManager() {
        return messageManager;
    }
    
    @Override
    public org.bukkit.configuration.file.FileConfiguration getConfig() {
        return super.getConfig();
    }
    
    public Config getCustomConfig() {
        return config;
    }
    
    public Economy getEconomy() {
        return economy;
    }
    
    /**
     * @return 统一经济服务
     */
    public EconomyService getEconomyService() {
        return economyService;
    }
    
    /**
     * @return RealisticSeasons 季节服务
     */
    public SeasonsService getSeasonsService() {
        return seasonsService;
    }
    
    public boolean isRealisticSeasonsEnabled() {
        return seasonsService != null && seasonsService.isEnabled();
    }
    
    public void initMetrics() {
        if (rootService != null) {
            rootService.reloadMetrics();
        }
    }
    
    public String getCurrentSeason() {
        return seasonsService != null ? seasonsService.getCurrentSeason() : null;
    }
    
    public PlayerPointsAPI getPlayerPointsAPI() {
        return playerPointsAPI;
    }
    
    public GUI getGUI() {
        return gui;
    }
    
    public Fishing getFishingListener() {
        return fishingListener;
    }
    
    public int getMajorVersion() {
        return majorVersion;
    }
    
    public int getMinorVersion() {
        return minorVersion;
    }
    
    /**
     * @return 统一版本检测服务
     */
    public VersionService getVersionService() {
        return versionService;
    }
    
    public me.kkfish.utils.EntityBatchProcessor getEntityBatchProcessor() {
        return entityBatchProcessor;
    }
    
    public boolean isVersion1_21OrHigher() {
        return versionService != null ? versionService.is1_21OrHigher() : ((majorVersion > 1) || (majorVersion == 1 && minorVersion >= 21));
    }

    public boolean isPlayerInVanillaMode(UUID playerId) {
        return playerFishingMode.getOrDefault(playerId, false);
    }

    public void setPlayerFishingMode(UUID playerId, boolean vanillaMode) {
        playerFishingMode.put(playerId, vanillaMode);
    }

    public void clearPlayerFishingMode(UUID playerId) {
        playerFishingMode.remove(playerId);
    }
    
    public MinigameManager getMinigameManager() {
        return minigameManager;
    }

    /**
     * 供 RootService 在启动期间设置音效管理器。
     * 管理器在构造时通过 plugin.getSoundManager() 访问。
     */
    public void setSoundManagerInternal(SoundManager soundManager) {
        this.soundManager = soundManager;
    }

    /**
     * 供 RootService 在启动期间设置调度器。
     * Manager 构造时通过 SchedulerUtil 访问，需要提前注入。
     */
    public void setSchedulerInternal(SchedulerProvider scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * 供 RootService 在启动期间设置经济实例。
     */
    public void setEconomyInternal(Economy economy) {
        this.economy = economy;
    }

    /**
     * 供 RootService 在启动期间设置 PlayerPoints API。
     */
    public void setPlayerPointsInternal(PlayerPointsAPI playerPointsAPI) {
        this.playerPointsAPI = playerPointsAPI;
    }

    /**
     * 供 RootService 在启动期间设置数据库管理器。
     * Manager 构造时通过 plugin.getDB() 访问。
     */
    public void setDBInternal(DB db) {
        this.db = db;
    }

    /**
     * 供 RootService 在启动期间设置玩家上下文存储。
     * Manager 构造时通过 plugin.getPlayerContextStore() 访问。
     */
    public void setPlayerContextStoreInternal(PlayerContextStore playerContextStore) {
        this.playerContextStore = playerContextStore;
    }

    /**
     * 供 RootService 在启动期间设置 GUI。
     * Manager 构造时通过 plugin.getGUI() 访问。
     */
    public void setGUIInternal(GUI gui) {
        this.gui = gui;
    }

    /**
     * 供 RootService 在启动期间设置小游戏管理器。
     * Manager 构造时通过 plugin.getMinigameManager() 访问。
     */
    public void setMinigameManagerInternal(MinigameManager minigameManager) {
        this.minigameManager = minigameManager;
    }

    /**
     * 供 RootService 在启动期间设置钓鱼核心管理器。
     * Manager 构造时通过 plugin.getFish() 访问。
     */
    public void setFishInternal(Fish fish) {
        this.fish = fish;
    }

    /**
     * 供 RootService 在启动期间设置命令管理器。
     */
    public void setCmdInternal(Cmd cmd) {
        this.cmd = cmd;
    }

    /**
     * 供 RootService 在启动期间设置 AuraSkills 处理器。
     */
    public void setAuraSkillsInternal(AuraSkills auraSkills) {
        this.auraSkills = auraSkills;
    }

    /**
     * 供 RootService 在启动期间设置竞赛管理器。
     */
    public void setCompeteInternal(Compete compete) {
        this.compete = compete;
    }

    /**
     * @return 组合根服务
     */
    public RootService getRootService() {
        return rootService;
    }

    /**
     * @return 玩家上下文存储
     */
    public PlayerContextStore getPlayerContextStore() {
        return rootService != null ? rootService.getPlayerContextStore() : null;
    }

    /**
     * @return 根事件总线
     */
    public EventBus getEventBus() {
        return rootService != null ? rootService.getEventBus() : null;
    }
}
