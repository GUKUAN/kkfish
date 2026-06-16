package me.kkfish.player;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 玩家上下文（每玩家一个实例）。
 *
 * <p>统一持有玩家身份、持久化数据、会话数据和运行时数据，
 * 并管理生命周期标签转换。所有业务模块通过 PlayerContext 访问玩家状态，
 * 不再直接持有分散的 {@code Map<UUID, ?>}。</p>
 *
 * <h3>生命周期</h3>
 * <pre>
 *   CREATED → LOADING → ACTIVE → QUIT_PENDING → SAVING → DESTROYING → CLEANED
 * </pre>
 *
 * <h3>线程安全</h3>
 * <ul>
 *   <li>生命周期标签使用 {@link AtomicReference} 保证可见性和原子性</li>
 *   <li>子数据对象（{@link PersistentPlayerData}/{@link SessionData}/{@link RuntimeData}）内部线程安全</li>
 *   <li>状态转换由 {@link PlayerContextStore} 串行化，业务代码仅读取标签</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>
 *   PlayerContext ctx = playerContextStore.getContext(playerId);
 *   if (ctx == null || !ctx.isUsable()) return;
 *   ctx.getSession().setWaterType(waterType);
 *   ctx.getRuntime().setChargeStartTime(System.currentTimeMillis());
 * </pre>
 */
public class PlayerContext {

    private final PlayerIdentity identity;
    private final PersistentPlayerData persistent;
    private final SessionData session;
    private final RuntimeData runtime;

    private final AtomicReference<LifecycleTag> lifecycle = new AtomicReference<>(LifecycleTag.CREATED);

    public PlayerContext(PlayerIdentity identity) {
        this.identity = identity;
        this.persistent = new PersistentPlayerData();
        this.session = new SessionData();
        this.runtime = new RuntimeData();
    }

    public PlayerIdentity getIdentity() {
        return identity;
    }

    public PersistentPlayerData getPersistent() {
        return persistent;
    }

    public SessionData getSession() {
        return session;
    }

    public RuntimeData getRuntime() {
        return runtime;
    }

    // ===== 生命周期 =====

    public LifecycleTag getLifecycle() {
        return lifecycle.get();
    }

    /**
     * 尝试将生命周期转换到目标标签。
     *
     * <p>仅允许向前转换（CREATED → ... → CLEANED），不允许回退。
     * 由 {@link PlayerContextStore} 在串行化门内调用。</p>
     *
     * @param target 目标标签
     * @return true 如果转换成功
     */
    public boolean transitionTo(LifecycleTag target) {
        while (true) {
            LifecycleTag current = lifecycle.get();
            if (current.ordinal() > target.ordinal()) {
                return false; // 不允许回退
            }
            if (current == target) {
                return true;
            }
            if (lifecycle.compareAndSet(current, target)) {
                return true;
            }
        }
    }

    /**
     * 判断当前是否可接受业务动作（仅 ACTIVE 状态）。
     *
     * @return true 如果上下文处于 ACTIVE 状态
     */
    public boolean isUsable() {
        return lifecycle.get().isUsable();
    }

    /**
     * 判断当前是否处于关闭流程中。
     *
     * @return true 如果已进入 QUIT_PENDING 或之后的状态
     */
    public boolean isClosing() {
        return lifecycle.get().isClosing();
    }

    /**
     * 销毁上下文：取消所有任务、清空会话和运行时数据。
     *
     * <p>由 {@link PlayerContextStore} 在 DESTROYING 阶段调用。
     * 调用后上下文进入 CLEANED 状态，不可再使用。</p>
     */
    public void destroy() {
        runtime.clear();
        session.clear();
        transitionTo(LifecycleTag.CLEANED);
    }
}
