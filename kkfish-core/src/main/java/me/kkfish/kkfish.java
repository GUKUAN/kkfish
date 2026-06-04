package me.kkfish;

import java.lang.reflect.*;
import java.util.UUID;
import java.util.regex.*;

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
import me.kkfish.misc.UpdateChecker;
import me.kkfish.misc.MessageManager;
import me.kkfish.misc.Metrics;
import me.kkfish.misc.DependencyManager;
import me.kkfish.misc.minigame.MinigameManager;
import me.kkfish.scheduler.SchedulerProvider;
import me.kkfish.scheduler.SchedulerProviderFactory;
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
    private GUI gui;
    private AuraSkills auraSkills;
    private Fishing fishingListener;
    private Compete compete;
    private Object realisticSeasons = null;
    private ItemCraft itemCraft;
    private MinigameManager minigameManager;

    private int majorVersion = 0;
    private int minorVersion = 0;
    private me.kkfish.utils.EntityBatchProcessor entityBatchProcessor;
    private Metrics metrics;
    private SchedulerProvider scheduler;

    private final ConcurrentHashMap<UUID, Boolean> playerFishingMode = new ConcurrentHashMap<>();

    @Override
    public void onLoad() {
        instance = this;
        
        config = new Config(this);
        
        messageManager = MessageManager.getInstance(this);
        messageManager.completeAllLanguageFiles();
        
        config.checkAndAddMissingConfigs();
        
        DependencyManager dependencyManager = new DependencyManager(this);
        if (!dependencyManager.checkAndDownloadDependencies()) {
            kkfish.log("§c" + messageManager.getMessageWithoutPrefix("dependency_download_failed_all", "Dependency download failed, plugin may not work properly!"));
        }
        dependencyManager.loadDependencies();
        
        config.initializeItemValue();
        
        detectServerVersion();
    }

    @Override
    public void onEnable() {
        scheduler = SchedulerProviderFactory.create(this);
        soundManager = new SoundManager(this);
        
        if (!setupEconomy()) {
            kkfish.log(messageManager.getMessageWithoutPrefix("log.no_economy", "Vault or economy plugin not found! Economy features will be unavailable."));
        } else {
            kkfish.log(messageManager.getMessageWithoutPrefix("log.economy_success", "Successfully connected to economy system~"));
        }
        
        setupPlayerPoints();
        
        setupRealisticSeasons();
        db = new DB(this);
        gui = new GUI(this);
        minigameManager = new MinigameManager(this);
        fish = new Fish(this);
        cmd = new Cmd(this);
        try {
            auraSkills = new AuraSkills(this);
        } catch (Exception e) {
            kkfish.log(messageManager.getMessageWithoutPrefix("log.aura_skills_failed", "AuraSkills handler initialization failed (AuraSkills plugin may be missing): %s", e.getMessage()));
            auraSkills = null;
        }
        
        compete = new Compete(this);
        
        fishingListener = new Fishing(this);
        Bukkit.getPluginManager().registerEvents(fishingListener, this);
        
        itemCraft = new ItemCraft(this);
        
        entityBatchProcessor = new me.kkfish.utils.EntityBatchProcessor();
        scheduler.runTaskTimer(entityBatchProcessor::flush, 20L, 20L);
        
        initMetrics();
        
        if (config.isUpdateCheckEnabled()) {
            new UpdateChecker(this).checkForUpdates();
        }
        
        scheduler.runTaskLater(() -> {
            kkfish.log(messageManager.getMessageWithoutPrefix("log.plugin_loaded", "KKFISH fishing system has been loaded!"));
        }, 20L);
    }

    @Override
    public void onDisable() {
        if (fish != null) {
            fish.cleanup();
        }
        
        if (compete != null) {
            compete.cleanup();
        }
        
        if (db != null) {
            db.clearAllCache();
            db.close();
        }
        
        if (entityBatchProcessor != null) {
            entityBatchProcessor.flush();
            entityBatchProcessor.clear();
        }

        kkfish.log(messageManager.getMessageWithoutPrefix("log.plugin_disabled", "KKFISH fishing system has been disabled."));
    }

    public static kkfish getInstance() {
        return instance;
    }

    public SchedulerProvider getScheduler() {
        return scheduler;
    }

    public static void log(String message) {
        MessageManager mm = getInstance().getMessageManager();
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
    
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        
        economy = rsp.getProvider();
        return economy != null;
    }
    
    public Economy getEconomy() {
        return economy;
    }
    
    private void setupPlayerPoints() {
        Plugin playerPointsPlugin = getServer().getPluginManager().getPlugin("PlayerPoints");
        if (playerPointsPlugin != null) {
            try {
                Method getAPIMethod = playerPointsPlugin.getClass().getMethod("getAPI");
                if (Modifier.isStatic(getAPIMethod.getModifiers())) {
                    playerPointsAPI = (PlayerPointsAPI) getAPIMethod.invoke(null);
                } else {
                    playerPointsAPI = (PlayerPointsAPI) getAPIMethod.invoke(playerPointsPlugin);
                }
                kkfish.log(messageManager.getMessageWithoutPrefix("log.player_points_success", "Successfully connected to PlayerPoints system~"));
            } catch (Exception e) {
                kkfish.log("§e" + messageManager.getMessageWithoutPrefix("log.player_points_failed", "Failed to get PlayerPoints API: %s", e.getMessage()));
                e.printStackTrace();
                playerPointsAPI = null;
            }
        } else {
            kkfish.log(messageManager.getMessageWithoutPrefix("log.player_points_not_found", "PlayerPoints plugin not found, point purchase features will be unavailable."));
            playerPointsAPI = null;
        }
    }
    
    private void setupRealisticSeasons() {
        try {
            if (getServer().getPluginManager().getPlugin("RealisticSeasons") != null) {
                realisticSeasons = getServer().getPluginManager().getPlugin("RealisticSeasons");
                kkfish.log(messageManager.getMessageWithoutPrefix("log.realistic_seasons_success", "Successfully connected to RealisticSeasons system~"));
            } else {
                kkfish.log(messageManager.getMessageWithoutPrefix("log.realistic_seasons_not_found", "RealisticSeasons plugin not found, seasonal fishing features will be unavailable."));
                realisticSeasons = null;
            }
        } catch (Exception e) {
            kkfish.log("§e" + messageManager.getMessageWithoutPrefix("log.realistic_seasons_failed", "Failed to get RealisticSeasons API: %s", e.getMessage()));
            realisticSeasons = null;
        }
    }
    
    public boolean isRealisticSeasonsEnabled() {
        return realisticSeasons != null;
    }
    
    public void initMetrics() {
        boolean bstatsEnabled = getConfig().getBoolean("bstats.enabled", true);
        
        if (metrics != null) {
            try {
                metrics.shutdown();
            } catch (Exception e) {
                // 忽略错误
            }
            metrics = null;
        }
        
        if (bstatsEnabled) {
            try {
                metrics = new me.kkfish.misc.Metrics(this, 28982);
                kkfish.log(messageManager.getMessageWithoutPrefix("log.bstats_initialized", "Successfully initialized bStats statistics module"));
            } catch (Exception e) {
                String errorMessage = e.getMessage();
                if (errorMessage == null) errorMessage = e.getClass().getName();
                kkfish.log("§c" + messageManager.getMessageWithoutPrefix("log.bstats_init_failed", "Failed to initialize bStats statistics module: %s", errorMessage));
            }
        } else {
            kkfish.log(messageManager.getMessageWithoutPrefix("log.bstats_disabled", "bStats statistics module has been disabled"));
        }
    }
    
    public String getCurrentSeason() {
        if (realisticSeasons == null) return null;
        
        try {
                try {
                    Class<?> seasonClass = Class.forName("me.lenis0012.bukkit.realisticseasons.season.Season");
                    Object season = realisticSeasons.getClass().getMethod("getSeason").invoke(realisticSeasons);
                    return season.toString().toLowerCase();
                } catch (Exception e1) {
                    kkfish.log("§e" + messageManager.getMessageWithoutPrefix("log.realistic_seasons_method_failed", "Failed to directly call getSeason method: %s", e1.getMessage()));
                    
                    try {
                        Object seasonManager = realisticSeasons.getClass().getMethod("getSeasonManager").invoke(realisticSeasons);
                        Object season = seasonManager.getClass().getMethod("getCurrentSeason").invoke(seasonManager);
                        return season.toString().toLowerCase();
                    } catch (Exception e2) {
                        kkfish.log("§e" + messageManager.getMessageWithoutPrefix("log.realistic_seasons_manager_failed", "Failed to get season through season manager: %s", e2.getMessage()));
                        
                        try {
                            Object world = Bukkit.getWorlds().get(0);
                            Object season = realisticSeasons.getClass().getMethod("getSeason", World.class).invoke(realisticSeasons, world);
                            return season.toString().toLowerCase();
                        } catch (Exception e3) {
                            kkfish.log("§e" + messageManager.getMessageWithoutPrefix("log.realistic_seasons_get_failed", "Failed to get current season: %s", e3.getMessage()));
                            realisticSeasons = null;
                            kkfish.log(messageManager.getMessageWithoutPrefix("log.realistic_seasons_disabled", "Seasonal fishing features temporarily disabled to avoid continuous errors."));
                            return null;
                        }
                    }
                }
        } catch (Exception e) {
            kkfish.log("§e" + messageManager.getMessageWithoutPrefix("log.realistic_seasons_unknown_error", "Unknown error occurred while getting current season: %s", e.getMessage()));
            return null;
        }
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
    
    private void detectServerVersion() {
        String version = Bukkit.getVersion();
        Pattern pattern = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
        Matcher matcher = pattern.matcher(version);
        
        if (matcher.find()) {
            try {
                majorVersion = Integer.parseInt(matcher.group(1));
                minorVersion = Integer.parseInt(matcher.group(2));
                if (messageManager != null) {
                    kkfish.log(messageManager.getMessageWithoutPrefix("log.version_detected", "Server version detected: %s.%s", majorVersion, minorVersion));
                } else {
                    kkfish.log("Detected server version: " + majorVersion + "." + minorVersion);
                }
            } catch (NumberFormatException e) {
                if (messageManager != null) {
                    kkfish.log("§e" + messageManager.getMessageWithoutPrefix("log.version_detection_failed", "Version detection failed, using basic GUI restriction scheme by default"));
                } else {
                    kkfish.log("§e" + "Version detection failed, using basic GUI restriction scheme by default");
                }
            }
        } else {
            if (messageManager != null) {
                kkfish.log("§e" + messageManager.getMessageWithoutPrefix("log.version_unknown", "Unable to identify server version, using basic GUI restriction scheme by default"));
            } else {
                kkfish.log("§e" + "Unable to identify server version, using basic GUI restriction scheme by default");
            }
        }
    }
    
    public int getMajorVersion() {
        return majorVersion;
    }
    
    public int getMinorVersion() {
        return minorVersion;
    }
    
    public me.kkfish.utils.EntityBatchProcessor getEntityBatchProcessor() {
        return entityBatchProcessor;
    }
    
    public boolean isVersion1_21OrHigher() {
        return (majorVersion > 1) || (majorVersion == 1 && minorVersion >= 21);
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
}
