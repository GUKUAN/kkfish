package me.kkfish.economy;

/**
 * 出售经济值，兼容旧版单 value 和新版分经济值。
 */
public class SellValue {
    public static final int NOT_SET = -1;

    private final int oldValue;
    private final int vaultValue;
    private final int pointsValue;

    private SellValue(int oldValue, int vaultValue, int pointsValue) {
        this.oldValue = oldValue;
        this.vaultValue = vaultValue;
        this.pointsValue = pointsValue;
    }

    public static SellValue oldValue(int value) {
        return new SellValue(Math.max(0, value), NOT_SET, NOT_SET);
    }

    public static SellValue splitValue(int vault, int points) {
        return new SellValue(0, Math.max(0, vault), Math.max(0, points));
    }

    public static SellValue raw(int oldValue, int vaultValue, int pointsValue) {
        return new SellValue(Math.max(0, oldValue), vaultValue, pointsValue);
    }

    public int getOldValue() {
        return oldValue;
    }

    public int getVaultValue() {
        return vaultValue;
    }

    public int getPointsValue() {
        return pointsValue;
    }

    public boolean hasSplitValue() {
        return vaultValue >= 0 || pointsValue >= 0;
    }

    public boolean hasAnyValue() {
        if (hasSplitValue()) {
            return vaultValue > 0 || pointsValue > 0;
        }
        return oldValue > 0;
    }

    public int getDisplayValue() {
        if (hasSplitValue()) {
            if (vaultValue > 0) return vaultValue;
            if (pointsValue > 0) return pointsValue;
        }
        return oldValue;
    }
}
