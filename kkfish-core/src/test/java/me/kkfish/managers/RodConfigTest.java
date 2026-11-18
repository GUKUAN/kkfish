package me.kkfish.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RodConfigTest {

    @Test
    void readsBiteChanceBonusBeforeLegacyKey() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection rod = config.createSection("rods.master-fishing-rod");
        rod.set("bite-chance-bonus", 0.5);
        rod.set("bite-rate-bonus", 500.0);

        assertEquals(0.5, Config.getRodBiteRateBonus(rod));
    }

    @Test
    void readsLegacyBiteRateBonusWhenCanonicalKeyIsMissing() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection rod = config.createSection("rods.old-rod");
        rod.set("bite-rate-bonus", 0.75);

        assertEquals(0.75, Config.getRodBiteRateBonus(rod));
    }
}
