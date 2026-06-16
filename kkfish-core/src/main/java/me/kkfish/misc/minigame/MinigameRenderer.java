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
        String stylePath;
        if (waterType == WaterType.LAVA) {
            stylePath = "styles.lava";
        } else if (waterType == WaterType.VOID) {
            stylePath = "styles.void";
        } else {
            stylePath = "styles.default";
        }

        String progressBarChar = translateColor(stylePath + ".progress-char", "&9=");
        String progressBarEmptyChar = translateColor(stylePath + ".progress-bar-empty-char", "&7-");
        String greenBarChar = translateColor(stylePath + ".green-bar-char", "&a|");
        String greenBarEdgeChar = translateColor(stylePath + ".green-bar-edge-char", "&2|");
        String backgroundChar = translateColor(stylePath + ".background-char", "&7|");
        String fishIndicatorChar = translateColor(stylePath + ".fish-indicator-char", "&9|||");

        StringBuilder greenBar = new StringBuilder("[");
        int totalBars = 30;
        int greenBarCenter = (int) (greenBarPos * totalBars);
        int fishPosInBar = (int) (fishPos * totalBars);

        fishPosInBar = Math.min(fishPosInBar, totalBars - 1);

        String[] barSegments = new String[totalBars];

        int greenBarLength = (int) (greenBarWidth * totalBars);
        int edgeLeft = greenBarCenter - greenBarLength / 2;
        int edgeRight = greenBarCenter + greenBarLength / 2;
        for (int i = 0; i < totalBars; i++) {
            if (Math.abs(i - greenBarCenter) <= greenBarLength / 2) {
                if (i == edgeLeft || i == edgeRight) {
                    barSegments[i] = greenBarEdgeChar;
                } else {
                    barSegments[i] = greenBarChar;
                }
            } else {
                barSegments[i] = backgroundChar;
            }
        }

        barSegments[fishPosInBar] = fishIndicatorChar;

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

    /**
     * 从配置读取颜色/字符并翻译为 ChatColor。
     */
    private String translateColor(String path, String defaultValue) {
        String value = config.getMainConfig().getString(path, defaultValue);
        return ChatColor.translateAlternateColorCodes('&', value);
    }
}
