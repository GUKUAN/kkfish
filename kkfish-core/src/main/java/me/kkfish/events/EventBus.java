package me.kkfish.events;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 根事件总线。
 *
 * <p>域事件的发布/订阅中心。所有域服务通过此总线解耦：
 * 发布者只需 {@link #publish(DomainEvent)}，订阅者通过 {@link #subscribe(Class, Consumer)}
 * 注册回调，总线负责按事件类型分发。</p>
 *
 * <h3>线程安全</h3>
 * <ul>
 *   <li>订阅列表使用 {@link CopyOnWriteArrayList}，订阅/取消订阅操作线程安全</li>
 *   <li>事件分发在发布者线程同步执行（订阅者应避免阻塞操作）</li>
 *   <li>需要异步处理的订阅者应自行调度到异步线程</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 订阅（通常在服务启动时）
 * SubscriptionToken token = eventBus.subscribe(FishCaughtEvent.class, event -> {
 *     recordFishCatch(event.getPlayer(), event.getFishName());
 * });
 *
 * // 发布（在业务流程中）
 * eventBus.publish(new FishCaughtEvent(player, fishName, value, rarity));
 *
 * // 取消订阅（在服务关闭时）
 * token.unsubscribe();
 * </pre>
 */
public class EventBus {

    /** 事件类型 → 订阅者列表。 */
    private final Map<Class<? extends DomainEvent>, List<Subscription>> subscribers = new ConcurrentHashMap<>();

    /**
     * 订阅指定类型的事件。
     *
     * @param eventType 事件类型（Class 对象）
     * @param consumer  事件处理回调
     * @param <T>       事件类型
     * @return 订阅令牌，用于取消订阅
     */
    public <T extends DomainEvent> Subscription subscribe(Class<T> eventType, Consumer<T> consumer) {
        Subscription sub = new Subscription(eventType, consumer, this);
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(sub);
        return sub;
    }

    /**
     * 发布事件到所有订阅者。
     *
     * @param event 事件对象
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void publish(DomainEvent event) {
        List<Subscription> list = subscribers.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Subscription sub : list) {
            try {
                ((Consumer) sub.consumer).accept(event);
            } catch (Exception e) {
                // 单个订阅者异常不影响其他订阅者
                org.bukkit.Bukkit.getConsoleSender().sendMessage(
                    org.bukkit.ChatColor.RED + "[kkfish EventBus] Subscriber error for "
                    + event.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * 取消指定订阅（由 {@link Subscription#unsubscribe()} 调用）。
     *
     * @param sub 订阅对象
     */
    void unsubscribe(Subscription sub) {
        List<Subscription> list = subscribers.get(sub.eventType);
        if (list != null) {
            list.remove(sub);
        }
    }

    /**
     * 取消所有订阅（用于插件关闭）。
     */
    public void clear() {
        subscribers.clear();
    }

    /**
     * 获取指定事件类型的订阅者数量（用于测试和调试）。
     *
     * @param eventType 事件类型
     * @return 订阅者数量
     */
    public int getSubscriberCount(Class<? extends DomainEvent> eventType) {
        List<Subscription> list = subscribers.get(eventType);
        return list != null ? list.size() : 0;
    }

    /**
     * 订阅令牌，持有订阅信息用于取消订阅。
     */
    public class Subscription {
        private final Class<? extends DomainEvent> eventType;
        private final Consumer<?> consumer;
        private final EventBus bus;
        private volatile boolean active = true;

        Subscription(Class<? extends DomainEvent> eventType, Consumer<?> consumer, EventBus bus) {
            this.eventType = eventType;
            this.consumer = consumer;
            this.bus = bus;
        }

        /**
         * 取消此订阅。
         *
         * <p>取消后不再接收新事件。重复调用安全。</p>
         */
        public void unsubscribe() {
            if (active) {
                active = false;
                bus.unsubscribe(this);
            }
        }

        public boolean isActive() {
            return active;
        }
    }
}
