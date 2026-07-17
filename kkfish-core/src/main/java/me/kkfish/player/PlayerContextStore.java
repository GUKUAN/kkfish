package me.kkfish.player;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.kkfish.kkfish;
import me.kkfish.events.EventBus;
import me.kkfish.events.PlayerContextClosingEvent;
import me.kkfish.events.PlayerContextLoadedEvent;
import me.kkfish.managers.DB;
import me.kkfish.misc.MessageManager;
import me.kkfish.utils.SchedulerUtil;

/**
 * 玩家上下文存储与生命周期管理。
 *
 * <p>负责 {@link PlayerContext} 的创建、加载、保存和销毁，
 * 是所有玩家状态的唯一入口。业务模块通过 {@link #getContext(UUID)} 获取上下文，
 * 不再直接持有分散的 {@code Map<UUID, ?>}。</p>
 *
 * <h3>生命周期编排</h3>
 * <pre>
 *   PlayerJoinEvent:
 *     CREATED → LOADING (异步加载持久化数据) → ACTIVE
 *   PlayerQuitEvent:
 *     ACTIVE → QUIT_PENDING (等待活跃会话关闭) → SAVING (异步保存) → DESTROYING → CLEANED
 *   插件关闭:
 *     遍历所有上下文，执行 QUIT_PENDING → ... → CLEANED
 * </pre>
 *
 * <h3>数据循环（DataLoop）</h3>
 * <p>每玩家的异步 IO 操作（加载/保存）通过单线程执行器串行化，
 * 避免并发写入同一玩家的数据。主线程仅提交任务，不阻塞。</p>
 *
 * <h3>重连保护</h3>
 * <p>玩家退出后，如果旧上下文仍在 SAVING/DESTROYING 阶段，
 * 新的加入请求会被拒绝（在 PlayerPreLoginEvent 中检查），
 * 直到旧上下文完全清理。</p>
 */
public class PlayerContextStore {

    private final kkfish plugin;
    private final DB db;
    private final MessageManager messageManager;
    private final EventBus eventBus;

    /** 活跃上下文映射（UUID → PlayerContext）。 */
    private final ConcurrentHashMap<UUID, PlayerContext> contexts = new ConcurrentHashMap<>();

    /** 异步 IO 串行化执行器（单线程，保证每玩家操作顺序）。 */
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kkfish-player-io");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public PlayerContextStore(kkfish plugin, DB db, EventBus eventBus) {
        this.plugin = plugin;
        this.db = db;
        this.messageManager = plugin.getMessageManager();
        this.eventBus = eventBus;
    }

    // ===== 查询 =====

    /**
     * 为所有在线玩家初始化上下文（用于插件 reload 场景）。
     *
     * <p>在 RootService.startup() 末尾调用，确保 reload 后在线玩家也有上下文。</p>
     */
    public void initializeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!contexts.containsKey(player.getUniqueId())) {
                onPlayerJoin(player);
            }
        }
    }

    /**
     * 获取玩家上下文。
     *
     * @param playerId 玩家 UUID
     * @return 上下文，不存在或已清理返回 null
     */
    public PlayerContext getContext(UUID playerId) {
        return contexts.get(playerId);
    }

    /**
     * 获取玩家上下文，仅当处于 ACTIVE 状态时返回。
     *
     * @param playerId 玩家 UUID
     * @return 可用的上下文，否则 null
     */
    public PlayerContext getUsableContext(UUID playerId) {
        PlayerContext ctx = contexts.get(playerId);
        return (ctx != null && ctx.isUsable()) ? ctx : null;
    }

    /**
     * 获取所有活跃上下文（只读视图）。
     *
     * @return 上下文集合
     */
    public Collection<PlayerContext> getAllContexts() {
        return contexts.values();
    }

    /**
     * 判断指定玩家是否已有上下文（含正在关闭的）。
     *
     * @param playerId 玩家 UUID
     * @return true 如果存在上下文
     */
    public boolean hasContext(UUID playerId) {
        return contexts.containsKey(playerId);
    }

    /**
     * 检查玩家是否可以重连（旧上下文不在保存/销毁中）。
     *
     * <p>在 {@code AsyncPlayerPreLoginEvent} 中调用，
     * 如果返回 false 则拒绝登录。</p>
     *
     * @param playerId 玩家 UUID
     * @return true 如果可以重连
     */
    public boolean canReconnect(UUID playerId) {
        PlayerContext ctx = contexts.get(playerId);
        if (ctx == null) {
            return true;
        }
        LifecycleTag tag = ctx.getLifecycle();
        // 旧上下文仍在保存或销毁中，拒绝重连
        return tag.ordinal() < LifecycleTag.SAVING.ordinal();
    }

    // ===== 生命周期 =====

    /**
     * 玩家加入：创建上下文并异步加载持久化数据。
     *
     * <p>在 {@code PlayerJoinEvent} 中调用。
     * 创建后立即进入 LOADING 状态，业务动作在 ACTIVE 前被 gate。</p>
     *
     * @param player 玩家
     */
    public void onPlayerJoin(Player player) {
        if (closed.get()) return;

        UUID playerId = player.getUniqueId();

        // 如果旧上下文残留（理论上不应发生），先清理
        PlayerContext old = contexts.remove(playerId);
        if (old != null) {
            old.destroy();
        }

        PlayerContext ctx = new PlayerContext(new PlayerIdentity(playerId, player.getName()));
        contexts.put(playerId, ctx);

        // CREATED → LOADING
        ctx.transitionTo(LifecycleTag.LOADING);

        // 异步加载持久化数据
        ioExecutor.submit(() -> {
            try {
                loadPersistentData(ctx);
            } catch (Exception e) {
                log("§cFailed to load player data for " + player.getName() + ": " + e.getMessage());
            } finally {
                // LOADING → ACTIVE（回到主线程标记）
                SchedulerUtil.runSync(plugin, () -> {
                    ctx.transitionTo(LifecycleTag.ACTIVE);
                });
            }
        });
    }

    /**
     * 玩家退出：标记 QUIT_PENDING，若无活跃会话则立即保存。
     *
     * <p>在 {@code PlayerQuitEvent} 中调用。</p>
     *
     * @param player 玩家
     */
    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerContext ctx = contexts.get(playerId);
        if (ctx == null) {
            return;
        }

        // ACTIVE → QUIT_PENDING
        ctx.transitionTo(LifecycleTag.QUIT_PENDING);

        // 发布上下文关闭中事件（让订阅者清理玩家资源）
        if (eventBus != null) {
            eventBus.publish(new PlayerContextClosingEvent(player, ctx));
        }

        // 取消所有运行时任务（蓄力/咬钩/效果等）
        ctx.getRuntime().cancelAllTasks();

        // 清理会话资源（ArmorStand/小游戏会话等）
        ctx.getSession().clear();

        // 异步保存并销毁
        saveAndDestroy(ctx);
    }

    /**
     * 异步保存持久化数据并销毁上下文。
     *
     * @param ctx 玩家上下文
     */
    private void saveAndDestroy(PlayerContext ctx) {
        // QUIT_PENDING → SAVING
        ctx.transitionTo(LifecycleTag.SAVING);

        // 快照持久化数据（在主线程复制，避免异步读取可变状态）
        PersistentPlayerData snapshot = ctx.getPersistent().snapshot();

        ioExecutor.submit(() -> {
            try {
                savePersistentData(ctx.getIdentity().getUuid(), snapshot);
            } catch (Exception e) {
                log("§cFailed to save player data for " + ctx.getIdentity().getName() + ": " + e.getMessage());
            } finally {
                // SAVING → DESTROYING → CLEANED
                SchedulerUtil.runSync(plugin, () -> {
                    ctx.transitionTo(LifecycleTag.DESTROYING);
                    ctx.destroy();
                    contexts.remove(ctx.getIdentity().getUuid());
                });
            }
        });
    }

    // ===== 持久化加载/保存 =====

    /**
     * 从 DataSource 加载玩家持久化数据到上下文。
     *
     * <p>在 IO 线程执行。加载完成后上下文进入 ACTIVE。</p>
     *
     * @param ctx 玩家上下文
     */
    private void loadPersistentData(PlayerContext ctx) {
        if (db == null || !db.isDatabaseAvailable()) {
            return;
        }

        String playerId = ctx.getIdentity().getUuid().toString();

        // 加载语言偏好
        try {
            String lang = db.getPlayerLanguage(playerId);
            if (lang != null && !lang.isEmpty()) {
                ctx.getPersistent().setLanguage(lang);
            }
        } catch (Exception e) {
            log("§cFailed to load language for " + ctx.getIdentity().getName() + ": " + e.getMessage());
        }

        // 按需加载鱼记录（延迟加载：仅在玩家打开图鉴时按鱼名加载）
        // 当前阶段仅加载语言，鱼记录在迁移阶段逐步接入
    }

    /**
     * 将玩家持久化数据快照保存到 DataSource。
     *
     * <p>在 IO 线程执行，仅读取不可变快照。</p>
     *
     * @param playerId 玩家 UUID
     * @param snapshot 持久化数据快照
     */
    private void savePersistentData(UUID playerId, PersistentPlayerData snapshot) {
        if (db == null || !db.isDatabaseAvailable()) {
            return;
        }

        String id = playerId.toString();

        // 保存语言偏好
        try {
            String lang = snapshot.getLanguage();
            if (lang != null && !lang.isEmpty()) {
                db.setPlayerLanguage(id, lang);
            }
        } catch (Exception e) {
            log("§cFailed to save language for " + playerId + ": " + e.getMessage());
        }

        // 鱼记录保存在迁移阶段逐步接入
    }

    // ===== 关闭 =====

    /**
     * 关闭存储：保存并销毁所有活跃上下文。
     *
     * <p>在插件 {@code onDisable} 中调用。
     * 关闭后不再接受新的上下文操作。</p>
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        // 标记所有上下文为 QUIT_PENDING
        for (PlayerContext ctx : contexts.values()) {
            ctx.transitionTo(LifecycleTag.QUIT_PENDING);
            ctx.getRuntime().cancelAllTasks();
            ctx.getSession().clear();
        }

        // 提交所有保存任务
        for (PlayerContext ctx : contexts.values()) {
            ctx.transitionTo(LifecycleTag.SAVING);
            PersistentPlayerData snapshot = ctx.getPersistent().snapshot();
            ioExecutor.submit(() -> {
                try {
                    savePersistentData(ctx.getIdentity().getUuid(), snapshot);
                } catch (Exception e) {
                    log("§cFailed to save player data on shutdown: " + e.getMessage());
                } finally {
                    ctx.transitionTo(LifecycleTag.DESTROYING);
                    ctx.destroy();
                }
            });
        }

        // 优雅关闭 IO 执行器（等待保存完成）
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log("§cPlayer IO executor did not terminate in 10s, forcing shutdown");
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 清理所有上下文
        contexts.clear();

        log(messageManager.getMessageWithoutPrefix("log.player_context_store_closed",
                "PlayerContextStore has been closed."));
    }

    // ===== 工具 =====

    private void log(String message) {
        Bukkit.getConsoleSender().sendMessage(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', message));
    }
}
