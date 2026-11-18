package me.kkfish.misc.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreenBarWidthTest {

    @Test
    void usesConfiguredFloatAreaAsPercentage() {
        assertEquals(0.3, GameSession.getGreenBarWidth(30));
        assertEquals(0.01, GameSession.getGreenBarWidth(1));
    }

    @Test
    void clampsFloatAreaPercentageToZeroThroughOneHundred() {
        assertEquals(0.0, GameSession.getGreenBarWidth(-10));
        assertEquals(0.0, GameSession.getGreenBarWidth(0));
        assertEquals(1.0, GameSession.getGreenBarWidth(100));
        assertEquals(1.0, GameSession.getGreenBarWidth(10000));
    }
}
