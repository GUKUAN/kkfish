package me.kkfish.player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;

import me.kkfish.fishing.HookMechanic;
import me.kkfish.fishing.WaterType;
import me.kkfish.misc.minigame.GameSession;
import me.kkfish.scheduler.SchedulerTask;

/**
 * 玩家会话数据（在线期间临时状态）。
 *
 * <p>持有玩家当前活跃的钓鱼/小游戏/GUI/ActionBar 等会话级资源。
 * 这些数据在玩家退出时需要先关闭资源（取消任务/移除实体/关闭菜单），
 * 然后才能进入 SAVING 阶段。</p>
 *
 * <h3>迁移来源</h3>
 * <ul>
 *   <li>{@code Fish.activeSessions} → {@link #fishingSession}</li>
 *   <li>{@code Fish.minigameData} → {@link #minigameData}</li>
 *   <li>{@code Fish.playerHookMaterials} → {@link #hookMaterial}</li>
 *   <li>{@code Fish.playerWaterType} → {@link #waterType}</li>
 *   <li>{@code Fish.playerHookMechanic} → {@link #hookMechanic}</li>
 *   <li>{@code MinigameManager.gameSessions} → {@link #minigameSession}</li>
 *   <li>{@code GUI.fishDexPages} 等 → {@link #menuView}</li>
 *   <li>{@code ActionBarUtil.persistentMessageTasks} → {@link #actionBar}</li>
 * </ul>
 */
public class SessionData {

    /**
     * 当前钓鱼会话的 ArmorStand（用于显示鱼上钩提示）。
     *
     * <p>迁移自 {@code Fish.activeSessions}。
     * null 表示当前无活跃钓鱼会话。</p>
     */
    private volatile ArmorStand fishingSession;

    /**
     * 小游戏物理数据。
     *
     * <p>迁移自 {@code Fish.minigameData}（原内部类 {@code MinigameData}）。
     * null 表示当前未在小游戏中。</p>
     */
    private volatile MinigameData minigameData;

    /**
     * 当前使用的鱼钩材质。
     *
     * <p>迁移自 {@code Fish.playerHookMaterials}。
     * null 表示使用默认鱼钩。</p>
     */
    private volatile Material hookMaterial;

    /**
     * 当前钓鱼水域类型。
     *
     * <p>迁移自 {@code Fish.playerWaterType}。
     * null 表示尚未确定水域。</p>
     */
    private volatile WaterType waterType;

    /**
     * 当前鱼钩机制实例。
     *
     * <p>迁移自 {@code Fish.playerHookMechanic}。
     * null 表示尚未选择机制。</p>
     */
    private volatile HookMechanic hookMechanic;

    /**
     * 当前小游戏会话。
     *
     * <p>迁移自 {@code MinigameManager.gameSessions}。
     * null 表示当前未在小游戏中。</p>
     */
    private volatile GameSession minigameSession;

    /**
     * 当前活跃菜单视图（分页/搜索/排序状态）。
     *
     * <p>迁移自 {@code GUI.fishDexPages}、{@code GUI.fishDexSearch} 等分散字段。</p>
     */
    private final MenuView menuView = new MenuView();

    /**
     * 持久 ActionBar 状态。
     *
     * <p>迁移自 {@code ActionBarUtil.persistentMessageTasks}。
     * 按 MessageType 索引的调度任务。</p>
     */
    private final Map<Enum<?>, SchedulerTask> actionBarTasks = new ConcurrentHashMap<>();

    public SessionData() {
    }

    public ArmorStand getFishingSession() {
        return fishingSession;
    }

    public void setFishingSession(ArmorStand fishingSession) {
        this.fishingSession = fishingSession;
    }

    public MinigameData getMinigameData() {
        return minigameData;
    }

    public void setMinigameData(MinigameData minigameData) {
        this.minigameData = minigameData;
    }

    /**
     * 获取或创建小游戏数据。
     *
     * @return 小游戏数据（不存在时自动创建）
     */
    public MinigameData getOrCreateMinigameData() {
        if (minigameData == null) {
            minigameData = new MinigameData();
        }
        return minigameData;
    }

    public Material getHookMaterial() {
        return hookMaterial;
    }

    public void setHookMaterial(Material hookMaterial) {
        this.hookMaterial = hookMaterial;
    }

    public WaterType getWaterType() {
        return waterType;
    }

    public void setWaterType(WaterType waterType) {
        this.waterType = waterType;
    }

    public HookMechanic getHookMechanic() {
        return hookMechanic;
    }

    public void setHookMechanic(HookMechanic hookMechanic) {
        this.hookMechanic = hookMechanic;
    }

    public GameSession getMinigameSession() {
        return minigameSession;
    }

    public void setMinigameSession(GameSession minigameSession) {
        this.minigameSession = minigameSession;
    }

    public MenuView getMenuView() {
        return menuView;
    }

    public Map<Enum<?>, SchedulerTask> getActionBarTasks() {
        return actionBarTasks;
    }

    /**
     * 关闭所有会话资源（取消任务/清空引用），用于上下文销毁。
     */
    public void clear() {
        fishingSession = null;
        minigameData = null;
        hookMaterial = null;
        waterType = null;
        hookMechanic = null;
        minigameSession = null;
        menuView.clear();
        for (SchedulerTask task : actionBarTasks.values()) {
            if (task != null) {
                try {
                    task.cancel();
                } catch (Exception ignored) {
                }
            }
        }
        actionBarTasks.clear();
    }

    /**
     * 小游戏物理数据（迁移自 {@code Fish.MinigameData} 内部类）。
     */
    public static class MinigameData {
        private volatile double greenBarPosition = 0.5;
        private volatile double greenBarSpeed = 0;
        private volatile double fishPosition = 0.5;
        private volatile double progress = 0;

        public double getGreenBarPosition() {
            return greenBarPosition;
        }

        public void setGreenBarPosition(double greenBarPosition) {
            this.greenBarPosition = greenBarPosition;
        }

        public double getGreenBarSpeed() {
            return greenBarSpeed;
        }

        public void setGreenBarSpeed(double greenBarSpeed) {
            this.greenBarSpeed = greenBarSpeed;
        }

        public double getFishPosition() {
            return fishPosition;
        }

        public void setFishPosition(double fishPosition) {
            this.fishPosition = fishPosition;
        }

        public double getProgress() {
            return progress;
        }

        public void setProgress(double progress) {
            this.progress = progress;
        }
    }

    /**
     * 菜单视图状态（分页/搜索/排序），迁移自 {@code GUI} 中分散的 Map。
     */
    public static class MenuView {
        private volatile int fishDexPage = 0;
        private volatile String fishDexSearch = null;
        private volatile int fishRecordPage = 0;
        private volatile String fishRecordSearch = null;
        private volatile String sorting = null;

        public int getFishDexPage() {
            return fishDexPage;
        }

        public void setFishDexPage(int fishDexPage) {
            this.fishDexPage = fishDexPage;
        }

        public String getFishDexSearch() {
            return fishDexSearch;
        }

        public void setFishDexSearch(String fishDexSearch) {
            this.fishDexSearch = fishDexSearch;
        }

        public int getFishRecordPage() {
            return fishRecordPage;
        }

        public void setFishRecordPage(int fishRecordPage) {
            this.fishRecordPage = fishRecordPage;
        }

        public String getFishRecordSearch() {
            return fishRecordSearch;
        }

        public void setFishRecordSearch(String fishRecordSearch) {
            this.fishRecordSearch = fishRecordSearch;
        }

        public String getSorting() {
            return sorting;
        }

        public void setSorting(String sorting) {
            this.sorting = sorting;
        }

        public void clear() {
            fishDexPage = 0;
            fishDexSearch = null;
            fishRecordPage = 0;
            fishRecordSearch = null;
            sorting = null;
        }
    }
}
