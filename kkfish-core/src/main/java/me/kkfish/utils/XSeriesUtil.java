package me.kkfish.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class XSeriesUtil {

    private static boolean initialized = false;
    private static Class<?> xMaterialClass;
    private static Class<?> xSoundClass;
    private static Class<?> xParticleClass;

    private static void initialize() {
        if (!initialized) {
            try {
                try {
                    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
                    xMaterialClass = contextClassLoader.loadClass("com.cryptomorin.xseries.XMaterial");
                    xSoundClass = contextClassLoader.loadClass("com.cryptomorin.xseries.XSound");
                    xParticleClass = contextClassLoader.loadClass("com.cryptomorin.xseries.XParticle");
                    initialized = true;
                    return;
                } catch (ClassNotFoundException e) {
                }
                
                try {
                    xMaterialClass = Class.forName("com.cryptomorin.xseries.XMaterial");
                    xSoundClass = Class.forName("com.cryptomorin.xseries.XSound");
                    xParticleClass = Class.forName("com.cryptomorin.xseries.XParticle");
                    initialized = true;
                    return;
                } catch (ClassNotFoundException e) {
                }
            } catch (Exception e) {
            }
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
        
        if (xParticleClass != null) {
            try {
                Method matchMethod = xParticleClass.getMethod("matchXParticle", String.class);
                Object optionalXParticle = matchMethod.invoke(null, particleName);
                
                Method isPresentMethod = optionalXParticle.getClass().getMethod("isPresent");
                if (!(Boolean) isPresentMethod.invoke(optionalXParticle)) return;
                
                Method getMethod = optionalXParticle.getClass().getMethod("get");
                Object xParticle = getMethod.invoke(optionalXParticle);
                
                Method spawnMethod = xParticle.getClass().getMethod("spawn", Location.class, int.class, double.class, double.class, double.class, double.class);
                spawnMethod.invoke(xParticle, location, count, spreadX, spreadY, spreadZ, extra);
                return;
            } catch (Exception e) {
            }
        }
        
        try {
            org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleName.toUpperCase());
            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(particle, location, count, spreadX, spreadY, spreadZ, (float) extra);
            }
        } catch (Exception e) {
        }
    }

    public static void spawnParticleWithData(Location location, String particleName, int count, double spreadX, double spreadY, double spreadZ, double extra, Object data) {
        if (location == null || location.getWorld() == null) return;
        
        initialize();
        
        if (xParticleClass != null) {
            try {
                Method matchMethod = xParticleClass.getMethod("matchXParticle", String.class);
                Object optionalXParticle = matchMethod.invoke(null, particleName);
                
                Method isPresentMethod = optionalXParticle.getClass().getMethod("isPresent");
                if (!(Boolean) isPresentMethod.invoke(optionalXParticle)) return;
                
                Method getMethod = optionalXParticle.getClass().getMethod("get");
                Object xParticle = getMethod.invoke(optionalXParticle);
                
                try {
                    Method spawnMethod = xParticle.getClass().getMethod("spawn", Location.class, int.class, double.class, double.class, double.class, double.class, Object.class);
                    spawnMethod.invoke(xParticle, location, count, spreadX, spreadY, spreadZ, extra, data);
                    return;
                } catch (NoSuchMethodException e) {
                    Method spawnMethod = xParticle.getClass().getMethod("spawn", Location.class, int.class, double.class, double.class, double.class, double.class);
                    spawnMethod.invoke(xParticle, location, count, spreadX, spreadY, spreadZ, extra);
                    return;
                }
            } catch (Exception e) {
            }
        }
        
        try {
            org.bukkit.Particle particle = org.bukkit.Particle.valueOf(particleName.toUpperCase());
            if (location.getWorld() != null) {
                if (data != null) {
                    location.getWorld().spawnParticle(particle, location, count, spreadX, spreadY, spreadZ, (float) extra, data);
                } else {
                    location.getWorld().spawnParticle(particle, location, count, spreadX, spreadY, spreadZ, (float) extra);
                }
            }
        } catch (Exception e) {
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

    public static org.bukkit.Particle getParticle(String particleName) {
        if (particleName == null || particleName.isEmpty()) return null;
        
        initialize();
        
        if (xParticleClass != null) {
            try {
                Method matchMethod = xParticleClass.getMethod("matchXParticle", String.class);
                Object optionalXParticle = matchMethod.invoke(null, particleName);
                
                Method isPresentMethod = optionalXParticle.getClass().getMethod("isPresent");
                if ((Boolean) isPresentMethod.invoke(optionalXParticle)) {
                    Method getMethod = optionalXParticle.getClass().getMethod("get");
                    Object xParticle = getMethod.invoke(optionalXParticle);
                    
                    Method parseMethod = xParticle.getClass().getMethod("get");
                    Object particle = parseMethod.invoke(xParticle);
                    if (particle instanceof org.bukkit.Particle) {
                        return (org.bukkit.Particle) particle;
                    }
                }
            } catch (Exception e) {
            }
        }
        
        try {
            return org.bukkit.Particle.valueOf(particleName.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    public static org.bukkit.Particle getParticle(String particleName, org.bukkit.Particle fallback) {
        org.bukkit.Particle particle = getParticle(particleName);
        return particle != null ? particle : fallback;
    }
}
