package me.kkfish.misc.minigame;

import me.kkfish.ConfigFixture;
import me.kkfish.fishing.WaterType;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 边缘对齐回归：鱼和绿条贴边时，画面、判定、进度必须一致。
 */
class EdgeAlignmentTest {

    @Test
    void fishAtBothFieldEndsCanBeHeldByTheClampedBar() {
        // 30% 杆：左右两端都要能命中
        assertTrue(MinigameField.inBar(0.0, 0.15, 0.3));
        assertTrue(MinigameField.inBar(1.0, 0.85, 0.3));
        // 5% 窄杆：最右端也要能命中
        assertTrue(MinigameField.inBar(0.0, 0.025, 0.05));
        assertTrue(MinigameField.inBar(1.0, 0.975, 0.05));
        // 绿条不在旁边时仍然不算命中
        assertFalse(MinigameField.inBar(0.0, 0.5, 0.3));
        assertFalse(MinigameField.inBar(1.0, 0.5, 0.3));
    }

    @Test
    void progressGrowsWhenFishSitsOnEitherEdge() throws Exception {
        assertEquals(0.515, progressAfterEdgeTick(0.15, 0.0), 0.000001);
        assertEquals(0.515, progressAfterEdgeTick(0.85, 1.0), 0.000001);
    }

    @Test
    void fishBlockTouchingTheBarCountsAsInside() {
        // 3 格宽的鱼块只要有一格压到绿条就算命中
        assertTrue(MinigameField.inBar(1.0, 0.8, 0.3, 3));
        assertTrue(MinigameField.inBar(0.0, 0.2, 0.3, 3));
        assertFalse(MinigameField.inBar(1.0, 0.15, 0.3, 3));
        assertFalse(MinigameField.inBar(0.0, 0.85, 0.3, 3));
    }

    @Test
    void progressGrowsWhenTheThreeCellFishBlockTouchesEitherEdge() throws Exception {
        // 0.8 是鱼块压住绿条右端、但鱼中心格已经落在绿条外的位置
        assertEquals(0.515, progressAfterEdgeTick(0.8, 1.0, 3), 0.000001);
        assertEquals(0.515, progressAfterEdgeTick(0.2, 0.0, 3), 0.000001);
    }

    @Test
    void renderedFishCellAgreesWithTheHitTest() throws Exception {
        for (double center : new double[]{0.0, 0.15, 0.5, 0.85, 1.0}) {
            int[] bar = expectedBarRange(center);
            for (double fish : new double[]{0.0, 0.05, 0.1, 0.5, 0.9, 0.95, 1.0}) {
                int fishCell = fishCellOnScreen(center, fish);
                boolean drawnInside = fishCell >= bar[0] && fishCell <= bar[1];
                assertEquals(MinigameField.inBar(fish, center, 0.3), drawnInside,
                        "center=" + center + " fish=" + fish + " bar=" + bar[0] + ".." + bar[1] + " cell=" + fishCell);
                assertEquals(MinigameField.fishCell(fish), fishCell,
                        "渲染落格和判定落格不一致: center=" + center + " fish=" + fish);
            }
        }
    }

    @Test
    void threeCharFishIndicatorNeverSpillsOutOfTheBracket() throws Exception {
        for (double fish : new double[]{0.0, 0.5, 1.0}) {
            String title = render(0.15 + fish * 0.7, fish, ".", "&9|||");
            String visible = ChatColor.stripColor(title);
            assertEquals(MinigameField.CELLS + 2, visible.length(), visible);
            assertTrue(visible.endsWith("]"), visible);
        }
    }

    @Test
    void renderedThreeCellFishBlockMatchesTheHitTestAtTheRightEdge() throws Exception {
        String visible = ChatColor.stripColor(render(0.8, 1.0, ".", "&9|||"));
        int fishStart = visible.indexOf("|||") - 1;
        int[] bar = expectedBarRange(0.8);
        assertTrue(fishStart <= bar[1] && fishStart + 2 >= bar[0], visible);
        assertTrue(MinigameField.inBar(1.0, 0.8, 0.3, 3), visible);
    }

    @Test
    void renderedThreeCellFishBlockMatchesTheHitTestAtTheLeftEdge() throws Exception {
        String visible = ChatColor.stripColor(render(0.2, 0.0, ".", "&9|||"));
        int fishStart = visible.indexOf("|||") - 1;
        int[] bar = expectedBarRange(0.2);
        assertTrue(fishStart <= bar[1] && fishStart + 2 >= bar[0], visible);
        assertTrue(MinigameField.inBar(0.0, 0.2, 0.3, 3), visible);
    }

    @Test
    void escapeFromRightEdgeTurnsAroundInsteadOfStayingPut() throws Exception {
        FishMovementSimulator movement = edgeSimulator(1.0, 10);
        int target = escape(movement, 10, 1, 0.85);
        assertTrue(target < 10, "鱼卡在右边缘没有回头，target=" + target);
    }

    @Test
    void escapeFromLeftEdgeTurnsAroundInsteadOfStayingPut() throws Exception {
        FishMovementSimulator movement = edgeSimulator(0.0, 0);
        int target = escape(movement, 0, 1, 0.15);
        assertTrue(target > 0, "鱼卡在左边缘没有回头，target=" + target);
    }

    private double progressAfterEdgeTick(double center, double fishPos) throws Exception {
        return progressAfterEdgeTick(center, fishPos, 1);
    }

    private double progressAfterEdgeTick(double center, double fishPos, int fishIndicatorWidth) throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getRodConfig().set("rods.default.float-area-size", 30);
        fixture.config.getMainConfig().set("fishing-settings.progress-bar.increase-speed", 0.015);
        fixture.config.getMainConfig().set("fishing-settings.progress-bar.decrease-speed", 0.01);
        fixture.config.getMainConfig().set("fishing-settings.progress-bar.rarity-impact.enabled", false);

        GameSession session = ConfigFixture.allocate(GameSession.class);
        ConfigFixture.set(session, "config", fixture.config);
        ConfigFixture.set(session, "cachedRodName", "default");
        ConfigFixture.set(session, "greenBarWidth", 0.3);
        ConfigFixture.set(session, "greenBarPos", center);
        ConfigFixture.set(session, "fishIndicatorWidth", fishIndicatorWidth);
        ConfigFixture.set(session, "difficulty", 1.0);
        ConfigFixture.set(session, "fishLevel", "common");
        ConfigFixture.set(session, "progress", 0.5);
        ConfigFixture.set(session, "invincibleTicks", 0);

        FishMovementSimulator movement = ConfigFixture.allocate(FishMovementSimulator.class);
        ConfigFixture.set(movement, "fishPos", fishPos);
        ConfigFixture.set(session, "movementSimulator", movement);

        Method update = GameSession.class.getDeclaredMethod("updateProgress");
        update.setAccessible(true);
        update.invoke(session);

        Field progress = GameSession.class.getDeclaredField("progress");
        progress.setAccessible(true);
        return progress.getDouble(session);
    }

    /** 30 格、30% 宽的绿条按规格应该画在哪几格（独立于被测工具类手算）。 */
    private int[] expectedBarRange(double center) {
        int length = 9;
        int left = (int) Math.round(center * MinigameField.CELLS - length / 2.0);
        left = Math.max(0, Math.min(MinigameField.CELLS - length, left));
        return new int[]{left, left + length - 1};
    }

    /** 用 "F" 画一遍，读鱼指示符实际落在哪一格。 */
    private int fishCellOnScreen(double center, double fish) throws Exception {
        String title = render(center, fish, ".", "F");
        return title.indexOf('F') - 1;
    }

    private String render(double center, double fish, String backgroundChar, String fishChar) throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getMainConfig().set("styles.default.green-bar-char", "G");
        fixture.config.getMainConfig().set("styles.default.green-bar-edge-char", "G");
        fixture.config.getMainConfig().set("styles.default.background-char", backgroundChar);
        fixture.config.getMainConfig().set("styles.default.fish-indicator-char", fishChar);

        String[] title = new String[1];
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("sendTitle")) {
                        title[0] = (String) args[0];
                    }
                    return null;
                });
        new MinigameRenderer(fixture.plugin, player, WaterType.WATER).render(center, 0.3, fish, 0.5);
        return title[0];
    }

    private FishMovementSimulator edgeSimulator(double fishPos, int gridPos) throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        FishMovementSimulator movement = ConfigFixture.allocate(FishMovementSimulator.class);
        ConfigFixture.set(movement, "plugin", fixture.plugin);
        ConfigFixture.set(movement, "config", fixture.config);
        ConfigFixture.set(movement, "random", new Random(7));
        ConfigFixture.set(movement, "targetFish", "test");
        ConfigFixture.set(movement, "movementAmplitude", 1.0);
        ConfigFixture.set(movement, "fishPos", fishPos);
        ConfigFixture.set(movement, "targetPos", gridPos);
        ConfigFixture.set(movement, "dangerZone", new double[11]);
        return movement;
    }

    private int escape(FishMovementSimulator movement, int gridPos, int rarity, double barCenter) throws Exception {
        Method method = FishMovementSimulator.class.getDeclaredMethod("escapeFromGreenBar", int.class, int.class, double.class);
        method.setAccessible(true);
        method.invoke(movement, gridPos, rarity, barCenter);

        Field targetPos = FishMovementSimulator.class.getDeclaredField("targetPos");
        targetPos.setAccessible(true);
        return targetPos.getInt(movement);
    }
}
