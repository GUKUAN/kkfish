package me.kkfish.utils;

import org.bukkit.Material;

import me.kkfish.managers.Config;

/**
 * 鱼钩材质解析工具。
 *
 * <p>统一 Fish.java 和 GUI.java 中重复的 getMaterialFromType 逻辑，
 * 所有材质解析通过 {@link XSeriesUtil} 保证多版本兼容。</p>
 */
public final class MaterialResolver {

    private MaterialResolver() {
    }

    /**
     * 将字符串材质类型转换为 Material。
     *
     * <p>解析顺序：配置映射 → 硬编码映射 → 直接 XSeries 解析 → 归一化解析 → 默认值。</p>
     *
     * @param type   材质类型字符串（如 "wood"、"stone"、"white_wool"）
     * @param config 配置管理器，用于查询 hook_material.yml 中的映射
     * @return 解析到的 Material，永不返回 null（失败时返回 OAK_LOG）
     */
    public static Material getMaterialFromType(String type, Config config) {
        if (type == null) {
            return XSeriesUtil.getMaterial("OAK_LOG");
        }

        // 首先尝试从配置中获取材质
        if (config != null) {
            try {
                Material configMaterial = config.getHookMaterial(type);
                if (configMaterial != null) {
                    return configMaterial;
                }
            } catch (Exception ignored) {
            }
        }

        // 硬编码映射作为后备
        switch (type.toLowerCase()) {
            case "wood":
                return XSeriesUtil.getMaterial("OAK_LOG");
            case "stone":
                return XSeriesUtil.getMaterial("COBBLESTONE");
            case "iron":
                return XSeriesUtil.getMaterial("IRON_BLOCK");
            case "gold":
                return XSeriesUtil.getMaterial("GOLD_BLOCK");
            case "diamond":
                return XSeriesUtil.getMaterial("DIAMOND_BLOCK");
            case "white_wool":
                return XSeriesUtil.getMaterial("WHITE_WOOL");
            case "orange_wool":
                return XSeriesUtil.getMaterial("ORANGE_WOOL");
            case "magenta_wool":
                return XSeriesUtil.getMaterial("MAGENTA_WOOL");
            case "light_blue_wool":
                return XSeriesUtil.getMaterial("LIGHT_BLUE_WOOL");
            case "yellow_wool":
                return XSeriesUtil.getMaterial("YELLOW_WOOL");
            case "lime_wool":
                return XSeriesUtil.getMaterial("LIME_WOOL");
            case "pink_wool":
                return XSeriesUtil.getMaterial("PINK_WOOL");
            case "gray_wool":
                return XSeriesUtil.getMaterial("GRAY_WOOL");
            case "light_gray_wool":
                return XSeriesUtil.getMaterial("LIGHT_GRAY_WOOL");
            case "cyan_wool":
                return XSeriesUtil.getMaterial("CYAN_WOOL");
            case "purple_wool":
                return XSeriesUtil.getMaterial("PURPLE_WOOL");
            case "blue_wool":
                return XSeriesUtil.getMaterial("BLUE_WOOL");
            case "brown_wool":
                return XSeriesUtil.getMaterial("BROWN_WOOL");
            case "green_wool":
                return XSeriesUtil.getMaterial("GREEN_WOOL");
            case "red_wool":
                return XSeriesUtil.getMaterial("RED_WOOL");
            case "black_wool":
                return XSeriesUtil.getMaterial("BLACK_WOOL");
            default:
                // 尝试直接使用 XSeriesUtil.getMaterial 获取材质
                try {
                    Material material = XSeriesUtil.getMaterial(type);
                    if (material != null) {
                        return material;
                    }
                } catch (Exception ignored) {
                }

                // 最后尝试将材质类型转换为大写并移除下划线
                try {
                    String normalizedType = type.toUpperCase().replace("_", "");
                    Material material = XSeriesUtil.getMaterial(normalizedType);
                    if (material != null) {
                        return material;
                    }
                } catch (Exception ignored) {
                }
                return XSeriesUtil.getMaterial("OAK_LOG");
        }
    }
}
