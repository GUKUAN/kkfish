package me.kkfish.events;

import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * 小游戏结束事件。
 *
 * <p>由 {@code MinigameService} 在小游戏会话结束时发布（无论成功或失败）。
 * 订阅者：{@code ActionBarService}（清理提示）、{@code SoundManager}（播放音效）。</p>
 *
 * <p>注意：成功捕获的详细信息通过 {@link FishCaughtEvent} 发布，
 * 此事件仅用于小游戏会话生命周期通知（如音效/ActionBar 清理）。</p>
 */
public final class MinigameEndedEvent extends DomainEvent {

    private final UUID playerId;
    private final String playerName;
    private final boolean success;

    public MinigameEndedEvent(Player player, boolean success) {
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.success = success;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public boolean isSuccess() {
        return success;
    }
}
