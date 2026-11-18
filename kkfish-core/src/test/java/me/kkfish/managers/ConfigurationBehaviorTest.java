package me.kkfish.managers;

import me.kkfish.ConfigFixture;
import me.kkfish.fishing.WaterType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationBehaviorTest {
    @Test
    void fishActivityDefaultsToOneAndClampsValidValues() throws Exception {
        Config config = new ConfigFixture().config;

        assertEquals(1.0, config.getFishActivity("test"));
        config.getFishConfig().set("fish.test.activity", 2.5);
        assertEquals(2.5, config.getFishActivity("test"));
        config.getFishConfig().set("fish.test.activity", 0.01);
        assertEquals(0.1, config.getFishActivity("test"));
        config.getFishConfig().set("fish.test.activity", 50.0);
        assertEquals(10.0, config.getFishActivity("test"));
    }

    @Test
    void fishActivityFallsBackForNonPositiveAndNonFiniteValues() throws Exception {
        Config config = new ConfigFixture().config;

        for (double activity : new double[]{0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            config.getFishConfig().set("fish.test.activity", activity);
            assertEquals(1.0, config.getFishActivity("test"));
        }
    }

    @Test
    void inheritedPriceKeepsExplicitDisabledParent() throws Exception {
        Config config = new ConfigFixture().config;
        config.getRodConfig().set("rods.default.price.vault", 100.0);
        config.getRodConfig().set("rods.custom.price", false);
        assertEquals("rods.custom.price.vault", config.getRodPropertyPath("custom", "price.vault"));
    }

    @Test
    void publicFishValueCalculationUsesConfiguredRarityMultiplier() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getFishConfig().set("fish.test.value", 10);
        fixture.config.getFishConfig().set("fish.test.max-size", 100);
        fixture.config.getMainConfig().set("fishing-settings.rarity.legendary.value-multiplier", 7.0);
        FishItemFactory factory = new FishItemFactory(fixture.plugin, fixture.config, null, new Random(1));
        assertEquals(35.0, factory.calculateFishValue("test", 50, 5));
    }

    @Test
    void zeroWeightTableCannotInventCommonFish() throws Exception {
        Config config = new ConfigFixture().config;
        config.getFishConfig().set("fish.test.level", Arrays.asList("legendary:0"));
        assertNull(config.getRandomFishLevel("test"));
        assertTrue(config.getAvailableFish("RIVER", "CLEAR", 0, null).isEmpty());
    }

    @Test
    void fractionalGlobalWeightsRemainEffective() throws Exception {
        Config config = new ConfigFixture().config;
        config.getFishConfig().set("fish.test.level", Arrays.asList("legendary:1"));
        config.getMainConfig().set("fishing-settings.rarity.legendary.weight", 0.5);
        assertEquals("legendary", config.getRandomFishLevel("test"));
    }

    @Test
    void defaultLevelStringsAreWeightedAndReturnIds() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.loadPack("en_us");
        Set<String> levels = new HashSet<>();
        for (int i = 0; i < 2000; i++) levels.add(fixture.config.getRandomFishLevel("Carp"));
        assertEquals(new HashSet<>(Arrays.asList("common", "rare", "epic")), levels);
        assertEquals(5, fixture.config.getFishRarity("DragonFish"));
        assertEquals(3, fixture.config.getFishRarity("Goldfish"));
    }

    @Test
    void rarityBonusChangesWeightsWithoutRevivingZeroEntries() throws Exception {
        Config config = new ConfigFixture().config;
        config.getFishConfig().set("fish.test.level", Arrays.asList("common:1", "rare:1", "legendary:0"));
        config.getMainConfig().set("fishing-settings.rarity.common.weight", 1);
        config.getMainConfig().set("fishing-settings.rarity.rare.weight", 1);
        int rare = 0;
        Random random = new Random(123);
        for (int i = 0; i < 10000; i++) {
            String level = config.getRandomFishLevel("test", null, 3, random);
            assertNotEquals("legendary", level);
            if ("rare".equals(level)) rare++;
        }
        assertEquals(0.75, rare / 10000.0, 0.02);
    }

    @Test
    void zeroWeightDoesNotDetermineFishRarity() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getFishConfig().set("fish.test.level", Arrays.asList(Collections.singletonMap("common", 0), Collections.singletonMap("legendary", 100)));
        assertEquals(5, fixture.config.getFishRarity("test"));
        assertEquals("legendary", fixture.config.getRandomFishLevel("test"));
    }

    @Test
    void rodsInheritMissingFieldsButKeepExplicitZeroAndEmptyList() throws Exception {
        Config config = new ConfigFixture().config;
        config.getRodConfig().set("rods.default.charge-speed", 3.0);
        config.getRodConfig().set("rods.default.float-area-size", 42);
        config.getRodConfig().set("rods.default.bite-chance-bonus", 0.7);
        config.getRodConfig().set("rods.default.effects", Arrays.asList("SPEED 1:10"));
        config.getRodConfig().set("rods.custom.difficulty", 2.0);
        config.getRodConfig().set("rods.custom.bite-chance-bonus", 0.0);
        config.getRodConfig().set("rods.custom.effects", Collections.emptyList());
        assertEquals(3.0, config.getRodChargeSpeed("custom"));
        assertEquals(42, config.getRodFloatAreaSize("custom"));
        assertEquals(2.0, config.getRodDifficulty("custom"));
        assertEquals(0.0, config.getRodBiteRateBonus("custom"));
        assertTrue(config.getRodEffects("custom").isEmpty());
    }

    @Test
    void legacyRodBonusWinsOverInheritedDefault() throws Exception {
        Config config = new ConfigFixture().config;
        config.getRodConfig().set("rods.default.bite-chance-bonus", 0.7);
        config.getRodConfig().set("rods.old.bite-rate-bonus", 0.2);
        assertEquals(0.2, config.getRodBiteRateBonus("old"));
    }

    @Test
    void legacyRareChanceKeyWinsOverInheritedValue() throws Exception {
        Config config = new ConfigFixture().config;
        config.getRodConfig().set("rods.default.rare-fish-chance", 0.7);
        config.getRodConfig().set("rods.old.carp-fish-chance", 0.2);
        assertEquals(0.2, config.getRodRareFishChance("old"));
        config.getRodConfig().set("rods.old.rare-fish-chance", 0.0);
        assertEquals(0.0, config.getRodRareFishChance("old"));
    }

    @Test
    void pagedHooksUseTheirSelectedTemplate() throws Exception {
        Config config = new ConfigFixture().config;
        YamlConfiguration hooks = new YamlConfiguration();
        hooks.set("hooks.1.custom.template", "compact");
        hooks.set("hooks.1.custom.effects.bite-rate-bonus", 0.8);
        ConfigFixture.set(config, "hookConfig", hooks);
        assertEquals("compact", config.getHookTemplateName("custom"));
        assertEquals(0.8, config.getHookBiteRateBonus("custom"));
    }

    @Test
    void bothLanguagesHaveIndependentRarityXpMultipliers() throws Exception {
        for (String language : Arrays.asList("en_us", "zh_cn")) {
            ConfigFixture fixture = new ConfigFixture();
            fixture.loadPack(language);
            assertEquals("uncommon", fixture.config.getRarityNameByLevel("uncommon"));
            assertEquals("mythic", fixture.config.getRarityNameByLevel("mythic"));
            assertEquals("legendary", fixture.config.getRarityNameByLevel("传说"));
            assertEquals(5.0, fixture.config.getRarityAuraSkillsXpMultiplier(fixture.config.getRandomFishLevel("DragonFish")));
        }
    }

    @Test
    void canonicalBaitFieldsWinAndOldSingleEffectStillWorks() throws Exception {
        Config config = new ConfigFixture().config;
        config.getBaitConfig().set("baits.test.effects", Arrays.asList("size"));
        config.getBaitConfig().set("baits.test.effect-values.size", 0.2);
        config.getBaitConfig().set("baits.test.effect", "size");
        config.getBaitConfig().set("baits.test.value", 0.8);
        assertEquals(0.2, config.getBaitEffectValueByName("test", "size"));
        config.getBaitConfig().set("baits.old.effect", "bite");
        config.getBaitConfig().set("baits.old.value", 0.4);
        assertEquals(0.4, config.getBaitEffectValueByName("old", "bite"));
        config.getBaitConfig().set("baits.old.effects", Collections.emptyList());
        assertTrue(config.getBaitEffects("old").isEmpty());
    }

    @Test
    void configuredEmptyOrFilteredPoolNeverFallsBack() throws Exception {
        Config config = new ConfigFixture().config;
        config.getFishConfig().set("fish.outside.value", 10);
        config.getFishConfig().set("fish.inside.biomes", Arrays.asList("OCEAN"));
        config.getPoolConfig().set("water", Collections.emptyList());
        assertTrue(config.getAvailableFishFromPool(WaterType.WATER, "RIVER", "CLEAR", 0, null).isEmpty());
        ConfigFixture.set(config, "validPoolFishCache", Collections.singletonMap(WaterType.WATER, Arrays.asList("inside")));
        assertTrue(config.getAvailableFishFromPool(WaterType.WATER, "RIVER", "CLEAR", 0, null).isEmpty());
    }

    @Test
    void seasonCaseAndAllWildcardsAreAccepted() throws Exception {
        Config config = new ConfigFixture().config;
        config.getFishConfig().set("fish.test.seasons", Arrays.asList("SPRING"));
        config.getFishConfig().set("fish.test.biomes", Arrays.asList("ALL"));
        config.getFishConfig().set("fish.test.weather", Arrays.asList("ALL"));
        assertEquals(Arrays.asList("test"), config.getAvailableFish("spring"));
        assertEquals(Arrays.asList("test"), config.getAvailableFish("RIVER", "THUNDER", 0, "spring"));
    }

    @Test
    void flatHooksResolvePropertiesAndTemplates() throws Exception {
        Config config = new ConfigFixture().config;
        YamlConfiguration hooks = new YamlConfiguration();
        hooks.set("hooks.custom.effects.bite-rate-bonus", 0.8);
        hooks.set("hooks.custom.template", "compact");
        ConfigFixture.set(config, "hookConfig", hooks);
        assertTrue(config.hookExists("custom"));
        assertEquals(0.8, config.getHookBiteRateBonus("custom"));
        assertEquals("compact", config.getHookTemplateName("custom"));
    }
}
