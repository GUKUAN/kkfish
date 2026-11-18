package me.kkfish.misc.minigame;

import me.kkfish.ConfigFixture;
import me.kkfish.fishing.WaterType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SelectorTest {
    @Test
    void canonicalBaitSizeDoesNotGetOverriddenByLegacyValue() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getBaitConfig().set("baits.test.effects", Arrays.asList("size"));
        fixture.config.getBaitConfig().set("baits.test.effect-values.size", 0.2);
        fixture.config.getBaitConfig().set("baits.test.effect", "size");
        fixture.config.getBaitConfig().set("baits.test.value", 0.8);
        FishSelector selector = new FishSelector(fixture.plugin, null, WaterType.WATER, "test", 0, new Random(1));
        assertEquals(1.2, selector.getSizeBonus());
    }

    @Test
    void rareBonusNeverCreatesAnUnconfiguredLevel() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getFishConfig().set("fish.test.level", Arrays.asList("common:100"));
        FishSelector selector = new FishSelector(fixture.plugin, null, WaterType.WATER, null, 1, new Random(1));
        for (int i = 0; i < 1000; i++) assertEquals("common", selector.selectRandomFishLevel("test"));
    }
}
