package me.kkfish.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BiteCheckSchedulerTest {

    @Test
    void lureSpeedShortensDelay() {
        assertEquals(133, BiteCheckScheduler.applyLureSpeed(200, 0.5, 20));
    }

    @Test
    void lureSpeedCannotGoBelowMinimumDelay() {
        assertEquals(20, BiteCheckScheduler.applyLureSpeed(200, 100.0, 20));
    }

    @Test
    void zeroBonusKeepsDelay() {
        assertEquals(200, BiteCheckScheduler.applyLureSpeed(200, 0.0, 20));
    }

    @Test
    void normalDelayStillHonorsMinimum() {
        assertEquals(20, BiteCheckScheduler.applyLureSpeed(5, 0.0, 20));
    }
}
