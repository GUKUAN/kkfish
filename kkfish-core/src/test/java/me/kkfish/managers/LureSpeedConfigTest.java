package me.kkfish.managers;

import java.util.Arrays;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import me.kkfish.ConfigFixture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LureSpeedConfigTest {

    @Test
    void rodLureSpeedFallsBackToDefaultRod() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        YamlConfiguration rods = (YamlConfiguration) fixture.config.getRodConfig();
        rods.set("rods.stone", new java.util.HashMap<String, Object>());
        rods.set("rods.default.lure-speed", 0.05);
        rods.set("rods.master.lure-speed", 0.2);

        assertEquals(0.2, fixture.config.getRodLureSpeed("master"), 1e-9);
        assertEquals(0.05, fixture.config.getRodLureSpeed("stone"), 1e-9);
    }

    @Test
    void rodLureSpeedRejectsNegativeValues() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        YamlConfiguration rods = (YamlConfiguration) fixture.config.getRodConfig();
        rods.set("rods.master.lure-speed", -0.5);

        assertEquals(0.0, fixture.config.getRodLureSpeed("master"), 1e-9);
    }

    @Test
    void rodSizeMultiplierFallsBackAndRejectsInvalidValues() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        YamlConfiguration rods = (YamlConfiguration) fixture.config.getRodConfig();
        rods.set("rods.stone", new java.util.HashMap<String, Object>());
        rods.set("rods.default.size-multiplier", 1.1);
        rods.set("rods.master.size-multiplier", 1.25);
        rods.set("rods.broken.size-multiplier", 0.0);

        assertEquals(1.25, fixture.config.getRodSizeMultiplier("master"), 1e-9);
        assertEquals(1.1, fixture.config.getRodSizeMultiplier("stone"), 1e-9);
        assertEquals(1.0, fixture.config.getRodSizeMultiplier("broken"), 1e-9);
    }

    @Test
    void lureSpeedAddsRodHookAndBait() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        YamlConfiguration rods = (YamlConfiguration) fixture.config.getRodConfig();
        YamlConfiguration hooks = new YamlConfiguration();
        YamlConfiguration baits = (YamlConfiguration) fixture.config.getBaitConfig();
        ConfigFixture.set(fixture.config, "hookConfig", hooks);

        rods.set("rods.master.lure-speed", 0.2);
        hooks.set("hooks.1.diamond.effects.lure-speed", 0.12);
        baits.set("baits.magic.effects", Arrays.asList("rare", "lure"));
        baits.set("baits.magic.effect-values.lure", 0.1);

        assertEquals(0.42, fixture.config.getLureSpeedBonus("master", "diamond", "magic"), 1e-9);
    }

    @Test
    void baitLureSpeedReadsSingleEffectFormat() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        YamlConfiguration baits = (YamlConfiguration) fixture.config.getBaitConfig();
        baits.set("baits.worm.effect", "lure");
        baits.set("baits.worm.value", 0.2);

        assertEquals(0.2, fixture.config.getBaitLureSpeed("worm"), 1e-9);
    }

    @Test
    void hookSizeMultiplierIsDisplayedOnlyWhenConfiguredAndEffective() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        YamlConfiguration hooks = new YamlConfiguration();
        ConfigFixture.set(fixture.config, "hookConfig", hooks);
        hooks.set("hooks.1.wood.effects.size-multiplier", 1.0);
        hooks.set("hooks.1.iron.effects.size-multiplier", 1.15);

        org.junit.jupiter.api.Assertions.assertFalse(fixture.config.shouldDisplayHookSizeMultiplier("diamond"));
        org.junit.jupiter.api.Assertions.assertFalse(fixture.config.shouldDisplayHookSizeMultiplier("wood"));
        org.junit.jupiter.api.Assertions.assertTrue(fixture.config.shouldDisplayHookSizeMultiplier("iron"));
    }
}
