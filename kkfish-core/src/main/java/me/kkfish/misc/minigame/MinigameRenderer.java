package me.kkfish.misc.minigame;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import me.kkfish.kkfish;
import me.kkfish.managers.Config;
import me.kkfish.fishing.WaterType;

/**
 * 小游戏 UI 渲染器：负责将绿条、进度条、鱼指示符渲染为标题消息发送给玩家。
 * 从 GameSession 抽取，职责单一。
 */
public class MinigameRenderer {

    private final kkfish plugin;
    private final Config config;
    private final Player player;
    private final WaterType waterType;

    public MinigameRenderer(kkfish plugin, Player player, WaterType waterType) {
        this.plugin = plugin;
        this.config = plugin.getCustomConfig();
        this.player = player;
        this.waterType = waterType;
    }

    /**
     * 渲染小游戏 UI（绿条 + 进度条）并发送给玩家。
     *
     * @param greenBarPos   绿条中心位置 (0~1)
     * @param greenBarWidth 绿条宽度 (0~1)
     * @param fishPos       鱼指示符位置 (0~1)
     * @param progress      进度 (0~1)
     */
    public void render(double greenBarPos, double greenBarWidth, double fishPos, double progress) {
        String stylePath = stylePath();

        String progressBarChar = translateColor(stylePath + ".progress-char", "&9=");
        String progressBarEmptyChar = translateColor(stylePath + ".progress-bar-empty-char", "&7-");
        String greenBarChar = translateColor(stylePath + ".green-bar-char", "&a|");
        String greenBarEdgeChar = translateColor(stylePath + ".green-bar-edge-char", "&2|");
        String backgroundChar = translateColor(stylePath + ".background-char", "&7|");
        String fishIndicatorChar = translateColor(stylePath + ".fish-indicator-char", "&9|||");

        StringBuilder greenBar = new StringBuilder("[");
        int totalBars = MinigameField.CELLS;

        String[] barSegments = new String[totalBars];

        int greenBarLength = MinigameField.barLength(greenBarWidth);
        int edgeLeft = MinigameField.barLeft(greenBarPos, greenBarWidth);
        int edgeRight = edgeLeft + greenBarLength - 1;
        for (int i = 0; i < totalBars; i++) {
            if (greenBarLength > 0 && i >= edgeLeft && i <= edgeRight) {
                if (i == edgeLeft || i == edgeRight) {
                    barSegments[i] = greenBarEdgeChar;
                } else {
                    barSegments[i] = greenBarChar;
                }
            } else {
                barSegments[i] = backgroundChar;
            }
        }

        // 鱼指示符可能多格宽（默认 |||），按可见宽度居中并限制在底栏内，避免顶出右括号
        int fishWidth = MinigameField.clampFishWidth(ChatColor.stripColor(fishIndicatorChar).length());
        int fishStart = MinigameField.fishStart(fishPos, fishWidth);
        for (int i = fishStart; i < fishStart + fishWidth; i++) {
            barSegments[i] = "";
        }
        barSegments[fishStart] = fishIndicatorChar;

        for (String segment : barSegments) {
            greenBar.append(segment);
        }
        greenBar.append("]");

        StringBuilder progressBar = new StringBuilder("[");
        int progressLength = (int) (progress * 20);

        for (int i = 0; i < 20; i++) {
            progressBar.append(i < progressLength ? progressBarChar : progressBarEmptyChar);
        }
        progressBar.append("]");

        player.sendTitle(greenBar.toString(), progressBar.toString(), 0, 10, 0);
    }

    /** 当前水域使用的样式节点。 */
    private String stylePath() {
        if (waterType == WaterType.LAVA) {
            return "styles.lava";
        }
        if (waterType == WaterType.VOID) {
            return "styles.void";
        }
        return "styles.default";
    }

    /** 当前风格下鱼指示符的可见宽度，进度判定要和这个宽度保持一致。 */
    int getFishIndicatorWidth() {
        String value = translateColor(stylePath() + ".fish-indicator-char", "&9|||");
        return MinigameField.clampFishWidth(ChatColor.stripColor(value).length());
    }

    /**
     * 从配置读取颜色/字符并翻译为 ChatColor。
     */
    private String translateColor(String path, String defaultValue) {
        String value = config.getMainConfig().getString(path, defaultValue);
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
