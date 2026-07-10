package me.kkfish.integrations;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import me.kkfish.kkfish;
import me.kkfish.utils.XSeriesUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一自定义物品插件集成门面，支持 ItemsAdder / Oraxen / Nexo / CraftEngine。
 *
 * <p>通过反射访问各插件 API，避免硬依赖。配置中 material 字段使用前缀标识来源：
 * <ul>
 *   <li>ItemsAdder: {@code ia:} 或 {@code ::}</li>
 *   <li>Oraxen: {@code oraxen:} 或 {@code ox:}</li>
 *   <li>Nexo: {@code nexomc:} 或 {@code nx:}</li>
 *   <li>CraftEngine: {@code craftengine:} 或 {@code ce:}</li>
 * </ul>
 * </p>
 */
public class CustomItemHook {

    // ======================== 前缀常量 ========================
    private static final String[] IA_PREFIXES = {"ia:", "::"};
    private static final String[] ORAXEN_PREFIXES = {"oraxen:", "ox:"};
    private static final String[] NEXO_PREFIXES = {"nexomc:", "nx:"};
    private static final String[] CE_PREFIXES = {"craftengine:", "ce:"};

    // ======================== ItemsAdder 反射缓存 ========================
    private static boolean iaChecked = false;
    private static boolean iaAvailable = false;
    private static Class<?> iaCustomStackClass;
    private static Method iaGetInstance;
    private static Method iaIsInRegistry;
    private static Method iaGetItemStack;
    private static Method iaByItemStack;
    private static Constructor<?> iaFontImageCtor;
    private static Method iaFontImageGetString;

    // ======================== Oraxen 反射缓存 ========================
    private static boolean oraxenChecked = false;
    private static boolean oraxenAvailable = false;
    private static Class<?> oraxenItemsClass;
    private static Method oraxenGetItemById;
    private static Method oraxenExists;
    private static Method oraxenGetIdByItem;
    private static Method oraxenItemBuilderBuild;

    // ======================== Nexo 反射缓存 ========================
    private static boolean nexoChecked = false;
    private static boolean nexoAvailable = false;
    private static Class<?> nexoItemsClass;
    private static Method nexoItemFromId;
    private static Method nexoIdFromItem;
    private static Method nexoItemBuilderBuild;

    // ======================== CraftEngine 反射缓存 ========================
    private static boolean ceChecked = false;
    private static boolean ceAvailable = false;
    private static Class<?> ceItemsClass;
    private static Method ceById;
    private static Method ceIsCustomItem;
    private static Method ceGetCustomItemId;
    private static Method ceItemDefGetItem;

    // 字体图片占位符正则：:namespace:name: 或 :name:
    private static final Pattern FONT_IMAGE_PATTERN = Pattern.compile(":([a-zA-Z0-9_]+):([a-zA-Z0-9_]+):|:([a-zA-Z0-9_]+):");

    // ======================== ItemsAdder 初始化 ========================

    private static void checkIA() {
        if (iaChecked) return;
        iaChecked = true;
        try {
            if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) return;
            iaCustomStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Class<?> fontImageClass = Class.forName("dev.lone.itemsadder.api.FontImageWrapper");

            iaGetInstance = iaCustomStackClass.getMethod("getInstance", String.class);
            iaIsInRegistry = iaCustomStackClass.getMethod("isInRegistry", String.class);
            iaGetItemStack = iaCustomStackClass.getMethod("getItemStack");
            iaByItemStack = iaCustomStackClass.getMethod("byItemStack", ItemStack.class);

            iaFontImageCtor = fontImageClass.getConstructor(String.class);
            iaFontImageGetString = fontImageClass.getMethod("getString");

            iaAvailable = true;
            kkfish.log("§a[CustomItemHook] ItemsAdder detected~");
        } catch (Exception ignored) {}
    }

    // ======================== Oraxen 初始化 ========================

    private static void checkOraxen() {
        if (oraxenChecked) return;
        oraxenChecked = true;
        try {
            if (Bukkit.getPluginManager().getPlugin("Oraxen") == null) return;
            oraxenItemsClass = Class.forName("io.th0rgal.oraxen.api.OraxenItems");
            Class<?> itemBuilderClass = Class.forName("io.th0rgal.oraxen.items.ItemBuilder");

            oraxenGetItemById = oraxenItemsClass.getMethod("getItemById", String.class);
            oraxenExists = oraxenItemsClass.getMethod("exists", ItemStack.class);
            oraxenGetIdByItem = oraxenItemsClass.getMethod("getIdByItem", ItemStack.class);
            oraxenItemBuilderBuild = itemBuilderClass.getMethod("build");

            oraxenAvailable = true;
            kkfish.log("§a[CustomItemHook] Oraxen detected~");
        } catch (Exception ignored) {}
    }

    // ======================== Nexo 初始化 ========================

    private static void checkNexo() {
        if (nexoChecked) return;
        nexoChecked = true;
        try {
            if (Bukkit.getPluginManager().getPlugin("Nexo") == null) return;
            nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            Class<?> itemBuilderClass = Class.forName("com.nexomc.nexo.items.ItemBuilder");

            nexoItemFromId = nexoItemsClass.getMethod("itemFromId", String.class);
            nexoIdFromItem = nexoItemsClass.getMethod("idFromItem", ItemStack.class);
            nexoItemBuilderBuild = itemBuilderClass.getMethod("build");

            nexoAvailable = true;
            kkfish.log("§a[CustomItemHook] Nexo detected~");
        } catch (Exception ignored) {}
    }

    // ======================== CraftEngine 初始化 ========================

    private static void checkCraftEngine() {
        if (ceChecked) return;
        ceChecked = true;
        try {
            if (Bukkit.getPluginManager().getPlugin("CraftEngine") == null) return;
            ceItemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Class<?> itemDefClass = Class.forName("net.momirealms.craftengine.bukkit.item.BukkitItemDefinition");

            ceById = ceItemsClass.getMethod("byId", String.class);
            ceIsCustomItem = ceItemsClass.getMethod("isCustomItem", ItemStack.class);
            ceGetCustomItemId = ceItemsClass.getMethod("getCustomItemId", ItemStack.class);
            // BukkitItemDefinition 继承自 ItemDefinition，getItem() 返回 ItemStack
            ceItemDefGetItem = findMethod(itemDefClass, "getItem", ItemStack.class);

            ceAvailable = true;
            kkfish.log("§a[CustomItemHook] CraftEngine detected~");
        } catch (Exception ignored) {}
    }

    /** 在类及父类中查找方法 */
    private static Method findMethod(Class<?> clazz, String name, Class<?> returnType) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && (returnType == null || returnType.isAssignableFrom(m.getReturnType()))) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    // ======================== 公共 API ========================

    /**
     * 判断材质字符串是否指向任意受支持的自定义物品插件。
     */
    public static boolean isCustomItemStr(String materialStr) {
        return extractItemId(materialStr) != null;
    }

    /**
     * 从材质字符串中提取物品ID和来源插件。
     * 返回 null 表示不是自定义物品标识。
     */
    public static String extractItemId(String materialStr) {
        if (materialStr == null || materialStr.isEmpty()) return null;
        String lower = materialStr.toLowerCase();

        // ItemsAdder: ia: 或 ::
        for (String prefix : IA_PREFIXES) {
            if (lower.startsWith(prefix)) return materialStr.substring(prefix.length()).trim();
        }
        // Oraxen: oraxen: 或 ox:
        for (String prefix : ORAXEN_PREFIXES) {
            if (lower.startsWith(prefix)) return materialStr.substring(prefix.length()).trim();
        }
        // Nexo: nexomc: 或 nx:
        for (String prefix : NEXO_PREFIXES) {
            if (lower.startsWith(prefix)) return materialStr.substring(prefix.length()).trim();
        }
        // CraftEngine: craftengine: 或 ce:
        for (String prefix : CE_PREFIXES) {
            if (lower.startsWith(prefix)) return materialStr.substring(prefix.length()).trim();
        }
        return null;
    }

    /**
     * 统一物品创建：按优先级尝试各插件，失败则回退原版材质。
     *
     * @param materialStr 材质字符串（可带插件前缀）
     * @param amount 物品数量
     * @return ItemStack，永不为null
     */
    public static ItemStack createItemStack(String materialStr, int amount) {
        if (materialStr != null) {
            String lower = materialStr.toLowerCase();
            String itemId;

            // ItemsAdder
            itemId = tryExtractPrefix(lower, materialStr, IA_PREFIXES);
            if (itemId != null) {
                checkIA();
                if (iaAvailable) {
                    ItemStack item = getIAItem(itemId);
                    if (item != null) { item.setAmount(amount); return item; }
                    kkfish.log("§e[CustomItemHook] IA item not found: " + itemId);
                }
                materialStr = itemId;
            }

            // Oraxen
            if (itemId == null) {
                itemId = tryExtractPrefix(lower, materialStr, ORAXEN_PREFIXES);
                if (itemId != null) {
                    checkOraxen();
                    if (oraxenAvailable) {
                        ItemStack item = getOraxenItem(itemId);
                        if (item != null) { item.setAmount(amount); return item; }
                        kkfish.log("§e[CustomItemHook] Oraxen item not found: " + itemId);
                    }
                    materialStr = itemId;
                }
            }

            // Nexo
            if (itemId == null) {
                itemId = tryExtractPrefix(lower, materialStr, NEXO_PREFIXES);
                if (itemId != null) {
                    checkNexo();
                    if (nexoAvailable) {
                        ItemStack item = getNexoItem(itemId);
                        if (item != null) { item.setAmount(amount); return item; }
                        kkfish.log("§e[CustomItemHook] Nexo item not found: " + itemId);
                    }
                    materialStr = itemId;
                }
            }

            // CraftEngine
            if (itemId == null) {
                itemId = tryExtractPrefix(lower, materialStr, CE_PREFIXES);
                if (itemId != null) {
                    checkCraftEngine();
                    if (ceAvailable) {
                        ItemStack item = getCEItem(itemId);
                        if (item != null) { item.setAmount(amount); return item; }
                        kkfish.log("§e[CustomItemHook] CraftEngine item not found: " + itemId);
                    }
                    materialStr = itemId;
                }
            }
        }

        // 原版流程
        Material material = XSeriesUtil.parseMaterial(materialStr);
        if (material == null) material = XSeriesUtil.getMaterial("STONE");
        return new ItemStack(material, amount);
    }

    public static ItemStack createItemStack(String materialStr) {
        return createItemStack(materialStr, 1);
    }

    /**
     * 检查一个 ItemStack 是否为任意受支持插件的自定义物品
     */
    public static boolean isCustomItem(ItemStack item) {
        if (item == null) return false;
        checkIA();
        if (iaAvailable) {
            try {
                if (iaByItemStack.invoke(null, item) != null) return true;
            } catch (Exception ignored) {}
        }
        checkOraxen();
        if (oraxenAvailable) {
            try {
                if ((boolean) oraxenExists.invoke(null, item)) return true;
            } catch (Exception ignored) {}
        }
        checkNexo();
        if (nexoAvailable) {
            try {
                Object id = nexoIdFromItem.invoke(null, item);
                if (id != null && !((String) id).isEmpty()) return true;
            } catch (Exception ignored) {}
        }
        checkCraftEngine();
        if (ceAvailable) {
            try {
                if ((boolean) ceIsCustomItem.invoke(null, item)) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * 替换文本中的 ItemsAdder 字体图片占位符。
     * 仅 ItemsAdder 支持此功能，其他插件不处理。
     */
    public static String replaceFontImages(String text) {
        if (text == null || text.isEmpty()) return text;
        checkIA();
        if (!iaAvailable) return text;
        if (!text.contains(":")) return text;

        Matcher matcher = FONT_IMAGE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = null;
            try {
                String fontImageId;
                if (matcher.group(1) != null && matcher.group(2) != null) {
                    fontImageId = matcher.group(1) + ":" + matcher.group(2);
                } else {
                    fontImageId = matcher.group(3);
                }
                Object fontImage = iaFontImageCtor.newInstance(fontImageId);
                replacement = (String) iaFontImageGetString.invoke(fontImage);
            } catch (Exception ignored) {}

            if (replacement != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 批量替换列表中的字体图片占位符 */
    public static java.util.List<String> replaceFontImages(java.util.List<String> lines) {
        if (lines == null || lines.isEmpty()) return lines;
        checkIA();
        if (!iaAvailable) return lines;
        java.util.List<String> result = new java.util.ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(replaceFontImages(line));
        }
        return result;
    }

    // 兼容旧调用
    /** @deprecated 请用 {@link #isCustomItemStr(String)} */
    @Deprecated
    public static boolean isItemsAdderItem(String materialStr) {
        return isCustomItemStr(materialStr);
    }
    /** @deprecated 请用 {@link #isAvailable()} 检查对应插件 */
    @Deprecated
    public static boolean isAvailable() {
        checkIA(); checkOraxen(); checkNexo(); checkCraftEngine();
        return iaAvailable || oraxenAvailable || nexoAvailable || ceAvailable;
    }

    // ======================== 各插件物品获取 ========================

    private static ItemStack getIAItem(String id) {
        try {
            Object customStack = iaGetInstance.invoke(null, id);
            if (customStack == null) return null;
            ItemStack item = (ItemStack) iaGetItemStack.invoke(customStack);
            return item != null ? item.clone() : null;
        } catch (Exception e) { return null; }
    }

    private static ItemStack getOraxenItem(String id) {
        try {
            Object builder = oraxenGetItemById.invoke(null, id);
            if (builder == null) return null;
            ItemStack item = (ItemStack) oraxenItemBuilderBuild.invoke(builder);
            return item != null ? item.clone() : null;
        } catch (Exception e) { return null; }
    }

    private static ItemStack getNexoItem(String id) {
        try {
            Object builder = nexoItemFromId.invoke(null, id);
            if (builder == null) return null;
            ItemStack item = (ItemStack) nexoItemBuilderBuild.invoke(builder);
            return item != null ? item.clone() : null;
        } catch (Exception e) { return null; }
    }

    private static ItemStack getCEItem(String id) {
        try {
            Object def = ceById.invoke(null, id);
            if (def == null) return null;
            if (ceItemDefGetItem != null) {
                ItemStack item = (ItemStack) ceItemDefGetItem.invoke(def);
                return item != null ? item.clone() : null;
            }
        } catch (Exception e) { return null; }
        return null;
    }

    // ======================== 工具方法 ========================

    private static String tryExtractPrefix(String lower, String original, String[] prefixes) {
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                return original.substring(prefix.length()).trim();
            }
        }
        return null;
    }
}
