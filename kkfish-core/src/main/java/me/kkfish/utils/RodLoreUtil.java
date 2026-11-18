package me.kkfish.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.ChatColor;

import me.kkfish.integrations.CustomItemHook;
import me.kkfish.managers.Config;
import me.kkfish.misc.MessageManager;

/**
 * 鱼竿物品 lore 构建。
 * 合成、耐久刷新、give 指令三处原本各拼一套，这里统一模板替换。
 */
public final class RodLoreUtil {

    private RodLoreUtil() {
    }

    /**
     * 按鱼竿模板生成描述。
     */
    public static List<String> build(Config config, MessageManager messageManager, String rodName,
                                     String displayName, String durabilityText) {
        String template = config.getRodTemplate(config.getRodTemplateName(rodName));
        if (template == null || template.isEmpty()) {
            template = "";
        }

        double lureSpeed = config.getRodLureSpeed(rodName);
        double sizeMultiplier = config.getRodSizeMultiplier(rodName);

        Map<String, String> variables = new HashMap<>();
        variables.put("%name%", displayName);
        variables.put("%difficulty%", String.valueOf(config.getRodDifficulty(rodName)));
        variables.put("%float_area%", config.getRodFloatAreaSize(rodName) + "%");
        variables.put("%durability%", durabilityText);
        variables.put("%charge_speed%", config.getRodChargeSpeedText(rodName));
        variables.put("%bite_rate_bonus%", formatBonus(config.getRodBiteRateBonus(rodName), messageManager));
        variables.put("%rare_fish_chance%", formatRareFishChance(config.getRodRareFishChance(rodName)));
        variables.put("%lure_speed%", formatBonus(lureSpeed, messageManager));
        variables.put("%size_multiplier%", String.format(Locale.ROOT, "x%.2f", sizeMultiplier));
        variables.put("%effects%", buildEffects(config, messageManager, rodName));
        variables.put("%rod_durability%", messageManager.getMessageWithoutPrefix("rod_durability", "Durability"));

        boolean showLureSpeed = lureSpeed > 0;
        boolean showSizeMultiplier = Math.abs(sizeMultiplier - 1.0) > 0.0001;
        if (template.isEmpty()) {
            template = buildDefaultTemplate(messageManager, showLureSpeed, showSizeMultiplier);
        }
        String formattedTemplate = filterUnconfiguredStats(template, showLureSpeed, showSizeMultiplier);
        formattedTemplate = injectMissingStats(formattedTemplate, showLureSpeed, showSizeMultiplier,
                line(messageManager, "rod_lure_speed_line", "&d│ 诱鱼速度: &f") + formatBonus(lureSpeed, messageManager),
                line(messageManager, "rod_size_multiplier_line", "&d│ 尺寸倍率: &f") + String.format(Locale.ROOT, "x%.2f", sizeMultiplier));
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            formattedTemplate = formattedTemplate.replace(entry.getKey(), entry.getValue());
        }

        List<String> lore = new ArrayList<>();
        for (String line : formattedTemplate.split("\\n")) {
            if (!line.trim().isEmpty()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', CustomItemHook.replaceFontImages(line)));
            } else {
                lore.add("");
            }
        }
        return lore;
    }

    private static String buildDefaultTemplate(MessageManager messageManager, boolean showLureSpeed, boolean showSizeMultiplier) {
        List<String> lines = new ArrayList<>();
        lines.add("&6[===== 鱼竿属性 =====]");
        lines.add("&b│ 难度系数: %difficulty%");
        lines.add("&a│ 浮标区域: %float_area%");
        lines.add("&c│ %rod_durability%: %durability%");
        lines.add("&d│ 充能速度: %charge_speed%");
        lines.add("&d│ 咬钩几率加成: %bite_rate_bonus%");
        lines.add(line(messageManager, "rod_rare_fish_chance_line", "&5│ 稀有鱼几率: &f") + "%rare_fish_chance%");
        if (showLureSpeed) lines.add(line(messageManager, "rod_lure_speed_line", "&d│ 诱鱼速度: &f") + "%lure_speed%");
        if (showSizeMultiplier) lines.add(line(messageManager, "rod_size_multiplier_line", "&d│ 尺寸倍率: &f") + "%size_multiplier%");
        lines.add("&6[====================]");
        lines.add(" ");
        lines.add("&e✨ 特殊效果:");
        lines.add("%effects%");
        lines.add(" ");
        lines.add("&7钓鱼快乐~");
        return String.join("\n", lines);
    }

    private static String formatBonus(double value, MessageManager messageManager) {
        return value > 0
                ? String.format(Locale.ROOT, "+%.1f%%", value * 100)
                : messageManager.getMessageWithoutPrefix("rod_bite_rate_bonus_none", "无");
    }

    static String formatRareFishChance(double value) {
        if (!Double.isFinite(value)) return "0.0%";
        return value > 0
                ? String.format(Locale.ROOT, "+%.1f%%", value * 100)
                : String.format(Locale.ROOT, "%.1f%%", value * 100);
    }

    private static String line(MessageManager messageManager, String key, String fallback) {
        return ChatColor.translateAlternateColorCodes('&', messageManager.getMessageWithoutPrefix(key, fallback));
    }

    private static String buildEffects(Config config, MessageManager messageManager, String rodName) {
        List<String> effects = config.getRodEffects(rodName);
        StringBuilder builder = new StringBuilder();
        if (!effects.isEmpty()) {
            for (String effect : effects) {
                builder.append("&7  └─ &r").append(ChatColor.translateAlternateColorCodes('&', effect)).append("\n");
            }
        } else {
            builder.append("&7  └─ ").append(messageManager.getMessageWithoutPrefix("rod_effects_none", "无特殊效果")).append("\n");
        }
        return builder.toString();
    }

    /**
     * 支持未包含新占位符的玩家自定义模板。
     */
    static String filterUnconfiguredStats(String template, boolean showLureSpeed, boolean showSizeMultiplier) {
        List<String> keptLines = new ArrayList<>();
        for (String line : template.split("\n", -1)) {
            String[] sections = line.split(" \\| ", -1);
            List<String> keptSections = new ArrayList<>();
            for (String section : sections) {
                if (!showLureSpeed && section.contains("%lure_speed%")) continue;
                if (!showSizeMultiplier && section.contains("%size_multiplier%")) continue;
                keptSections.add(section);
            }
            if (!keptSections.isEmpty()) keptLines.add(String.join(" | ", keptSections));
        }
        return String.join("\n", keptLines);
    }

    static String injectMissingStats(String template, boolean showLureSpeed, boolean showSizeMultiplier,
                                     String lureLine, String sizeLine) {
        StringBuilder stats = new StringBuilder();
        if (showLureSpeed && !template.contains("%lure_speed%")) {
            stats.append(ChatColor.translateAlternateColorCodes('&', lureLine)).append('\n');
        }
        if (showSizeMultiplier && !template.contains("%size_multiplier%")) {
            stats.append(ChatColor.translateAlternateColorCodes('&', sizeLine)).append('\n');
        }
        if (stats.length() == 0) {
            return template;
        }

        String[] lines = template.split("\n", -1);
        int insertAt = findInsertIndex(lines);
        List<String> merged = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (i == insertAt) {
                merged.add(stats.substring(0, stats.length() - 1));
            }
            merged.add(lines[i]);
        }
        if (insertAt >= lines.length) {
            merged.add(stats.substring(0, stats.length() - 1));
        }
        return String.join("\n", merged);
    }

    private static int findInsertIndex(String[] lines) {
        int index = indexOfPlaceholder(lines, "%bite_rate_bonus%");
        if (index >= 0) {
            return index + 1;
        }
        index = indexOfPlaceholder(lines, "%durability%");
        if (index >= 0) {
            return index + 1;
        }
        index = indexOfPlaceholder(lines, "%effects%");
        return index >= 0 ? index : lines.length;
    }

    private static int indexOfPlaceholder(String[] lines, String placeholder) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(placeholder)) {
                return i;
            }
        }
        return -1;
    }
}
