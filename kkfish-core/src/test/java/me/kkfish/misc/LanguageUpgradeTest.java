package me.kkfish.misc;

import me.kkfish.ConfigFixture;
import me.kkfish.kkfish;
import me.kkfish.managers.Config;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class LanguageUpgradeTest {
    @TempDir Path folder;

    public static class Resources extends kkfish {
        Config config;
        Path directory;

        @Override public Config getCustomConfig() { return config; }
        @Override public InputStream getResource(String name) {
            return getClass().getClassLoader().getResourceAsStream(name);
        }
        @Override public void saveResource(String name, boolean replace) {
            try (InputStream stream = getResource(name)) {
                if (stream == null) throw new IllegalArgumentException(name);
                Path file = directory.resolve(name);
                Files.createDirectories(file.getParent());
                if (replace || !Files.exists(file)) Files.copy(stream, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void upgradeUsesPackagedLanguageTemplatesAndPreservesCustomText() throws Exception {
        ConfigFixture fixture = new ConfigFixture();
        Resources plugin = ConfigFixture.allocate(Resources.class);
        plugin.config = fixture.config;
        plugin.directory = folder;
        Field data = JavaPlugin.class.getDeclaredField("dataFolder");
        data.setAccessible(true);
        data.set(plugin, folder.toFile());
        MessageManager messages = ConfigFixture.allocate(MessageManager.class);
        ConfigFixture.set(messages, "plugin", plugin);
        ConfigFixture.set(messages, "msgMap", new HashMap<>());
        ConfigFixture.set(messages, "prefix", "kkfish > ");
        ConfigFixture.set(messages, "currLang", "en_us");
        ConfigFixture.set(messages, "playerLangCache", new HashMap<>());

        Field instance = kkfish.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previousPlugin = instance.get(null);
        Field server = Bukkit.class.getDeclaredField("server");
        server.setAccessible(true);
        Object previousServer = server.get(null);
        ConsoleCommandSender console = (ConsoleCommandSender) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{ConsoleCommandSender.class}, (proxy, method, args) -> null);
        Server stub = (Server) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Server.class},
                (proxy, method, args) -> method.getName().equals("getConsoleSender") ? console : null);
        Field messageField = kkfish.class.getDeclaredField("messageManager");
        messageField.setAccessible(true);
        messageField.set(plugin, messages);
        instance.set(null, plugin);
        server.set(null, stub);
        try {
            Files.createDirectories(folder.resolve("lang"));
            for (String language : new String[]{"en_us", "zh_cn"}) {
                Path file = folder.resolve("lang/message_" + language + ".yml");
                YamlConfiguration old = new YamlConfiguration();
                old.set("prefix", "自定义前缀 > ");
                old.set("catch_success", "自定义捕获提示");
                old.save(file.toFile());
            }
            messages.completeAllLanguageFiles();
            for (String language : new String[]{"en_us", "zh_cn"}) {
                YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(folder.resolve("lang/message_" + language + ".yml").toFile());
                try (InputStream stream = plugin.getResource("lang/message_" + language + ".yml")) {
                    YamlConfiguration template = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    assertEquals(template.getString("no_available_fish"), upgraded.getString("no_available_fish"));
                    assertEquals(template.getString("minigame_start_failed"), upgraded.getString("minigame_start_failed"));
                }
                assertEquals("自定义前缀 > ", upgraded.getString("prefix"));
                assertEquals("自定义捕获提示", upgraded.getString("catch_success"));
                assertTrue(messages.completeLanguageFile(language));
                fixture.config.getMainConfig().set("language.current", language);
                messages.loadMessages();
                assertFalse(messages.getMessage("no_available_fish", "缺失提示").contains("缺失提示"));
            }

            Path chinese = folder.resolve("lang/message_zh_cn.yml");
            YamlConfiguration migrated = YamlConfiguration.loadConfiguration(chinese.toFile());
            migrated.set("aura_skills_added_xp", "已为 %s 添加 %.1f 点钓鱼经验");
            migrated.save(chinese.toFile());
            assertTrue(messages.completeLanguageFile("zh_cn"));
            assertEquals("已为 %2$s 添加 %1$.1f 点钓鱼经验", YamlConfiguration.loadConfiguration(chinese.toFile()).getString("aura_skills_added_xp"));
        } finally {
            instance.set(null, previousPlugin);
            server.set(null, previousServer);
        }
    }
}
