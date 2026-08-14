package me.kkfish.utils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigUpgradeTest {

    @Test
    void compareVersionsWorks() {
        assertTrue(ConfigUpgrade.compareVersions("1.7.10", "1.7.9") > 0);
        assertTrue(ConfigUpgrade.compareVersions("1.7.9", "1.7.10") < 0);
        assertEquals(0, ConfigUpgrade.compareVersions("1.7.10", "1.7.10"));
        assertTrue(ConfigUpgrade.compareVersions("1.7.10", "1.7.10.1") < 0);
        assertEquals(0, ConfigUpgrade.compareVersions("1.7", "1.7.0"));
    }

    @Test
    void fillMissingKeysAddsOnlyMissing() {
        YamlConfiguration target = new YamlConfiguration();
        target.set("existing", "keep-me");
        YamlConfiguration template = new YamlConfiguration();
        template.set("existing", "template-value");
        template.set("new-key", 42);
        template.set("nested.sub", "hello");

        int added = ConfigUpgrade.fillMissingKeys(target, template);

        assertEquals(2, added);
        assertEquals("keep-me", target.getString("existing"));
        assertEquals(42, target.getInt("new-key"));
        assertEquals("hello", target.getString("nested.sub"));
    }

    @Test
    void upgradeMigratesUnversionedConfig() {
        YamlConfiguration target = new YamlConfiguration();
        target.set("fishing-settings.max-charge-time", 999);
        YamlConfiguration template = new YamlConfiguration();
        template.set("fishing-settings.max-charge-time", 3000);
        template.set("fishing-settings.cast-cooldown", 5000);
        template.set("bstats.enabled", true);

        int added = ConfigUpgrade.upgrade(target, template, "1.7.10");

        // 迁移规则（language 推断）+ 缺失键补齐 + 版本写入
        assertTrue(added >= 3);
        assertEquals(999, target.getInt("fishing-settings.max-charge-time"));
        assertEquals(5000, target.getInt("fishing-settings.cast-cooldown"));
        assertTrue(target.contains("language.current"));
        assertEquals("1.7.10", target.getString(ConfigUpgrade.VERSION_KEY));
    }

    @Test
    void upgradeKeepsExistingValues() {
        YamlConfiguration target = new YamlConfiguration();
        target.set(ConfigUpgrade.VERSION_KEY, "1.7.10");
        target.set("language.current", "zh_cn");
        YamlConfiguration template = new YamlConfiguration();
        template.set("language.current", "en_us");
        template.set("debug.enabled", false);

        int added = ConfigUpgrade.upgrade(target, template, "1.7.10");

        assertEquals(1, added);
        assertEquals("zh_cn", target.getString("language.current"));
        assertEquals("1.7.10", target.getString(ConfigUpgrade.VERSION_KEY));
    }

    @Test
    void upgradeSkipsMigrationsWhenVersionIsCurrent() {
        YamlConfiguration target = new YamlConfiguration();
        target.set(ConfigUpgrade.VERSION_KEY, "1.7.10");
        target.set("language.current", "en_us");
        YamlConfiguration template = new YamlConfiguration();
        template.set("debug.enabled", true);

        int added = ConfigUpgrade.upgrade(target, template, "1.7.10");

        assertEquals(1, added);
        assertEquals("en_us", target.getString("language.current"));
    }
}