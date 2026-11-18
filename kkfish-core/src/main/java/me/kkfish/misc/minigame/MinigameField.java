package me.kkfish.misc.minigame;

/**
 * 小游戏底栏几何：渲染和进度判定共用同一套格子。
 * 坐标统一在这里换算成 30 格，避免两边各算一套导致边缘对不上。
 */
final class MinigameField {

    /** 底栏总格数。 */
    static final int CELLS = 30;

    private MinigameField() {
    }

    /** 绿条占用的格子数，宽度为 0 时返回 0（等于没有绿条）。 */
    static int barLength(double width) {
        return Math.max(0, Math.min(CELLS, (int) Math.round(width * CELLS)));
    }

    /** 绿条最左格；整条被钳制在底栏内，贴边时不会被截掉一半。 */
    static int barLeft(double center, double width) {
        int length = barLength(width);
        int left = (int) Math.round(center * CELLS - length / 2.0);
        return Math.max(0, Math.min(CELLS - length, left));
    }

    /** 鱼所在格，连续坐标四舍五入落格，和绘制保持一致。 */
    static int fishCell(double fishPos) {
        return Math.max(0, Math.min(CELLS - 1, (int) Math.round(fishPos * CELLS)));
    }

    /** 鱼指示符的可见宽度，至少 1 格且不超过底栏总格数。 */
    static int clampFishWidth(int width) {
        return Math.max(1, Math.min(CELLS, width));
    }

    /** 鱼指示符最左格，按可见宽度居中并限制在底栏内。 */
    static int fishStart(double fishPos, int fishWidth) {
        int width = clampFishWidth(fishWidth);
        int cell = fishCell(fishPos);
        return Math.max(0, Math.min(CELLS - width, cell - width / 2));
    }

    /** 单格鱼指示符的判定，等价于只检查鱼所在格。 */
    static boolean inBar(double fishPos, double center, double width) {
        return inBar(fishPos, center, width, 1);
    }

    /** 鱼指示符是否与绿条有重叠，结果与画面完全一致。 */
    static boolean inBar(double fishPos, double center, double width, int fishWidth) {
        int length = barLength(width);
        if (length <= 0) {
            return false;
        }
        int barLeft = barLeft(center, width);
        int visibleWidth = clampFishWidth(fishWidth);
        int start = fishStart(fishPos, visibleWidth);
        int end = start + visibleWidth - 1;
        return start <= barLeft + length - 1 && end >= barLeft;
    }
}
