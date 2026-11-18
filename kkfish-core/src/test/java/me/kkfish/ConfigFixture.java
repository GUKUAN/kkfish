package me.kkfish;

import me.kkfish.managers.Config;
import me.kkfish.fishing.WaterType;
import org.bukkit.configuration.file.YamlConfiguration;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.util.EnumMap;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigFixture {
    public final Config config;
    public final kkfish plugin;

    public ConfigFixture() throws Exception {
        // Bukkit 插件构造器要求服务器类加载器；这里只隔离启动，解析仍调用真实配置方法。
        plugin = allocate(kkfish.class);
        config = allocate(Config.class);
        set(config, "plugin", plugin);
        set(plugin, "config", config);
        for (String name : new String[]{"mainConfig", "fishConfig", "rodConfig", "baitConfig", "hookConfig", "poolConfig"}) {
            set(config, name, new YamlConfiguration());
        }
        set(config, "validPoolFishCache", new EnumMap<>(WaterType.class));
        set(config, "hookPathCache", new ConcurrentHashMap<>());
    }

    public static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    public static <T> T allocate(Class<T> type) throws Exception {
        Class<?> allocatorClass = Class.forName("sun.misc.Unsafe");
        Field field = allocatorClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(allocatorClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }

    public void loadPack(String language) throws Exception {
        String[][] files = {{"mainConfig", "config.yml"}, {"fishConfig", "fish.yml"}, {"rodConfig", "rods.yml"}};
        for (String[] file : files) {
            try (java.io.InputStream stream = getClass().getClassLoader().getResourceAsStream("config_packs/" + language + "/" + file[1])) {
                set(config, file[0], YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
            }
        }
    }
}
