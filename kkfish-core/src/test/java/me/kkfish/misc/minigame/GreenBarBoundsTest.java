package me.kkfish.misc.minigame;

import me.kkfish.ConfigFixture;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import me.kkfish.fishing.WaterType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

class GreenBarBoundsTest {
    private GameSession session() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getRodConfig().set("rods.default.float-area-size", 30);
        GameSession session = ConfigFixture.allocate(GameSession.class);
        ConfigFixture.set(session, "config", fixture.config);
        ConfigFixture.set(session, "cachedRodName", "default");
        ConfigFixture.set(session, "greenBarWidth", 0.3);
        ConfigFixture.set(session, "greenBarPos", 0.5);
        return session;
    }

    @Test
    void movementKeepsTheEntireGreenBarInsideBothEdges() throws Exception {
        GameSession session = session();
        Method update = GameSession.class.getDeclaredMethod("updateGreenBar");
        update.setAccessible(true);
        Field center = GameSession.class.getDeclaredField("greenBarPos");
        center.setAccessible(true);
        for (double start : new double[]{0.0, 1.0}) {
            ConfigFixture.set(session, "greenBarPos", start);
            ConfigFixture.set(session, "greenBarVel", start == 0 ? -0.05 : 0.05);
            update.invoke(session);
            assertTrue(center.getDouble(session) >= 0.15);
            assertTrue(center.getDouble(session) <= 0.85);
        }
        Field config = GameSession.class.getDeclaredField("config");
        config.setAccessible(true);
        ((me.kkfish.managers.Config) config.get(session)).getRodConfig().set("rods.default.float-area-size", 100);
        update.invoke(session);
        assertEquals(0.5, center.getDouble(session));
    }

    @Test
    void renderingKeepsExactlyTheSameWidthAtBothEdges() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        for (String field : new String[]{"green-bar-char", "green-bar-edge-char", "background-char", "fish-indicator-char"}) {
            fixture.config.getMainConfig().set("styles.default." + field, field.startsWith("green-bar") ? "G" : ".");
        }
        String[] title = new String[1];
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Player.class}, (proxy, method, args) -> {
            if (method.getName().equals("sendTitle")) title[0] = (String) args[0];
            return null;
        });
        MinigameRenderer renderer = new MinigameRenderer(fixture.plugin, player, WaterType.WATER);
        for (double center : new double[]{0.0, 0.15, 0.85, 1.0}) {
            renderer.render(center, 0.3, 0.5, 0.5);
            assertEquals(9, title[0].chars().filter(c -> c == 'G').count(), title[0]);
            assertEquals(32, title[0].length());
        }
        renderer.render(0.5, 1.0, 0.5, 0.5);
        assertEquals(29, title[0].chars().filter(c -> c == 'G').count());
        renderer.render(0.5, 0.0, 0.5, 0.5);
        assertEquals(0, title[0].chars().filter(c -> c == 'G').count());
    }

    @Test
    void gracePeriodDoesNotLoseProgressOutsideTheGreenBar() throws Exception {
        GameSession session = session();
        FishMovementSimulator movement = ConfigFixture.allocate(FishMovementSimulator.class);
        ConfigFixture.set(movement, "fishPos", 0.9);
        ConfigFixture.set(session, "movementSimulator", movement);
        ConfigFixture.set(session, "difficulty", 1.0);
        ConfigFixture.set(session, "fishLevel", "common");
        ConfigFixture.set(session, "progress", 0.5);
        ConfigFixture.set(session, "invincibleTicks", 1);
        Method update = GameSession.class.getDeclaredMethod("updateProgress");
        update.setAccessible(true);
        Field progress = GameSession.class.getDeclaredField("progress");
        progress.setAccessible(true);
        update.invoke(session);
        assertEquals(0.5, progress.getDouble(session));
        update.invoke(session);
        assertEquals(0.49, progress.getDouble(session), 0.000001);
    }

    @Test
    void gracePeriodStillAllowsProgressWhenFishIsInside() throws Exception {
        GameSession session = session();
        FishMovementSimulator movement = ConfigFixture.allocate(FishMovementSimulator.class);
        ConfigFixture.set(movement, "fishPos", 0.5);
        ConfigFixture.set(session, "movementSimulator", movement);
        ConfigFixture.set(session, "difficulty", 1.0);
        ConfigFixture.set(session, "fishLevel", "common");
        ConfigFixture.set(session, "progress", 0.5);
        ConfigFixture.set(session, "invincibleTicks", 1);
        Method update = GameSession.class.getDeclaredMethod("updateProgress");
        update.setAccessible(true);
        update.invoke(session);
        Field progress = GameSession.class.getDeclaredField("progress");
        progress.setAccessible(true);
        assertEquals(0.515, progress.getDouble(session), 0.000001);
    }
}
