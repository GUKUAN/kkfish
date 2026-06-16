package me.kkfish.events;

/**
 * 所有域事件的基类。
 *
 * <p>域事件由域服务发布，由其他域服务订阅。
 * 事件本身仅携带数据，不包含业务逻辑。</p>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>事件类应为不可变值对象（所有字段 final）</li>
 *   <li>事件类应位于 {@code me.kkfish.events} 包</li>
 *   <li>事件命名使用过去式（如 {@code FishCaughtEvent}，而非 {@code CatchFishEvent}）</li>
 *   <li>发布者负责构造事件，订阅者只读取</li>
 * </ul>
 */
public abstract class DomainEvent {

    private final long timestamp;

    protected DomainEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    public long getTimestamp() {
        return timestamp;
    }
}
