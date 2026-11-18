package me.kkfish.managers;

import me.kkfish.ConfigFixture;
import me.kkfish.kkfish;
import me.kkfish.handlers.AuraSkills;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ExperienceTest {
    public static class Skills extends AuraSkills {
        String level;
        public Skills(kkfish plugin) { super(plugin); }
        @Override public double addFishingExperience(Player player, double size, double min, double max, String level) {
            this.level = level;
            return 7.5;
        }
    }

    @Test
    void vanillaXpSwitchDoesNotDisableAuraSkills() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        fixture.config.getFishConfig().set("fish.test.exp", 10);
        fixture.config.getMainConfig().set("fishing-settings.enable-vanilla-exp", false);
        Skills skills = ConfigFixture.allocate(Skills.class);
        ConfigFixture.set(fixture.plugin, "auraSkills", skills);
        Fish fish = ConfigFixture.allocate(Fish.class);
        ConfigFixture.set(fish, "plugin", fixture.plugin);
        ConfigFixture.set(fish, "config", fixture.config);
        AtomicInteger xp = new AtomicInteger();
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (proxy, method, args) -> {
            if (method.getName().equals("giveExp")) xp.addAndGet((Integer) args[0]);
            return null;
        });
        assertEquals(7.5, fish.giveFishingExperience(player, "test", 10, 5, 15, "rare"));
        assertEquals(0, xp.get());
        assertEquals("rare", skills.level);
        fixture.config.getMainConfig().set("fishing-settings.enable-vanilla-exp", true);
        fish.giveFishingExperience(player, "test", 10, 5, 15, "rare");
        assertEquals(10, xp.get());
    }
}
