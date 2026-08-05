package me.kkfish.player;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家持久化数据（需要写入存储）。
 *
 * <p>这部分数据在玩家退出时会被快照并异步保存到 DataSource，
 * 在玩家加入时从 DataSource 异步加载。所有字段线程安全。</p>
 *
 * <h3>迁移来源</h3>
 * <ul>
 *   <li>{@code Fish.playerFishRecords} → {@link #fishRecords}</li>
 *   <li>{@code MessageManager.playerLangCache} → {@link #language}</li>
 * </ul>
 */
public class PersistentPlayerData {

    /**
     * 按鱼名索引的钓鱼记录。
     *
     * <p>迁移自 {@code Fish.playerFishRecords}（原 {@code Map<UUID, Map<String, FishRecord>>}）。
     * 外层 Map 按 UUID 拆分到 PlayerContext 后，内层 Map 保留在此。</p>
     */
    private final Map<String, FishRecordData> fishRecords = new ConcurrentHashMap<>();

    /**
     * 玩家语言偏好（如 "zh" / "en"），null 表示使用默认语言。
     *
     * <p>迁移自 {@code MessageManager.playerLangCache}。</p>
     */
    private volatile String language;

    /**
     * 钓鱼总次数（小游戏完成次数 = 成功 + 失败）。
     */
    private volatile int totalAttempts;

    /**
     * 钓鱼失败次数（小游戏失败结算次数）。
     */
    private volatile int failCount;

    public PersistentPlayerData() {
    }

    /**
     * 获取鱼记录映射（可变视图，调用方可直接读写）。
     *
     * @return 鱼名 → 记录 的并发映射
     */
    public Map<String, FishRecordData> getFishRecords() {
        return fishRecords;
    }

    /**
     * 获取或创建指定鱼的记录。
     *
     * @param fishName 鱼名
     * @return 记录对象（不存在时自动创建）
     */
    public FishRecordData getOrCreateRecord(String fishName) {
        return fishRecords.computeIfAbsent(fishName, k -> new FishRecordData());
    }

    /**
     * 记录一次钓鱼捕获。
     *
     * @param fishName 鱼名
     * @param size     鱼的尺寸
     */
    public void recordCatch(String fishName, double size) {
        FishRecordData record = getOrCreateRecord(fishName);
        record.incrementCount();
        record.updateMaxSize(size);
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    // ===== 钓鱼统计 =====

    public int getTotalAttempts() {
        return totalAttempts;
    }

    public void setTotalAttempts(int totalAttempts) {
        this.totalAttempts = totalAttempts;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    /**
     * 获取成功次数（总次数 - 失败次数）。
     *
     * @return 成功次数
     */
    public int getSuccessCount() {
        return Math.max(0, totalAttempts - failCount);
    }

    /**
     * 记录一次钓鱼完成（成功或失败）。
     */
    public void recordAttempt() {
        totalAttempts++;
    }

    /**
     * 记录一次钓鱼失败。
     */
    public void recordFail() {
        failCount++;
    }

    /**
     * 将当前持久化数据快照为不可变副本，用于异步保存。
     *
     * @return 不可变快照
     */
    public PersistentPlayerData snapshot() {
        PersistentPlayerData snap = new PersistentPlayerData();
        for (Map.Entry<String, FishRecordData> entry : fishRecords.entrySet()) {
            snap.fishRecords.put(entry.getKey(), entry.getValue().copy());
        }
        snap.language = this.language;
        snap.totalAttempts = this.totalAttempts;
        snap.failCount = this.failCount;
        return snap;
    }

    /**
     * 清空所有持久化数据（用于上下文销毁）。
     */
    public void clear() {
        fishRecords.clear();
        language = null;
        totalAttempts = 0;
        failCount = 0;
    }

    /**
     * 单种鱼的钓鱼记录数据。
     *
     * <p>迁移自 {@code Fish.FishRecord} 内部类（注意与 {@code gui.FishRecord} 不同，
     * 后者是竞赛统计记录）。</p>
     */
    public static class FishRecordData {
        private volatile int count;
        private volatile double maxSize;

        public FishRecordData() {
            this(0, 0.0);
        }

        public FishRecordData(int count, double maxSize) {
            this.count = count;
            this.maxSize = maxSize;
        }

        public int getCount() {
            return count;
        }

        public double getMaxSize() {
            return maxSize;
        }

        public void incrementCount() {
            count++;
        }

        public void updateMaxSize(double size) {
            if (size > maxSize) {
                maxSize = size;
            }
        }

        /**
         * 创建当前记录的不可变副本。
         *
         * @return 副本
         */
        public FishRecordData copy() {
            return new FishRecordData(count, maxSize);
        }
    }
}
