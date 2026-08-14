package me.kkfish.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 配置版本升级工具
 * 以语言包模板为基准，对比 config-version 执行版本迁移与缺失键补齐
 * 迁移规则按版本引入点登记，配置版本早于引入点的规则才会执行
 */
public class ConfigUpgrade {

    public static final String VERSION_KEY = "config-version";
    public static final String UNVERSIONED = "0.0.0";

    private static final List<String> SUPPORTED_LANGS = Arrays.asList("zh_cn", "en_us");

    private static final List<Migration> MIGRATIONS = new ArrayList<>();

    static {
        // language 段从 1.7.0 起存在：无版本号时代升级上来的配置缺少它时，
        // 按 JVM locale 推断语言（模板默认值不适用于推断场景）
        MIGRATIONS.add(new Migration("1.7.0", cfg -> {
            if (!cfg.contains("language")) {
                String serverLocale = Locale.getDefault().toString().toLowerCase();
                if (serverLocale.length() >= 5) {
                    serverLocale = serverLocale.substring(0, 5);
                }

                String defaultLanguage = "en_us";
                for (String lang : SUPPORTED_LANGS) {
                    if (serverLocale.startsWith(lang.substring(0, 2))) {
                        defaultLanguage = lang;
                        break;
                    }
                }

                cfg.set("language.current", defaultLanguage);
                return 1;
            }
            return 0;
        }));
    }

    private ConfigUpgrade() {
    }

    /**
     * 升级主配置：先按版本执行迁移规则，再以模板为基准补齐缺失键，最后写入当前版本号
     *
     * @param target         现有配置对象（会被原地修改）
     * @param template       当前语言包的模板 config.yml（已加载），null 则跳过补齐
     * @param currentVersion 插件当前版本号
     * @return 本次新增的键数量
     */
    public static int upgrade(FileConfiguration target, FileConfiguration template, String currentVersion) {
        String oldVersion = target.getString(VERSION_KEY, UNVERSIONED);
        int added = 0;

        if (compareVersions(oldVersion, currentVersion) < 0) {
            for (Migration m : MIGRATIONS) {
                if (compareVersions(oldVersion, m.introducedIn) < 0) {
                    added += m.apply(target);
                }
            }
        }

        if (template != null) {
            added += fillMissingKeys(target, template);
        }

        target.set(VERSION_KEY, currentVersion);
        return added;
    }

    /**
     * 以模板为基准，把缺失的键补进目标配置（不覆盖已有值）
     */
    public static int fillMissingKeys(FileConfiguration target, FileConfiguration template) {
        int added = 0;
        for (String key : template.getKeys(true)) {
            if (!target.contains(key)) {
                target.set(key, template.get(key));
                added++;
            }
        }
        return added;
    }

    /**
     * 比较两个版本号，a < b 返回负数，相等返回 0，a > b 返回正数
     */
    public static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? parseInt(pa[i]) : 0;
            int vb = i < pb.length ? parseInt(pb[i]) : 0;
            if (va != vb) {
                return va - vb;
            }
        }
        return 0;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 一条版本迁移规则：introducedIn 表示该规则随哪个版本引入，
     * 配置版本早于 introducedIn 时执行 apply，按配置实际状态决定是否改动
     */
    private static class Migration {
        final String introducedIn;
        final java.util.function.Function<FileConfiguration, Integer> action;

        Migration(String introducedIn, java.util.function.Function<FileConfiguration, Integer> action) {
            this.introducedIn = introducedIn;
            this.action = action;
        }

        int apply(FileConfiguration cfg) {
            return action.apply(cfg);
        }
    }
}
