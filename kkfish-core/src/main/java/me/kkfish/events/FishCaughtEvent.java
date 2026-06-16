package me.kkfish.events;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 鱼被捕获事件。
 *
 * <p>由 {@code MinigameService}（小游戏成功结束）或 {@code FishingService}（原版捕获）发布。
 * 订阅者：
 * <ul>
 *   <li>{@code FishingService}：记录钓鱼统计</li>
 *   <li>{@code CompetitionService}：竞赛计分</li>
 *   <li>{@code RewardService}：发放奖励（经验/命令/经济）</li>
 *   <li>{@code MessageService}：广播消息</li>
 * </ul>
 * </p>
 *
 * <p>重构前，这些跨域操作内联在 {@code MinigameManager.endGame()} 中，
 * 直接调用 {@code plugin.getFish().recordFishCatch()}、
 * {@code plugin.getCompete().recordPlayerCatch()}、
 * {@code fishingManager.sendFishBroadcast()} 等 8 个跨域方法。</p>
 */
public final class FishCaughtEvent extends DomainEvent {

    private final UUID playerId;
    private final String playerName;
    private final String fishName;
    private final String fishDisplayName;
    private final double fishSize;
    private final String fishLevel;
    private final int fishRarity;
    private final double fishValue;
    private final ItemStack fishItem;
    private final boolean announce;

    public FishCaughtEvent(Player player, String fishName, String fishDisplayName,
                           double fishSize, String fishLevel, int fishRarity,
                           double fishValue, ItemStack fishItem, boolean announce) {
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.fishName = Objects.requireNonNull(fishName, "fishName");
        this.fishDisplayName = fishDisplayName != null ? fishDisplayName : fishName;
        this.fishSize = fishSize;
        this.fishLevel = fishLevel;
        this.fishRarity = fishRarity;
        this.fishValue = fishValue;
        this.fishItem = fishItem;
        this.announce = announce;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getFishName() {
        return fishName;
    }

    public String getFishDisplayName() {
        return fishDisplayName;
    }

    public double getFishSize() {
        return fishSize;
    }

    public String getFishLevel() {
        return fishLevel;
    }

    public int getFishRarity() {
        return fishRarity;
    }

    public double getFishValue() {
        return fishValue;
    }

    public ItemStack getFishItem() {
        return fishItem;
    }

    /**
     * 是否应广播此捕获（由配置决定）。
     *
     * @return true 如果应广播
     */
    public boolean shouldAnnounce() {
        return announce;
    }
}
