package me.kkfish.events;

import java.util.UUID;

import org.bukkit.entity.Player;

import me.kkfish.player.PlayerContext;

/**
 * 玩家上下文关闭中事件。
 *
 * <p>由 {@code PlayerContextStore} 在玩家退出、上下文进入 QUIT_PENDING 阶段后发布。
 * 订阅者：各域服务清理玩家相关资源（如取消任务、移除记分板、关闭菜单等）。</p>
 *
 * <p>与 {@link PlayerContextLoadedEvent} 对称，一个在加入时发布，一个在退出时发布。</p>
 */
public final class PlayerContextClosingEvent extends DomainEvent {

    private final UUID playerId;
    private final String playerName;
    private final PlayerContext context;

    public PlayerContextClosingEvent(Player player, PlayerContext context) {
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
