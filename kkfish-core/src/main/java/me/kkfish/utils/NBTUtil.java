package me.kkfish.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class NBTUtil {

    private static boolean initialized = false;
    private static int majorVersion;
    private static int minorVersion;

    static {
        initialize();
    }

    private static void initialize() {
        if (!initialized) {
            try {
                // 检测服务器版本
                String version = Bukkit.getBukkitVersion().split("-")[0];
                String[] parts = version.split("\\.");
                if (parts.length >= 2) {
                    majorVersion = Integer.parseInt(parts[0]);
                    minorVersion = Integer.parseInt(parts[1]);
                }
                initialized = true;
            } catch (Exception e) {
                // 版本检测失败，默认使用较新的版本
                majorVersion = 1;
                minorVersion = 14;
                initialized = true;
            }
        }
    }


    public static boolean addTags(ItemStack item, List<String> tags) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        if (isVersionNewerOrEqual(1, 14)) {
            return addTagsUsingPDC(item, tags);
        }

        return addTagsUsingTraditionalNBT(item, tags);
    }


    private static boolean addTagsUsingPDC(ItemStack item, List<String> tags) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            Method getPdcMethod = meta.getClass().getMethod("getPersistentDataContainer");
            getPdcMethod.setAccessible(true);
            Object pdc = getPdcMethod.invoke(meta);

            if (pdc != null) {
                NamespacedKey tagsKey = new NamespacedKey("kkfish", "Tags");

                Method setMethod = pdc.getClass().getMethod("set", NamespacedKey.class, PersistentDataType.class, Object.class);
                setMethod.setAccessible(true);
                setMethod.invoke(pdc, tagsKey, PersistentDataType.STRING, String.join(",", tags));

                item.setItemMeta(meta);
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }


    private static boolean addTagsUsingTraditionalNBT(ItemStack item, List<String> tags) {
        try {
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + getServerVersion() + ".inventory.CraftItemStack");
            Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItemStack = asNMSCopyMethod.invoke(null, item);

            if (nmsItemStack != null) {
                Method getTagMethod = nmsItemStack.getClass().getMethod("getTag");
                Object nbtTag = getTagMethod.invoke(nmsItemStack);

                if (nbtTag == null) {
                    Class<?> nbtTagCompoundClass = Class.forName("net.minecraft.server." + getServerVersion() + ".NBTTagCompound");
                    nbtTag = nbtTagCompoundClass.newInstance();
                    Method setTagMethod = nmsItemStack.getClass().getMethod("setTag", nbtTag.getClass());
                    setTagMethod.invoke(nmsItemStack, nbtTag);
                }

                Method setStringMethod = nbtTag.getClass().getMethod("setString", String.class, String.class);
                setStringMethod.invoke(nbtTag, "Tags", String.join(",", tags));

                Method asBukkitCopyMethod = craftItemStackClass.getMethod("asBukkitCopy", nmsItemStack.getClass());
                ItemStack resultItem = (ItemStack) asBukkitCopyMethod.invoke(null, nmsItemStack);

                item.setItemMeta(resultItem.getItemMeta());
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }


    public static boolean setNBTData(ItemStack item, String key, Object value) {
        if (item == null) {
            return false;
        }

        if (NBTUtilAPI.isNbtApiAvailable()) {
            return NBTUtilAPI.setNBTData(item, key, value);
        }

        if (isVersionNewerOrEqual(1, 14) && item.hasItemMeta()) {
            return setNBTDataUsingPDC(item, key, value);
        }

        return setNBTDataUsingTraditionalNBT(item, key, value);
    }


    private static boolean setNBTDataUsingPDC(ItemStack item, String key, Object value) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            Method getPdcMethod = meta.getClass().getMethod("getPersistentDataContainer");
            getPdcMethod.setAccessible(true);
            Object pdc = getPdcMethod.invoke(meta);

            if (pdc != null) {
                NamespacedKey nsk = new NamespacedKey("kkfish", key);

                Method setMethod = pdc.getClass().getMethod("set", NamespacedKey.class, PersistentDataType.class, Object.class);
                setMethod.setAccessible(true);

                if (value instanceof String) {
                    setMethod.invoke(pdc, nsk, PersistentDataType.STRING, value);
                } else if (value instanceof Integer) {
                    setMethod.invoke(pdc, nsk, PersistentDataType.INTEGER, value);
                } else if (value instanceof Double) {
                    setMethod.invoke(pdc, nsk, PersistentDataType.DOUBLE, value);
                } else if (value instanceof Boolean) {
                    setMethod.invoke(pdc, nsk, PersistentDataType.INTEGER, ((Boolean) value) ? 1 : 0);
                } else {
                    setMethod.invoke(pdc, nsk, PersistentDataType.STRING, value.toString());
                }

                item.setItemMeta(meta);
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }


    private static boolean setNBTDataUsingTraditionalNBT(ItemStack item, String key, Object value) {
        try {
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + getServerVersion() + ".inventory.CraftItemStack");
            Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItemStack = asNMSCopyMethod.invoke(null, item);

            if (nmsItemStack != null) {
                Method getTagMethod = nmsItemStack.getClass().getMethod("getTag");
                Object nbtTag = getTagMethod.invoke(nmsItemStack);

                if (nbtTag == null) {
                    Class<?> nbtTagCompoundClass = Class.forName("net.minecraft.server." + getServerVersion() + ".NBTTagCompound");
                    nbtTag = nbtTagCompoundClass.newInstance();
                    Method setTagMethod = nmsItemStack.getClass().getMethod("setTag", nbtTag.getClass());
                    setTagMethod.invoke(nmsItemStack, nbtTag);
                }

                if (value instanceof String) {
                    Method setStringMethod = nbtTag.getClass().getMethod("setString", String.class, String.class);
                    setStringMethod.invoke(nbtTag, key, value);
                } else if (value instanceof Integer) {
                    Method setIntMethod = nbtTag.getClass().getMethod("setInt", String.class, int.class);
                    setIntMethod.invoke(nbtTag, key, value);
                } else if (value instanceof Double) {
                    Method setDoubleMethod = nbtTag.getClass().getMethod("setDouble", String.class, double.class);
                    setDoubleMethod.invoke(nbtTag, key, value);
                } else if (value instanceof Boolean) {
                    Method setBooleanMethod = nbtTag.getClass().getMethod("setBoolean", String.class, boolean.class);
                    setBooleanMethod.invoke(nbtTag, key, value);
                } else {
                    Method setStringMethod = nbtTag.getClass().getMethod("setString", String.class, String.class);
                    setStringMethod.invoke(nbtTag, key, value.toString());
                }

                Method asBukkitCopyMethod = craftItemStackClass.getMethod("asBukkitCopy", nmsItemStack.getClass());
                ItemStack resultItem = (ItemStack) asBukkitCopyMethod.invoke(null, nmsItemStack);

                item.setItemMeta(resultItem.getItemMeta());
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }


    public static boolean hasTag(ItemStack item, String tag) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        if (isVersionNewerOrEqual(1, 14)) {
            return hasTagUsingPDC(item, tag);
        }

        return hasTagUsingTraditionalNBT(item, tag);
    }


    private static boolean hasTagUsingPDC(ItemStack item, String tag) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            Method getPdcMethod = meta.getClass().getMethod("getPersistentDataContainer");
            getPdcMethod.setAccessible(true);
            Object pdc = getPdcMethod.invoke(meta);

            if (pdc != null) {
                NamespacedKey tagsKey = new NamespacedKey("kkfish", "Tags");

                Method hasMethod = pdc.getClass().getMethod("has", NamespacedKey.class, PersistentDataType.class);
                hasMethod.setAccessible(true);
                boolean hasTags = (boolean) hasMethod.invoke(pdc, tagsKey, PersistentDataType.STRING);

                if (hasTags) {
                    Method getMethod = pdc.getClass().getMethod("get", NamespacedKey.class, PersistentDataType.class);
                    getMethod.setAccessible(true);
                    String tagsValue = (String) getMethod.invoke(pdc, tagsKey, PersistentDataType.STRING);

                    return tagsValue.contains(tag);
                }
            }
        } catch (Exception e) {
        }
        return false;
    }


    private static boolean hasTagUsingTraditionalNBT(ItemStack item, String tag) {
        try {
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + getServerVersion() + ".inventory.CraftItemStack");
            Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItemStack = asNMSCopyMethod.invoke(null, item);

            if (nmsItemStack != null) {
                Method getTagMethod = nmsItemStack.getClass().getMethod("getTag");
                Object nbtTag = getTagMethod.invoke(nmsItemStack);

                if (nbtTag != null) {
                    Method hasKeyMethod = nbtTag.getClass().getMethod("hasKey", String.class);
                    boolean hasTags = (boolean) hasKeyMethod.invoke(nbtTag, "Tags");

                    if (hasTags) {
                        Method getStringMethod = nbtTag.getClass().getMethod("getString", String.class);
                        String tagsValue = (String) getStringMethod.invoke(nbtTag, "Tags");

                        return tagsValue.contains(tag);
                    }
                }
            }
        } catch (Exception e) {
        }
        return false;
    }


    private static String getServerVersion() {
        try {
            Class<?> craftServerClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
            Object server = craftServerClass.cast(org.bukkit.Bukkit.getServer());
            Field versionField = craftServerClass.getDeclaredField("SERVER_VERSION");
            versionField.setAccessible(true);
            String version = (String) versionField.get(server);
            return version;
        } catch (Exception e) {
            try {
                String version = org.bukkit.Bukkit.getBukkitVersion();
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d+\\.\\d+");
                java.util.regex.Matcher matcher = pattern.matcher(version);
                if (matcher.find()) {
                    String[] parts = matcher.group().split("\\.");
                    if (parts.length >= 2) {
                        int major = Integer.parseInt(parts[0]);
                        int minor = Integer.parseInt(parts[1]);
                        if (major == 1) {
                            if (minor == 8) return "v1_8_R3";
                            if (minor == 9) return "v1_9_R2";
                            if (minor == 10) return "v1_10_R1";
                            if (minor == 11) return "v1_11_R1";
                            if (minor == 12) return "v1_12_R1";
                            if (minor == 13) return "v1_13_R2";
                            if (minor == 14) return "v1_14_R1";
                            if (minor == 15) return "v1_15_R1";
                            if (minor == 16) return "v1_16_R3";
                            if (minor == 17) return "v1_17_R1";
                            if (minor == 18) return "v1_18_R2";
                            if (minor == 19) return "v1_19_R3";
                            if (minor == 20) return "v1_20_R2";
                            if (minor == 21) return "v1_21_R1";
                        }
                    }
                }
            } catch (Exception ex) {
            }
        }
        return "v1_8_R3";
    }


    private static boolean isVersionNewerOrEqual(int major, int minor) {
        if (majorVersion > major) {
            return true;
        } else if (majorVersion == major) {
            return minorVersion >= minor;
        }
        return false;
    }

    public static int getMajorVersion() {
        return majorVersion;
    }

    public static int getMinorVersion() {
        return minorVersion;
    }

    public static boolean isNBTAvailable() {
        return initialized;
    }
    

    public static Object getNBTData(ItemStack item, String key) {
        if (item == null) {
            return null;
        }

        if (NBTUtilAPI.isNbtApiAvailable()) {
            return NBTUtilAPI.getNBTData(item, key);
        }

        if (isVersionNewerOrEqual(1, 14) && item.hasItemMeta()) {
            return getNBTDataUsingPDC(item, key);
        }

        return getNBTDataUsingTraditionalNBT(item, key);
    }
    

    private static Object getNBTDataUsingPDC(ItemStack item, String key) {
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return null;
            }

            Method getPdcMethod = meta.getClass().getMethod("getPersistentDataContainer");
            getPdcMethod.setAccessible(true);
            Object pdc = getPdcMethod.invoke(meta);

            if (pdc != null) {
                NamespacedKey nsk = new NamespacedKey("kkfish", key);

                Method hasMethod = pdc.getClass().getMethod("has", NamespacedKey.class, PersistentDataType.class);
                hasMethod.setAccessible(true);
                boolean hasKey = (boolean) hasMethod.invoke(pdc, nsk, PersistentDataType.STRING);

                if (hasKey) {
                    Method getMethod = pdc.getClass().getMethod("get", NamespacedKey.class, PersistentDataType.class);
                    getMethod.setAccessible(true);
                    return getMethod.invoke(pdc, nsk, PersistentDataType.STRING);
                }
            }
        } catch (Exception e) {
        }
        return null;
    }
    

    private static Object getNBTDataUsingTraditionalNBT(ItemStack item, String key) {
        try {
            Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit." + getServerVersion() + ".inventory.CraftItemStack");
            Method asNMSCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);
            Object nmsItemStack = asNMSCopyMethod.invoke(null, item);

            if (nmsItemStack != null) {
                Method getTagMethod = nmsItemStack.getClass().getMethod("getTag");
                Object nbtTag = getTagMethod.invoke(nmsItemStack);

                if (nbtTag != null) {
                    Method hasKeyMethod = nbtTag.getClass().getMethod("hasKey", String.class);
                    boolean hasKey = (boolean) hasKeyMethod.invoke(nbtTag, key);

                    if (hasKey) {
                        Method getStringMethod = nbtTag.getClass().getMethod("getString", String.class);
                        return getStringMethod.invoke(nbtTag, key);
                    }
                }
            }
        } catch (Exception e) {
        }
        return null;
    }
}
