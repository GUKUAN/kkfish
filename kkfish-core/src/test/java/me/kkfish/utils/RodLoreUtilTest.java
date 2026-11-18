package me.kkfish.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RodLoreUtilTest {

    @Test
    void removesRowsForUnconfiguredStats() {
        String template = "bite: %bite_rate_bonus%\nlure: %lure_speed%\nsize: %size_multiplier%\neffects: %effects%";

        assertEquals("bite: %bite_rate_bonus%\neffects: %effects%",
                RodLoreUtil.filterUnconfiguredStats(template, false, false));
    }

    @Test
    void keepsRowsOnlyForConfiguredStats() {
        String template = "lure: %lure_speed%\nsize: %size_multiplier%";

        assertEquals("lure: %lure_speed%",
                RodLoreUtil.filterUnconfiguredStats(template, true, false));
    }

    @Test
    void doesNotInjectMissingRowsWithoutEffectiveBonuses() {
        String template = "bite: %bite_rate_bonus%\neffects: %effects%";

        assertEquals(template, RodLoreUtil.injectMissingStats(template, false, false,
                "lure: +10%", "size: x1.20"));
    }

    @Test
    void injectsOnlyConfiguredRowsIntoLegacyTemplates() {
        String template = "bite: %bite_rate_bonus%\neffects: %effects%";

        assertEquals("bite: %bite_rate_bonus%\nlure: +10%\neffects: %effects%",
                RodLoreUtil.injectMissingStats(template, true, false,
                        "lure: +10%", "size: x1.20"));
    }

    @Test
    void configuredZeroLureBonusDoesNotCreateLoreRow() {
        assertEquals("effects: %effects%", RodLoreUtil.filterUnconfiguredStats(
                "lure: %lure_speed%\neffects: %effects%", false, false));
    }

    @Test
    void formatsRareFishChanceAsPercentageIncludingZero() {
        assertEquals("+30.0%", RodLoreUtil.formatRareFishChance(0.3));
        assertEquals("0.0%", RodLoreUtil.formatRareFishChance(0.0));
    }
}
