package me.kkfish.events;

import java.util.UUID;

import org.bukkit.entity.Player;

import me.kkfish.player.PlayerContext;

/**
 * 玩家上下文加载完成事件。
 *
 * <p>由 {@code PlayerContextStore} 在玩家数据加载完成、上下文进入 ACTIVE 状态后发布。
 * 订阅者：各域服务初始化玩家相关状态（如竞赛记分板绑定、ActionBar 初始化等）。</p>
 */
public final class PlayerContextLoadedEvent extends DomainEvent {

    private final UUID playerId;
    private final String playerName;
    private final PlayerContext context;

    public PlayerContextLoadedEvent(Player player, PlayerContext context) {
        this.playerId = player.getUniqueId();
        this.playerName = player.getName();
        this.context = context;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public PlayerContext getContext() {
        return context;
    }
}
