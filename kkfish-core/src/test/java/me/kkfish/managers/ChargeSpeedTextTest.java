package me.kkfish.managers;

import me.kkfish.ConfigFixture;
import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class ChargeSpeedTextTest {
    @Test
    void chargeSpeedPlaceholderIsNumericForAllMultipliers() throws Exception {
        Config config = new ConfigFixture().config;
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            for (double speed : new double[]{2.0, 1.0, 0.5}) {
                config.getRodConfig().set("rods.default.charge-speed", speed);
                assertEquals(Double.toString(speed), config.getRodChargeSpeedText("default"));
            }
        } finally {
            Locale.setDefault(previous);
        }
    }
}
