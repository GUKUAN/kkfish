package me.kkfish.misc.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FishSizeMultiplierTest {

    @Test
    void sizeMultipliersNeverBreakMaxSize() {
        assertEquals(60.0, GameSession.calculateFishSize(58.0, 1.5, 60.0), 1e-9);
    }

    @Test
    void sizeMultipliersApplyBelowMaxSize() {
        assertEquals(26.0, GameSession.calculateFishSize(20.0, 1.3, 60.0), 1e-9);
    }
}
