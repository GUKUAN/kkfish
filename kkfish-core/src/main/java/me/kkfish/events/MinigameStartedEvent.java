package me.kkfish.events;

import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * 小游戏开始事件。
 *
 * <p>由 {@code MinigameService} 在小游戏会话启动时发布。
 * 订阅者：{@code ActionBarService}（显示提示）、{@code SoundManager}（播放音效）。</p>
 */
public final class MinigameStartedEvent extends DomainEvent {

    private final UUID playerId;
    private final String playerName;
    private final double chargePercentage;
    private final String baitName;

    public MinigameStartedEvent(Player player, double chargePercentage, String baitName) {
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.chargePercentage = chargePercentage;
        this.baitName = baitName;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public double getChargePercentage() {
        return chargePercentage;
    }

    public String getBaitName() {
        return baitName;
    }
}
