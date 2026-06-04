package me.kkfish.utils;

import org.bukkit.inventory.ItemStack;

public class NBTUtilAPI {

    private static boolean nbtApiAvailable = false;

    static {
        try {
            try {
                ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                contextClassLoader.loadClass("de.tr7zw.nbtapi.NBTItem");
                nbtApiAvailable = true;
            } catch (ClassNotFoundException e) {
                try {
                    Class.forName("de.tr7zw.nbtapi.NBTItem");
                    nbtApiAvailable = true;
                } catch (ClassNotFoundException ex) {
                }
            }
        } catch (Exception e) {
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
