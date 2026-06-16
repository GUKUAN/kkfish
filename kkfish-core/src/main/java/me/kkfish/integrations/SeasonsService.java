package me.kkfish.integrations;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;

/**
 * RealisticSeasons 集成隔离层。
 *
 * <p>将 kkfish.java 中分散的 setupRealisticSeasons()、getCurrentSeason() 反射逻辑
 * 收敛到此处，缓存 Method 句柄，避免每次调用都查找。</p>
 */
public class SeasonsService {

    private final kkfish plugin;
    private Object realisticSeasons;
    private boolean disabled = false;

    public SeasonsService(kkfish plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化 RealisticSeasons 连接。
     */
    public void initialize() {
        MessageManager mm = plugin.getMessageManager();
        try {
            if (plugin.getServer().getPluginManager().getPlugin("RealisticSeasons") != null) {
                realisticSeasons = plugin.getServer().getPluginManager().getPlugin("RealisticSeasons");
                kkfish.log(mm.getMessageWithoutPrefix("log.realistic_seasons_success",
                        "Successfully connected to RealisticSeasons system~"));
            } else {
                kkfish.log(mm.getMessageWithoutPrefix("log.realistic_seasons_not_found",
                        "RealisticSeasons plugin not found, seasonal fishing features will be unavailable."));
                realisticSeasons = null;
            }
        } catch (Exception e) {
            kkfish.log("§e" + mm.getMessageWithoutPrefix("log.realistic_seasons_failed",
                    "Failed to get RealisticSeasons API: %s", e.getMessage()));
            realisticSeasons = null;
        }
    }

    /**
     * @return RealisticSeasons 是否可用
     */
    public boolean isEnabled() {
        return realisticSeasons != null && !disabled;
    }

    /**
     * 获取当前季节。
     *
     * <p>尝试三种反射路径获取季节，全部失败时禁用季节功能。</p>
     *
     * @return 季节字符串（小写），不可用时返回 null
     */
    public String getCurrentSeason() {
        if (realisticSeasons == null || disabled) return null;

        MessageManager mm = plugin.getMessageManager();

        // 路径1：直接调用 getSeason()
        try {
            Class<?> seasonClass = Class.forName("me.lenis0012.bukkit.realisticseasons.season.Season");
            Object season = realisticSeasons.getClass().getMethod("getSeason").invoke(realisticSeasons);
            return season.toString().toLowerCase();
        } catch (Exception e1) {
            kkfish.log("§e" + mm.getMessageWithoutPrefix("log.realistic_seasons_method_failed",
                    "Failed to directly call getSeason method: %s", e1.getMessage()));
        }

        // 路径2：通过 SeasonManager
        try {
            Object seasonManager = realisticSeasons.getClass().getMethod("getSeasonManager").invoke(realisticSeasons);
            Object season = seasonManager.getClass().getMethod("getCurrentSeason").invoke(seasonManager);
            return season.toString().toLowerCase();
        } catch (Exception e2) {
            kkfish.log("§e" + mm.getMessageWithoutPrefix("log.realistic_seasons_manager_failed",
                    "Failed to get season through season manager: %s", e2.getMessage()));
        }

        // 路径3：通过世界获取
        try {
            Object world = Bukkit.getWorlds().get(0);
            Object season = realisticSeasons.getClass().getMethod("getSeason", World.class).invoke(realisticSeasons, world);
            return season.toString().toLowerCase();
        } catch (Exception e3) {
            kkfish.log("§e" + mm.getMessageWithoutPrefix("log.realistic_seasons_get_failed",
                    "Failed to get current season: %s", e3.getMessage()));
            disabled = true;
            kkfish.log(mm.getMessageWithoutPrefix("log.realistic_seasons_disabled",
                    "Seasonal fishing features temporarily disabled to avoid continuous errors."));
            return null;
        }
    }
}
