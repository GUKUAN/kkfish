package me.kkfish.player;

/**
 * 玩家上下文生命周期标签。
 *
 * <p>状态转换（单向）：
 * <pre>
 *   CREATED → LOADING → ACTIVE → QUIT_PENDING → SAVING → DESTROYING → CLEANED
 * </pre>
 *
 * <p>在并发场景下，状态转换由 {@link PlayerContextStore} 串行化，
 * 业务代码仅读取当前标签做判断，不直接修改。</p>
 */
public enum LifecycleTag {
    /** 已创建但尚未开始加载持久化数据。 */
    CREATED,

    /** 正在异步加载持久化数据（从 DataSource 读取）。 */
    LOADING,

    /** 已加载完成，可接受业务动作（钓鱼/小游戏/GUI 等）。 */
    ACTIVE,

    /** 玩家已退出，等待活跃会话关闭后快照保存。 */
    QUIT_PENDING,

    /** 正在将持久化数据快照写入存储。 */
    SAVING,

    /** 正在销毁上下文，释放所有运行时资源（任务/实体/UI）。 */
    DESTROYING,

    /** 已完全清理，不可再使用。 */
    CLEANED;

    /**
     * 判断当前状态是否允许业务动作。
     *
     * @return 仅 {@link #ACTIVE} 返回 true
     */
    public boolean isUsable() {
        return this == ACTIVE;
    }

    /**
     * 判断当前状态是否处于关闭流程中（退出后不可逆）。
     *
     * @return QUIT_PENDING 及之后的状态返回 true
     */
    public boolean isClosing() {
        return ordinal() >= QUIT_PENDING.ordinal();
    }
}
