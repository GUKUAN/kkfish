package me.kkfish.bootstrap;

import org.bukkit.Bukkit;

import me.kkfish.kkfish;
import me.kkfish.economy.EconomyService;
import me.kkfish.handlers.AuraSkills;
import me.kkfish.integrations.SeasonsService;
import me.kkfish.listeners.Fishing;
import me.kkfish.listeners.ItemCraft;
import me.kkfish.managers.Cmd;
import me.kkfish.managers.Compete;
import me.kkfish.managers.Config;
import me.kkfish.managers.DB;
import me.kkfish.managers.Fish;
import me.kkfish.managers.GUI;
import me.kkfish.misc.DependencyManager;
import me.kkfish.misc.MessageManager;
import me.kkfish.misc.Metrics;
import me.kkfish.misc.SoundManager;
import me.kkfish.misc.UpdateChecker;
import me.kkfish.misc.minigame.MinigameManager;
import me.kkfish.events.EventBus;
import me.kkfish.events.EventSubscriberRegistry;
import me.kkfish.player.PlayerContextStore;
import me.kkfish.platform.VersionService;
import me.kkfish.scheduler.SchedulerProvider;
import me.kkfish.scheduler.SchedulerProviderFactory;
import me.kkfish.utils.EntityBatchProcessor;

/**
 * 组合根与生命周期拥有者。
 *
 * <p>集中所有模块的构造、依赖装配和关闭顺序。{@code kkfish} 插件类仅负责创建
 * RootService 并委托生命周期。</p>
 *
 * <h3>启动顺序（显式）</h3>
 * <pre>
 *   config → scheduler → version → economy → seasons
 *     → db → gui → minigame → fish → cmd → auraskills → compete
 *     → listeners → entityBatchProcessor → metrics → updateCheck
 * </pre>
 *
 * <h3>关闭顺序（显式，逆序）</h3>
 * <pre>
 *   fish → compete → db → entityBatchProcessor
 * </pre>
 */
public class RootService implements AutoCloseable {

    private final kkfish plugin;

    // 基础设施
    private Config config;
    private SchedulerProvider scheduler;
    private VersionService versionService;
    private MessageManager messageManager;
    private EconomyService economyService;
    private SeasonsService seasonsService;
    private EntityBatchProcessor entityBatchProcessor;
    private Metrics metrics;
    private EventBus eventBus;
    private EventSubscriberRegistry eventSubscriberRegistry;

    // 域管理器
    private DB db;
    private GUI gui;
    private MinigameManager minigameManager;
    private Fish fish;
    private Cmd cmd;
    private AuraSkills auraSkills;
    private Compete compete;

    // 玩家上下文
    private PlayerContextStore playerContextStore;

    // 监听器
    private Fishing fishingListener;
    private ItemCraft itemCraft;

    private boolean started = false;

    public RootService(kkfish plugin) {
        this.plugin = plugin;
    }

    /**
     * 启动所有服务，按显式顺序构造模块。
     */
    public void startup() {
        if (started) return;

        MessageManager mm = plugin.getMessageManager();

        // 1. 调度器（立即同步到 plugin，因为后续 Manager 构造时会通过 SchedulerUtil 访问）
        scheduler = SchedulerProviderFactory.create(plugin);
        plugin.setSchedulerInternal(scheduler);

        // 1.5 事件总线（基础设施，供后续服务订阅）
        eventBus = new EventBus();

        // 2. 音效管理
        SoundManager soundManager = new SoundManager(plugin);
        plugin.setSoundManagerInternal(soundManager);

        // 3. 经济服务
        economyService = new EconomyService(plugin);
        economyService.initialize();
        plugin.setEconomyInternal(economyService.getEconomy());
        plugin.setPlayerPointsInternal(economyService.getPlayerPointsAPI());

        // 4. 季节服务
        seasonsService = new SeasonsService(plugin);
        seasonsService.initialize();

        // 5. 数据库
        db = new DB(plugin);
        plugin.setDBInternal(db);

        // 5.5 玩家上下文存储（依赖 DB + Scheduler + EventBus）
        playerContextStore = new PlayerContextStore(plugin, scheduler, db, eventBus);
        plugin.setPlayerContextStoreInternal(playerContextStore);

        // 6. GUI
        gui = new GUI(plugin);
        plugin.setGUIInternal(gui);

        // 7. 小游戏
        minigameManager = new MinigameManager(plugin);
        plugin.setMinigameManagerInternal(minigameManager);

        // 8. 钓鱼核心
        fish = new Fish(plugin);
        plugin.setFishInternal(fish);

        // 9. 命令
        cmd = new Cmd(plugin);
        plugin.setCmdInternal(cmd);

        // 10. AuraSkills
        try {
            auraSkills = new AuraSkills(plugin);
        } catch (Exception e) {
            kkfish.log(mm.getMessageWithoutPrefix("log.aura_skills_failed",
                    "AuraSkills handler initialization failed (AuraSkills plugin may be missing): %s", e.getMessage()));
            auraSkills = null;
        }
        plugin.setAuraSkillsInternal(auraSkills);

        // 11. 竞赛
        compete = new Compete(plugin);
        plugin.setCompeteInternal(compete);

        // 11.5 注册事件订阅者
        eventSubscriberRegistry = new EventSubscriberRegistry(plugin, eventBus);
        eventSubscriberRegistry.registerAll();

        // 12. 监听器
        fishingListener = new Fishing(plugin);
        Bukkit.getPluginManager().registerEvents(fishingListener, plugin);
        itemCraft = new ItemCraft(plugin);

        // 13. 实体批处理器
        entityBatchProcessor = new EntityBatchProcessor();
        scheduler.runTaskTimer(entityBatchProcessor::flush, 20L, 20L);

        // 14. bStats
        initMetrics();

        // 15. 更新检查
        if (plugin.getCustomConfig().isUpdateCheckEnabled()) {
            new UpdateChecker(plugin).checkForUpdates();
        }

        // 16. 启动完成通知
        scheduler.runTaskLater(() -> {
            kkfish.log(mm.getMessageWithoutPrefix("log.plugin_loaded", "KKFISH fishing system has been loaded!"));
        }, 20L);

        // 17. 为在线玩家初始化上下文（reload 场景）
        playerContextStore.initializeOnlinePlayers();

        started = true;
    }

    /**
     * 关闭所有服务，按显式逆序释放资源。
     */
    @Override
    public void close() {
        if (!started) return;

        MessageManager mm = plugin.getMessageManager();

        // 1. 停止钓鱼任务
        if (fish != null) {
            fish.cleanup();
        }

        // 1.5 关闭玩家上下文存储（保存所有在线玩家数据）
        if (playerContextStore != null) {
            playerContextStore.close();
        }

        // 2. 停止竞赛
        if (compete != null) {
            compete.cleanup();
        }

        // 3. 关闭数据库
        if (db != null) {
            db.clearAllCache();
            db.close();
        }

        // 4. 刷新实体批处理器
        if (entityBatchProcessor != null) {
            entityBatchProcessor.flush();
            entityBatchProcessor.clear();
        }

        // 5. 清理事件总线
        if (eventSubscriberRegistry != null) {
            eventSubscriberRegistry.close();
        }
        if (eventBus != null) {
            eventBus.clear();
        }

        kkfish.log(mm.getMessageWithoutPrefix("log.plugin_disabled", "KKFISH fishing system has been disabled."));

        started = false;
    }

    private void initMetrics() {
        boolean bstatsEnabled = plugin.getConfig().getBoolean("bstats.enabled", true);

        if (metrics != null) {
            try {
                metrics.shutdown();
            } catch (Exception ignored) {
            }
            metrics = null;
        }

        MessageManager mm = plugin.getMessageManager();
        if (bstatsEnabled) {
            try {
                metrics = new Metrics(plugin, 28982);
                kkfish.log(mm.getMessageWithoutPrefix("log.bstats_initialized", "Successfully initialized bStats statistics module"));
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                if (errorMessage == null) errorMessage = e.getClass().getName();
                kkfish.log("§c" + mm.getMessageWithoutPrefix("log.bstats_init_failed", "Failed to initialize bStats statistics module: %s", errorMessage));
            }
        } else {
            kkfish.log(mm.getMessageWithoutPrefix("log.bstats_disabled", "bStats statistics module has been disabled"));
        }
    }

    /**
     * 重新初始化 bStats 统计模块（供 reload 命令调用）。
     */
    public void reloadMetrics() {
        initMetrics();
    }

    // ===== Getters =====

    public SchedulerProvider getScheduler() {
        return scheduler;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public SeasonsService getSeasonsService() {
        return seasonsService;
    }

    public EntityBatchProcessor getEntityBatchProcessor() {
        return entityBatchProcessor;
    }

    public DB getDb() {
        return db;
    }

    public PlayerContextStore getPlayerContextStore() {
        return playerContextStore;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public GUI getGui() {
        return gui;
    }

    public MinigameManager getMinigameManager() {
        return minigameManager;
    }

    public Fish getFish() {
        return fish;
    }

    public Cmd getCmd() {
        return cmd;
    }

    public AuraSkills getAuraSkills() {
        return auraSkills;
    }

    public Compete getCompete() {
        return compete;
    }

    public Fishing getFishingListener() {
        return fishingListener;
    }

    public ItemCraft getItemCraft() {
        return itemCraft;
    }
}
