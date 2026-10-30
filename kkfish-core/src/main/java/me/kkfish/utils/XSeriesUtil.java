package me.kkfish.utils;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.Location;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class XSeriesUtil {

    private static boolean initialized = false;
    private static Class<?> xMaterialClass;
    private static Class<?> xSoundClass;

    // XSeries 14 起粒子统一走 ParticleDisplay，XParticle 只剩枚举查表
    private static Class<?> displayClass;
    private static Method xParticleOf;
    private static Method xParticleGet;
    private static Method displayOf;
    private static Method displayOffset;
    private static Method displaySpawn;
    private static Field displayCount;
    private static Field displayExtra;
    private static Method optionalIsPresent;
    private static Method optionalGet;

    private static void initialize() {
        if (initialized) return;

        try {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            xMaterialClass = contextClassLoader.loadClass("com.cryptomorin.xseries.XMaterial");
            xSoundClass = contextClassLoader.loadClass("com.cryptomorin.xseries.XSound");
            initialized = true;
        } catch (Throwable e) {
            try {
                xMaterialClass = Class.forName("com.cryptomorin.xseries.XMaterial");
                xSoundClass = Class.forName("com.cryptomorin.xseries.XSound");
                initialized = true;
            } catch (Throwable ex) {
            }
        }

        // 分两段加载：display 引用了 1.17 才有的 Vibration.Destination，低版本端一加载就抛
        // NoClassDefFoundError（Error 不是 Exception），合在一起会把名字映射也带崩
        try {
            optionalIsPresent = java.util.Optional.class.getMethod("isPresent");
            optionalGet = java.util.Optional.class.getMethod("get");

            Class<?> particleClass = Class.forName("com.cryptomorin.xseries.particles.XParticle");
            xParticleOf = particleClass.getMethod("of", String.class);
            xParticleGet = particleClass.getMethod("get");
        } catch (Throwable ignored) {
        }

        // 全部方法都拿到了才认这个类，避免 displayClass 非空但某个 Method 为 null，每次出粒子都空指针
        try {
            Class<?> displayType = Class.forName("com.cryptomorin.xseries.particles.ParticleDisplay");
            Method of = displayType.getMethod("display", Location.class, Particle.class);
            Method offset = displayType.getMethod("offset", double.class, double.class, double.class);
            Method spawn = displayType.getMethod("spawn");
            Field count = displayType.getField("count");
            Field extra = displayType.getField("extra");

            displayClass = displayType;
            displayOf = of;
            displayOffset = offset;
            displaySpawn = spawn;
            displayCount = count;
            displayExtra = extra;
        } catch (Throwable ignored) {
        }
    }

    public static Material parseMaterial(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return null;
        }
        
        try {
            Material material = Material.getMaterial(materialName);
            if (material != null) {
                return material;
            }
        } catch (Exception e) {
        }
        
        try {
            Material material = Material.getMaterial(materialName.toUpperCase());
            if (material != null) {
                return material;
            }
        } catch (Exception e) {
        }
        
        initialize();
        if (initialized && xMaterialClass != null) {
            try {
                Method matchMethod = xMaterialClass.getMethod("matchXMaterial", String.class);
                Object optionalXMaterial = matchMethod.invoke(null, materialName);
                
                Method isPresentMethod = optionalXMaterial.getClass().getMethod("isPresent");
                if (!(Boolean) isPresentMethod.invoke(optionalXMaterial)) {
                    return null;
                }
                
                Method getMethod = optionalXMaterial.getClass().getMethod("get");
                Object xMaterial = getMethod.invoke(optionalXMaterial);
                
                Method parseMaterialMethod = xMaterial.getClass().getMethod("parseMaterial");
                Material material = (Material) parseMaterialMethod.invoke(xMaterial);
                if (material != null) {
                    return material;
                }
            } catch (Exception e) {
            }
        }
        
        return getAlternativeMaterial(materialName);
    }

    public static Material getMaterial(String materialEnumName) {
        if (materialEnumName == null || materialEnumName.isEmpty()) {
            return getFallbackMaterial();
        }
        
        try {
            Material material = Material.getMaterial(materialEnumName);
            if (material != null) {
                return material;
            }
        } catch (Exception e) {
        }
        
        initialize();
        if (initialized && xMaterialClass != null) {
            try {
                Field field = xMaterialClass.getField(materialEnumName);
                Object xMaterial = field.get(null);
                
                Method parseMaterialMethod = xMaterial.getClass().getMethod("parseMaterial");
                Material material = (Material) parseMaterialMethod.invoke(xMaterial);
                if (material != null) {
                    return material;
                }
            } catch (Exception e) {
            }
        }
        
        return getAlternativeMaterial(materialEnumName);
    }

    private static Material getMaterialByName(String materialName) {
        try {
            return Material.getMaterial(materialName);
        } catch (Exception e) {
            return null;
        }
    }

    private static Material getAlternativeMaterial(String materialName) {
        try {
            Material material = Material.getMaterial(materialName);
            if (material != null) {
                return material;
            }
            
            if (materialName.contains("_WOOL")) {
                Material woolMaterial = Material.getMaterial("WOOL");
                if (woolMaterial != null) {
                    return woolMaterial;
                }
            }
            
            if (materialName.contains("_STAINED_GLASS_PANE")) {
                Material glassPaneMaterial = Material.getMaterial("GLASS_PANE");
                if (glassPaneMaterial != null) {
                    return glassPaneMaterial;
                }
            }
            
            if (materialName.contains("_STAINED_GLASS")) {
                Material glassMaterial = Material.getMaterial("GLASS");
                if (glassMaterial != null) {
                    return glassMaterial;
                }
            }
            
            if (materialName.contains("_LOG")) {
                Material logMaterial = Material.getMaterial("LOG");
                if (logMaterial != null) {
                    return logMaterial;
                }
            }
            
            if (materialName.contains("_WOOD")) {
                Material oakWoodMaterial = Material.getMaterial("OAK_WOOD");
                if (oakWoodMaterial != null) {
                    return oakWoodMaterial;
                }
                Material woodMaterial = Material.getMaterial("WOOD");
                if (woodMaterial != null) {
                    return woodMaterial;
                }
            }
            
            if (materialName.contains("_LEAVES")) {
                Material oakLeavesMaterial = Material.getMaterial("OAK_LEAVES");
                if (oakLeavesMaterial != null) {
                    return oakLeavesMaterial;
                }
                Material leavesMaterial = Material.getMaterial("LEAVES");
                if (leavesMaterial != null) {
                    return leavesMaterial;
                }
            }
            
            if (materialName.contains("_PLANKS")) {
                Material oakPlanksMaterial = Material.getMaterial("OAK_PLANKS");
                if (oakPlanksMaterial != null) {
                    return oakPlanksMaterial;
                }
                Material woodMaterial = Material.getMaterial("WOOD");
                if (woodMaterial != null) {
                    return woodMaterial;
                }
            }
            
            Material[] commonMaterials = {
                Material.STONE,
                Material.DIRT,
                Material.GRASS,
                getMaterialByName("OAK_WOOD"),
                getMaterialByName("WOOD"),
                Material.COAL,
                Material.IRON_INGOT,
                Material.GOLD_INGOT,
                Material.DIAMOND
            };
            
            for (Material commonMaterial : commonMaterials) {
                if (commonMaterial != null) {
                    return commonMaterial;
                }
            }
            
            return getFallbackMaterial();
        } catch (Exception e) {
            return getFallbackMaterial();
        }
    }

    private static Material getFallbackMaterial() {
        try {
            return Material.STONE;
        } catch (Exception e) {
            return null;
        }
    }

    public static void playSound(Location location, String soundName, float volume, float pitch) {
        initialize();
        if (!initialized || xSoundClass == null) {
            try {
                Sound sound = Sound.valueOf(soundName);
                if (location.getWorld() != null) {
                    location.getWorld().playSound(location, sound, volume, pitch);
                }
            } catch (Exception e) {
            }
            return;
        }

        try {
            Method matchMethod = xSoundClass.getMethod("matchXSound", String.class);
            Object optionalXSound = matchMethod.invoke(null, soundName);
            
            Method isPresentMethod = optionalXSound.getClass().getMethod("isPresent");
            if (!(Boolean) isPresentMethod.invoke(optionalXSound)) {
                return;
            }
            
            Method getMethod = optionalXSound.getClass().getMethod("get");
            Object xSound = getMethod.invoke(optionalXSound);
            
            Method playMethod = xSound.getClass().getMethod("play", Location.class, float.class, float.class);
            playMethod.invoke(xSound, location, volume, pitch);
        } catch (Exception e) {
            try {
                Sound sound = Sound.valueOf(soundName);
                if (location.getWorld() != null) {
                    location.getWorld().playSound(location, sound, volume, pitch);
                }
            } catch (Exception ex) {
            }
        }
    }

    public static boolean isXSeriesLoaded() {
        initialize();
        return initialized;
    }

    public static void spawnParticle(Location location, String particleName, int count, double spreadX, double spreadY, double spreadZ, double extra) {
        if (location == null || location.getWorld() == null) return;

        initialize();
        Particle particle = resolveParticle(particleName);
        if (particle == null) return;

        if (spawnByDisplay(location, particle, count, spreadX, spreadY, spreadZ, extra)) return;

        // 需要数据的粒子（BLOCK_CRACK 这类）在这里没数据会直接抛，不能让它把调用方带崩
        try {
            location.getWorld().spawnParticle(particle, location, count, spreadX, spreadY, spreadZ, (float) extra);
        } catch (Exception ignored) {
        }
    }

    public static void spawnParticleWithData(Location location, String particleName, int count, double spreadX, double spreadY, double spreadZ, double extra, Object data) {
        if (location == null || location.getWorld() == null) return;

        initialize();
        Particle particle = resolveParticle(particleName);
        if (particle == null) return;

        try {
            // 调用方传的 data 是 Bukkit 的 ItemStack / BlockData，ParticleDisplay 要的是它自己的 ParticleData 接口，
            // 中间没有现成转换，带数据的一律走 Bukkit 原生
            if (data != null) {
                location.getWorld().spawnParticle(particle, location, count, spreadX, spreadY, spreadZ, (float) extra, data);
            } else if (!spawnByDisplay(location, particle, count, spreadX, spreadY, spreadZ, extra)) {
                location.getWorld().spawnParticle(particle, location, count, spreadX, spreadY, spreadZ, (float) extra);
            }
        } catch (Exception ignored) {
        }
    }

    /** 用 ParticleDisplay 出粒子；拿不到 display 类就返回 false，由调用方退回 Bukkit 原生 */
    private static boolean spawnByDisplay(Location location, Particle particle, int count, double spreadX, double spreadY, double spreadZ, double extra) {
        if (displayClass == null) return false;

        try {
            Object display = displayOf.invoke(null, location, particle);
            if (display == null) return false;

            displayCount.setInt(display, count);
            displayExtra.setDouble(display, extra);
            displayOffset.invoke(display, spreadX, spreadY, spreadZ);
            displaySpawn.invoke(display);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 粒子名 → Bukkit Particle：先过 XParticle 别名表（能认旧名和新版改名），再退回枚举原名 */
    private static Particle resolveParticle(String particleName) {
        if (particleName == null || particleName.isEmpty()) return null;

        if (xParticleOf != null) {
            try {
                Object optional = xParticleOf.invoke(null, particleName);
                if (optional != null && (Boolean) optionalIsPresent.invoke(optional)) {
                    Object xParticle = optionalGet.invoke(optional);
                    Object particle = xParticleGet.invoke(xParticle);
                    if (particle instanceof Particle) return (Particle) particle;
                }
            } catch (Exception ignored) {
            }
        }

        try {
            return Particle.valueOf(particleName.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isWaterBlock(Block block) {
        if (block == null) return false;
        
        Material type = block.getType();
        if (type == Material.WATER) return true;
        
        try {
            Material stationaryWater = Material.valueOf("STATIONARY_WATER");
            if (type == stationaryWater) return true;
        } catch (Exception e) {
        }
        
        try {
            Material bubbleColumn = Material.valueOf("BUBBLE_COLUMN");
            if (type == bubbleColumn) return true;
        } catch (Exception e) {
        }
        
        try {
            Object blockData = block.getBlockData();
            if (blockData != null) {
                Class<?> waterloggedClass = Class.forName("org.bukkit.block.data.Waterlogged");
                if (waterloggedClass.isInstance(blockData)) {
                    Method isWaterloggedMethod = waterloggedClass.getMethod("isWaterlogged");
                    Boolean waterlogged = (Boolean) isWaterloggedMethod.invoke(blockData);
                    if (waterlogged != null && waterlogged) return true;
                }
            }
        } catch (Exception e) {
        }
        
        Material belowType = block.getRelative(org.bukkit.block.BlockFace.DOWN).getType();
        if (belowType == Material.WATER) return true;
        
        try {
            Material stationaryWater = Material.valueOf("STATIONARY_WATER");
            if (belowType == stationaryWater) return true;
        } catch (Exception e) {
        }
        
        try {
            Material bubbleColumn = Material.valueOf("BUBBLE_COLUMN");
            if (belowType == bubbleColumn) return true;
        } catch (Exception e) {
        }
        
        return false;
    }

    public static boolean isLavaBlock(Block block) {
        if (block == null) return false;
        
        Material type = block.getType();
        if (type == Material.LAVA) return true;
        
        try {
            Material stationaryLava = Material.valueOf("STATIONARY_LAVA");
            if (type == stationaryLava) return true;
        } catch (Exception e) {
        }
        
        Material belowType = block.getRelative(org.bukkit.block.BlockFace.DOWN).getType();
        if (belowType == Material.LAVA) return true;
        
        try {
            Material stationaryLava = Material.valueOf("STATIONARY_LAVA");
            if (belowType == stationaryLava) return true;
        } catch (Exception e) {
        }
        
        return false;
    }

    public static boolean isLiquidBlock(Block block) {
        return isWaterBlock(block) || isLavaBlock(block);
    }

    public static Particle getParticle(String particleName) {
        initialize();
        return resolveParticle(particleName);
    }

    public static Particle getParticle(String particleName, Particle fallback) {
        Particle particle = getParticle(particleName);
        return particle != null ? particle : fallback;
    }
}
