package me.kkfish.handlers;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuraSkillsTest {

    private interface Skill {
    }

    private enum Skills implements Skill {
        FISHING
    }

    private static class LegacySkillsUser {
        public int getSkillLevel(Skills skill) {
            return 7;
        }

        public void addSkillXp(Skills skill, double amount) {
        }
    }

    private static class SkillsUser extends LegacySkillsUser {
        private double addedXp;

        public int getSkillLevel(Skill skill) {
            return 12;
        }

        public void addSkillXp(Skill skill, double amount) {
            addedXp = amount;
        }

        public void addSkillXp(Skill skill, double amount, Object source) {
            addedXp = -1;
        }
    }

    @Test
    void invokesAuraSkillsMethodsUsingSkillInterfaceParameters() throws Exception {
        Method levelMethod = AuraSkills.findSkillMethod(SkillsUser.class, "getSkillLevel", Skill.class);
        Method xpMethod = AuraSkills.findSkillMethod(SkillsUser.class, "addSkillXp", Skill.class, double.class);
        SkillsUser user = new SkillsUser();
        levelMethod.setAccessible(true);
        xpMethod.setAccessible(true);

        assertEquals(Skill.class, levelMethod.getParameterTypes()[0]);
        assertEquals(Skill.class, xpMethod.getParameterTypes()[0]);
        assertEquals(12, levelMethod.invoke(user, Skills.FISHING));
        xpMethod.invoke(user, Skills.FISHING, 7.5);
        assertEquals(7.5, user.addedXp);

        Method legacyMethod = AuraSkills.findSkillMethod(LegacySkillsUser.class, "getSkillLevel", Skills.class);
        assertEquals(7, legacyMethod.invoke(new LegacySkillsUser(), Skills.FISHING));
    }

    @Test
    void xpUsesTheFishSizeRangeMidpointAndConfiguredRarityMultiplier() {
        assertEquals(5.0, AuraSkills.calculateFishingXp(5.0, 100.0, 100.0, 200.0, 1.5));
        assertEquals(7.5, AuraSkills.calculateFishingXp(5.0, 150.0, 100.0, 200.0, 1.5));
        assertEquals(10.0, AuraSkills.calculateFishingXp(5.0, 200.0, 100.0, 200.0, 1.5));
        assertEquals(6.0, AuraSkills.calculateFishingXp(5.0, 25.0, 10.0, 40.0, 1.2));
    }

    @Test
    void rarityNameDropsWeightSuffixBeforeLookingUpMultiplier() {
        assertEquals("legendary", AuraSkills.getRarityNameByLevel("legendary:100"));
    }

    @Test
    void defaultsDefineBaseXpAndIndependentRarityMultipliers() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("config_packs/en_us/config.yml")) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));

            assertEquals(5.0, config.getDouble("fishing-settings.aura-skills-base-xp"));
            assertEquals(1.0, config.getDouble("fishing-settings.rarity.common.aura-skills-xp-multiplier"));
            assertEquals(1.2, config.getDouble("fishing-settings.rarity.uncommon.aura-skills-xp-multiplier"));
            assertEquals(1.5, config.getDouble("fishing-settings.rarity.rare.aura-skills-xp-multiplier"));
            assertEquals(3.0, config.getDouble("fishing-settings.rarity.epic.aura-skills-xp-multiplier"));
            assertEquals(5.0, config.getDouble("fishing-settings.rarity.legendary.aura-skills-xp-multiplier"));
        }
    }

}
