package me.kkfish.misc.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FishActivityTest {

    @Test
    void scalesBehaviorIntervalsInverselyWithActivity() {
        assertEquals(30, FishMovementSimulator.scaleIntervalByActivity(30, 1.0));
        assertEquals(15, FishMovementSimulator.scaleIntervalByActivity(30, 2.0));
        assertEquals(60, FishMovementSimulator.scaleIntervalByActivity(30, 0.5));
    }

    @Test
    void keepsScaledIntervalAtLeastOneTick() {
        assertEquals(1, FishMovementSimulator.scaleIntervalByActivity(1, 10.0));
    }

    @Test
    void scalesIntervalsAtConfiguredActivityBounds() {
        assertEquals(3, FishMovementSimulator.scaleIntervalByActivity(30, 10.0));
        assertEquals(300, FishMovementSimulator.scaleIntervalByActivity(30, 0.1));
    }
}
