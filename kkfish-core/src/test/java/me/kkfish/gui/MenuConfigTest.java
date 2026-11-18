package me.kkfish.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuConfigTest {
    @Test
    void explicitEmptyActionsDoNotEnableLegacyCommands() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("items.test.left_click_actions", Collections.emptyList());
        config.set("items.test.left_click_commands", Arrays.asList("console: say old"));
        GUIMenuLoader.MenuConfig menu = new GUIMenuLoader.MenuConfig("test", config);
        assertTrue(menu.getItem("test").getLeftClickActions().isEmpty());
    }
}
