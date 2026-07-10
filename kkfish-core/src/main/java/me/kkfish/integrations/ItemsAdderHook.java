package me.kkfish.integrations;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * ItemsAdder 兼容层 — 委托给 {@link CustomItemHook}。
 *
 * @deprecated 请直接使用 {@link CustomItemHook}，此类仅保留向后兼容。
 */
@Deprecated
public class ItemsAdderHook {

    /** @deprecated 请用 {@link CustomItemHook#isCustomItemStr(String)} */
    @Deprecated
    public static boolean isItemsAdderItem(String materialStr) {
        return CustomItemHook.isCustomItemStr(materialStr);
    }

    /** @deprecated 请用 {@link CustomItemHook#extractItemId(String)} */
    @Deprecated
    public static String extractItemId(String materialStr) {
        return CustomItemHook.extractItemId(materialStr);
    }

    /** @deprecated 请用 {@link CustomItemHook#isAvailable()} */
    @Deprecated
    public static boolean isAvailable() {
        return CustomItemHook.isAvailable();
    }

    /** @deprecated 请用 {@link CustomItemHook#getCustomItem(String)} — 注意不存在，改用 createItemStack */
    @Deprecated
    public static ItemStack getCustomItem(String id) {
        return CustomItemHook.createItemStack(id, 1);
    }

    /** @deprecated 请用 {@link CustomItemHook#isInRegistry(String)} — 注意不存在 */
    @Deprecated
    public static boolean isInRegistry(String id) {
        return false;
    }

    /** @deprecated 请用 {@link CustomItemHook#createItemStack(String, int)} */
    @Deprecated
    public static ItemStack createItemStack(String materialStr, int amount) {
        return CustomItemHook.createItemStack(materialStr, amount);
    }

    /** @deprecated 请用 {@link CustomItemHook#createItemStack(String)} */
    @Deprecated
    public static ItemStack createItemStack(String materialStr) {
        return CustomItemHook.createItemStack(materialStr);
    }

    /** @deprecated 请用 {@link CustomItemHook#isCustomItem(ItemStack)} */
    @Deprecated
    public static boolean isCustomItem(ItemStack item) {
        return CustomItemHook.isCustomItem(item);
    }

    /** @deprecated 请用 {@link CustomItemHook#replaceFontImages(String)} */
    @Deprecated
    public static String replaceFontImages(String text) {
        return CustomItemHook.replaceFontImages(text);
    }

    /** @deprecated 请用 {@link CustomItemHook#replaceFontImages(List)} */
    @Deprecated
    public static List<String> replaceFontImages(List<String> lines) {
        return CustomItemHook.replaceFontImages(lines);
    }
}
