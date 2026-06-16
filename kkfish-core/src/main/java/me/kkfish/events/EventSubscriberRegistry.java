package me.kkfish.events;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.kkfish.kkfish;
import me.kkfish.managers.Compete;
import me.kkfish.managers.Fish;

/**
 * 事件订阅者注册表。
 *
 * <p>集中注册域事件的订阅者，将事件路由到对应的域服务。
 * 在 RootService.startup() 中创建，close() 时取消所有订阅。</p>
 */
public class EventSubscriberRegistry implements AutoCloseable {

    private final kkfish plugin;
    private final EventBus eventBus;
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();

    public EventSubscriberRegistry(kkfish plugin, EventBus eventBus) {
        this.plugin = plugin;
        this.eventBus = eventBus;
    }

    /**
     * 注册所有事件订阅者。
     */
    public void registerAll() {
        // FishCaughtEvent → 钓鱼记录
        subscriptions.add(eventBus.subscribe(FishCaughtEvent.class, this::onFishCaughtRecord));
        // FishCaughtEvent → 竞赛计分
        subscriptions.add(eventBus.subscribe(FishCaughtEvent.class, this::onFishCaughtCompetition));
        // FishCaughtEvent → 广播
        subscriptions.add(eventBus.subscribe(FishCaughtEvent.class, this::onFishCaughtBroadcast));
    }

    /**
     * 订阅者：记录钓鱼统计（原 plugin.getFish().recordFishCatch）
     */
    private void onFishCaughtRecord(FishCaughtEvent event) {
        Fish fish = plugin.getFish();
        if (fish == null) return;
        Player player = Bukkit.getPlayer(event.getPlayerId());
        if (player != null && player.isOnline()) {
            fish.recordFishCatch(player, event.getFishName(), event.getFishItem());
        }
    }

    /**
     * 订阅者：竞赛计分（原 plugin.getCompete().recordPlayerCatch）
     */
    private void onFishCaughtCompetition(FishCaughtEvent event) {
        Compete compete = plugin.getCompete();
        if (compete == null) return;
        Player player = Bukkit.getPlayer(event.getPlayerId());
        if (player != null && player.isOnline()) {
            compete.recordPlayerCatch(player, event.getFishName(), event.getFishValue());
        }
    }

    /**
     * 订阅者：广播捕获消息（原 fishingManager.sendFishBroadcast）
     */
    private void onFishCaughtBroadcast(FishCaughtEvent event) {
        if (!event.shouldAnnounce()) return;
        Fish fish = plugin.getFish();
        if (fish == null) return;
        Player player = Bukkit.getPlayer(event.getPlayerId());
        if (player != null && player.isOnline()) {
            fish.sendFishBroadcast(player, event.getFishDisplayName(), event.getFishSize(),
                event.getFishRarity(), event.getFishValue());
        }
    }

    @Override
    public void close() {
        for (EventBus.Subscription sub : subscriptions) {
            sub.unsubscribe();
        }
        subscriptions.clear();
    }
}
