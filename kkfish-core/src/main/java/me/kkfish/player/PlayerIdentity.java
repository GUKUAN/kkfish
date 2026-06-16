package me.kkfish.player;

import java.util.Objects;
import java.util.UUID;

/**
 * 玩家身份信息（不可变）。
 *
 * <p>仅持有标识性数据，不包含任何运行时或持久化业务状态。
 * 在 {@link PlayerContext} 生命周期内保持不变。</p>
 */
public final class PlayerIdentity {

    private final UUID uuid;
    private final String name;
    private final long creationTime;

    public PlayerIdentity(UUID uuid, String name) {
        this(uuid, name, System.currentTimeMillis());
    }

    public PlayerIdentity(UUID uuid, String name, long creationTime) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = name != null ? name : "";
        this.creationTime = creationTime;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerIdentity)) return false;
        PlayerIdentity that = (PlayerIdentity) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return "PlayerIdentity{uuid=" + uuid + ", name='" + name + "'}";
    }
}
