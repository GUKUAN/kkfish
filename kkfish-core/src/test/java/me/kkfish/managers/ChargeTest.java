package me.kkfish.managers;

import me.kkfish.ConfigFixture;
import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;
import me.kkfish.misc.SoundManager;
import me.kkfish.player.PlayerContext;
import me.kkfish.player.PlayerContextStore;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChargeTest {
    public static class CatchFish extends Fish {
        double charge;
        public CatchFish(kkfish plugin) { super(plugin); }
        @Override public void throwFishHook(Player player, double percentage) { charge = percentage; }
    }
    public static class SilentSound extends SoundManager {
        public SilentSound(kkfish plugin) { super(plugin); }
        @Override public void playFastCastSound(Location location) { }
        @Override public void playPerfectCastSound(Location location) { }
        @Override public void playGoodCastSound(Location location) { }
        @Override public void playNormalCastSound(Location location) { }
    }

    private double stopCharge(boolean overcharged) throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        MessageManager messages = ConfigFixture.allocate(MessageManager.class);
        ConfigFixture.set(messages, "msgMap", new HashMap<>());
        ConfigFixture.set(messages, "currLang", "en_us");
        ConfigFixture.set(messages, "playerLangCache", new HashMap<>());
        ConfigFixture.set(fixture.plugin, "messageManager", messages);
        ConfigFixture.set(fixture.plugin, "soundManager", new SilentSound(fixture.plugin));
        UUID id = UUID.randomUUID();
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class},
                (proxy, method, args) -> method.getName().equals("getUniqueId") ? id : null);
        ConfigFixture.set(messages, "playerLangCache", Collections.singletonMap(id, "en_us"));
        PlayerContext context = new PlayerContext(null);
        PlayerContextStore store = ConfigFixture.allocate(PlayerContextStore.class);
        ConfigFixture.set(store, "contexts", new java.util.concurrent.ConcurrentHashMap<>(Collections.singletonMap(id, context)));
        CatchFish fish = ConfigFixture.allocate(CatchFish.class);
        ChargeProgressTracker tracker = new ChargeProgressTracker(fixture.plugin, fixture.config, messages, store, null, fish);
        context.getRuntime().setActiveChargeTask(tracker.new ChargeProgressTask(player, 100000));
        context.getRuntime().setChargeStartTime(System.currentTimeMillis() - 95000);
        tracker.stopCharging(player, overcharged);
        assertFalse(context.getRuntime().isCharging());
        assertNull(context.getRuntime().getActiveChargeTask());
        return fish.charge;
    }

    @Test
    void releaseUsesTheSameAdjustedTimeAsTheProgressTask() throws Exception {
        assertEquals(95.0, stopCharge(false), 1.0);
    }

    @Test
    void overchargeCastsWithoutPower() throws Exception {
        assertEquals(0.0, stopCharge(true));
    }
}
