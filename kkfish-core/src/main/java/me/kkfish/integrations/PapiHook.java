package me.kkfish.integrations;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.OfflinePlayer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.kkfish.handlers.AuraSkills;
import me.kkfish.kkfish;
import me.kkfish.managers.DB;
import me.kkfish.player.PersistentPlayerData;
import me.kkfish.player.PersistentPlayerData.FishRecordData;
import me.kkfish.player.PlayerContext;
import me.kkfish.player.PlayerContextStore;

/**
 * PlaceholderAPI 扩展 — 提供 kkfish 玩家钓鱼数据占位符。
 *
 * <p>数据优先级：在线玩家读内存 PlayerContext（实时），
 * 离线或上下文不可用时查询数据库兜底。</p>
 *
 * <pre>
 *   %kkfish_fish_caught_types%                 钓过种类数
 *   %kkfish_fish_max_size%                     钓过最大长度（cm）
 *   %kkfish_fishing_level%                     钓鱼等级（AuraSkills）
 *   %kkfish_fishing_attempts%                  钓鱼总次数
 *   %kkfish_fishing_fails%                     钓鱼失败次数
 *   %kkfish_fishing_successes%                 钓鱼成功次数
 *   %kkfish_fishing_success_rate%              成功率
 *   %kkfish_fish_caught_&lt;鱼key&gt;%               指定鱼类成功次数
 *   %kkfish_fish_caught_&lt;鱼key&gt;_maxsize%       指定鱼类最大尺寸
 *   %kkfish_fish_level_&lt;等级key&gt;_caught%       指定等级鱼捕获数
 *   %kkfish_fish_most_valuable%                最值钱鱼价值
 *   %kkfish_fish_hook_material%                当前鱼钩材质
 *   %kkfish_fishing_time%                      总钓鱼时长（秒）
 * </pre>
 */
public class PapiHook extends PlaceholderExpansion {

    private final kkfish plugin;

    public PapiHook(kkfish plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "kkfish";
    }

    @Override
    public String getAuthor() {
        return "gukuan";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (offlinePlayer == null || params == null) {
            return null;
        }

        UUID uuid = offlinePlayer.getUniqueId();
        String id = uuid.toString();

        switch (params) {
            case "fish_caught_types":
                return String.valueOf(getTypesCount(uuid, id));
            case "fish_max_size":
                return formatSize(getMaxSize(uuid, id));
            case "fishing_level":
                return String.valueOf(getFishingLevel(offlinePlayer));
            case "fishing_attempts":
                return String.valueOf(getAttempts(uuid, id));
            case "fishing_fails":
                return String.valueOf(getFails(uuid, id));
            case "fishing_successes":
                return String.valueOf(getSuccesses(uuid, id));
            case "fishing_success_rate":
                return String.valueOf(getSuccessRate(uuid, id));
            case "fish_most_valuable":
                return String.valueOf(getMostValuable(id));
            case "fish_hook_material":
                return getHookMaterial(uuid, id);
            case "fishing_time":
                return String.valueOf(getFishingTime(id));
            default:
                break;
        }

        if (params.startsWith("fish_caught_")) {
            return handleFishCaught(params, uuid, id);
        }

        if (params.startsWith("fish_level_") && params.endsWith("_caught")) {
            String levelKey = params.substring("fish_level_".length(), params.length() - "_caught".length());
            if (!levelKey.isEmpty()) {
                return String.valueOf(getFishLevelCount(id, levelKey));
            }
        }

        return null;
    }

    // ===== 鱼类统计 =====

    private String handleFishCaught(String params, UUID uuid, String id) {
        String rest = params.substring("fish_caught_".length());
        boolean maxSize = rest.endsWith("_maxsize");
        if (maxSize) {
            rest = rest.substring(0, rest.length() - "_maxsize".length());
        }
        if (rest.isEmpty()) {
            return null;
        }

        Map<String, FishRecordData> records = getFishRecords(uuid, id);
        FishRecordData record = records.get(rest);
        if (record == null) {
            return "0";
        }
        return maxSize ? formatSize(record.getMaxSize()) : String.valueOf(record.getCount());
    }

    private int getTypesCount(UUID uuid, String id) {
        Map<String, FishRecordData> records = getFishRecords(uuid, id);
        return records.size();
    }

    private double getMaxSize(UUID uuid, String id) {
        Map<String, FishRecordData> records = getFishRecords(uuid, id);
        double max = 0.0;
        for (FishRecordData record : records.values()) {
            if (record.getMaxSize() > max) {
                max = record.getMaxSize();
            }
        }
        return max;
    }

    /**
     * 获取鱼记录：在线读内存，离线查数据库聚合。
     */
    private Map<String, FishRecordData> getFishRecords(UUID uuid, String id) {
        PersistentPlayerData persistent = getPersistent(uuid);
        if (persistent != null) {
            return persistent.getFishRecords();
        }
        DB db = plugin.getDB();
        if (db != null) {
            return db.getPlayerFishRecords(id);
        }
        return Collections.emptyMap();
    }

    // ===== 总次数/成功/失败 =====

    private int getAttempts(UUID uuid, String id) {
        PersistentPlayerData persistent = getPersistent(uuid);
        if (persistent != null) {
            return persistent.getTotalAttempts();
        }
        DB db = plugin.getDB();
        return db != null ? db.getPlayerTotalAttempts(id) : 0;
    }

    private int getFails(UUID uuid, String id) {
        PersistentPlayerData persistent = getPersistent(uuid);
        if (persistent != null) {
            return persistent.getFailCount();
        }
        DB db = plugin.getDB();
        return db != null ? db.getPlayerFailCount(id) : 0;
    }

    private int getSuccesses(UUID uuid, String id) {
        PersistentPlayerData persistent = getPersistent(uuid);
        if (persistent != null) {
            return persistent.getSuccessCount();
        }
        int attempts = getAttempts(uuid, id);
        int fails = getFails(uuid, id);
        return Math.max(0, attempts - fails);
    }

    private double getSuccessRate(UUID uuid, String id) {
        int attempts = getAttempts(uuid, id);
        if (attempts <= 0) {
            return 0.0;
        }
        int successes = getSuccesses(uuid, id);
        return Math.round(successes * 1000.0 / attempts) / 10.0;
    }

    // ===== 其他数据 =====

    private int getFishingLevel(OfflinePlayer offlinePlayer) {
        AuraSkills auraSkills = plugin.getAuraSkills();
        if (auraSkills == null || !offlinePlayer.isOnline()) {
            return 0;
        }
        return auraSkills.getFishingLevel(offlinePlayer.getPlayer());
    }

    private int getMostValuable(String id) {
        DB db = plugin.getDB();
        return db != null ? db.getPlayerMostValuable(id) : 0;
    }

    private String getHookMaterial(UUID uuid, String id) {
        PlayerContext ctx = getContext(uuid);
        if (ctx != null && ctx.getSession().getHookMaterial() != null) {
            return ctx.getSession().getHookMaterial().name();
        }
        DB db = plugin.getDB();
        return db != null ? db.getPlayerHookMaterial(id) : "wood";
    }

    private int getFishingTime(String id) {
        DB db = plugin.getDB();
        return db != null ? db.getPlayerTotalFishingTime(id) : 0;
    }

    private int getFishLevelCount(String id, String levelKey) {
        DB db = plugin.getDB();
        return db != null ? db.getPlayerFishLevelCount(id, levelKey) : 0;
    }

    // ===== 工具 =====

    private PlayerContext getContext(UUID uuid) {
        PlayerContextStore store = plugin.getPlayerContextStore();
        if (store == null) {
            return null;
        }
        return store.getUsableContext(uuid);
    }

    private PersistentPlayerData getPersistent(UUID uuid) {
        PlayerContext ctx = getContext(uuid);
        return ctx != null ? ctx.getPersistent() : null;
    }

    private String formatSize(double size) {
        return String.format(Locale.ROOT, "%.1f", size);
    }
}
