package me.kkfish.utils;

import org.bukkit.inventory.ItemStack;

public class NBTUtilAPI {

    private static boolean nbtApiAvailable = false;

    static {
        try {
            // 只认插件自己的类加载器。DependencyManager 会把 lib/ 下的 jar 挂到临时 URLClassLoader 并
            // 设成线程上下文，跟着上下文探测就会误判成可用，可后面反射又是用插件加载器找类，一样 ClassNotFound，
            // 结果是 NBT 写入全部失败还刷一堆堆栈
            Class.forName("de.tr7zw.nbtapi.NBTItem", false, NBTUtilAPI.class.getClassLoader());
            nbtApiAvailable = true;
        } catch (ClassNotFoundException ignored) {
        }
    }

    public static boolean isNbtApiAvailable() {
        return nbtApiAvailable;
    }

    public static boolean setNBTData(ItemStack item, String key, Object value) {
        if (!nbtApiAvailable || item == null) {
            return false;
        }

        try {
            Object nbtItem = Class.forName("de.tr7zw.nbtapi.NBTItem").getConstructor(ItemStack.class).newInstance(item);
            if (value instanceof String) {
                Class.forName("de.tr7zw.nbtapi.NBTItem").getMethod("setString", String.class, String.class).invoke(nbtItem, key, value);
            } else if (value instanceof Integer) {
                Class.forName("de.tr7zw.nbtapi.NBTItem").getMethod("setInteger", String.class, int.class).invoke(nbtItem, key, value);
            } else if (value instanceof Double) {
                Class.forName("de.tr7zw.nbtapi.NBTItem").getMethod("setDouble", String.class, double.class).invoke(nbtItem, key, value);
            } else if (value instanceof Boolean) {
                Class.forName("de.tr7zw.nbtapi.NBTItem").getMethod("setBoolean", String.class, boolean.class).invoke(nbtItem, key, value);
            } else {
                Class.forName("de.tr7zw.nbtapi.NBTItem").getMethod("setString", String.class, String.class).invoke(nbtItem, key, value.toString());
            }
            item.setItemMeta(((ItemStack) Class.forName("de.tr7zw.nbtapi.NBTItem").getMethod("getItem").invoke(nbtItem)).getItemMeta());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static Object getNBTData(ItemStack item, String key) {
        if (!nbtApiAvailable || item == null) {
            return null;
        }

        try {
            Object nbtItem = Class.forName("de.tr7zw.nbtapi.NBTItem").getConstructor(ItemStack.class).newInstance(item);
            if ((boolean) Class.forName("de.tr7zw.nbtapi.NBTItem").getMethod("hasKey", String.class).invoke(nbtItem, key)) {
                return Class.forName("de.tr7zw.nbtapi.NBTItem").getMethod("getObject", String.class, Class.class).invoke(nbtItem, key, Object.class);
            }
        } catch (Exception e) {
        }
        return null;
    }

    public static boolean hasNBTKey(ItemStack item, String key) {
        if (!nbtApiAvailable || item == null) {
            return false;
        }

        try {
            Object nbtItem = Class.forName("de.tr7zw.nbtapi.NBTItem").getConstructor(ItemStack.class).newInstance(item);
            return (boolean) Class.forName("de.tr7zw.nbtapi.NBTItem").getMethod("hasKey", String.class).invoke(nbtItem, key);
        } catch (Exception e) {
        }
        return false;
    }

    public static boolean removeNBTData(ItemStack item, String key) {
        if (!nbtApiAvailable || item == null) {
            return false;
        }

        try {
            Object nbtItem = Class.forName("de.tr7zw.nbtapi.NBTItem").getConstructor(ItemStack.class).newInstance(item);
            Class.forName("de.tr7zw.nbtapi.NBTItem").getMethod("removeKey", String.class).invoke(nbtItem, key);
            item.setItemMeta(((ItemStack) Class.forName("de.tr7zw.nbtapi.NBTItem").getMethod("getItem").invoke(nbtItem)).getItemMeta());
            return true;
        } catch (Exception e) {
        }
        return false;
    }
}
