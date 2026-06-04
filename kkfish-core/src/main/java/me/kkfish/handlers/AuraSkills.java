package me.kkfish.handlers;

import org.bukkit.Bukkit;
import org.bukkit.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.Bukkit;
import me.kkfish.kkfish;
import org.bukkit.Bukkit;
import me.kkfish.managers.Config;

import org.bukkit.Bukkit;
import java.lang.reflect.*;
import org.bukkit.Bukkit;
import java.util.UUID;

public class AuraSkills {

    private final kkfish plugin;
    private Config config;
    private boolean isAuraSkillsEnabled = false;
    private Object auraSkillsApi = null; // 用Object避免硬依赖
    private Class<?> auraSkillsApiClass = null;
    private Class<?> skillsClass = null;
    private Object fishingSkill = null;

    public AuraSkills(kkfish plugin) {
        this.plugin = plugin;
        this.config = plugin.getCustomConfig();
        initialize();
    }

    private void initialize() {
        Plugin auraSkillsPlugin = Bukkit.getPluginManager().getPlugin("AuraSkills");
        if (auraSkillsPlugin == null) {
            isAuraSkillsEnabled = false;
            config.debugLog(plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_not_found", "AuraSkills plugin not found, fishing experience feature temporarily unavailable"));
            return;
        }
        
        config.debugLog(plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_found", "AuraSkills plugin found!"));
        boolean connected = setupApiConnection();
        if (connected) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_connected", "AuraSkills API connection successful! Fishing experience feature enabled~"));
        }
    }
    
    private boolean tryReconnect() {
        Plugin auraSkillsPlugin = Bukkit.getPluginManager().getPlugin("AuraSkills");
        if (auraSkillsPlugin == null) {
            return false;
        }
        
        boolean reconnected = setupApiConnection();
        if (reconnected) {
            config.debugLog(plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_reconnected", "AuraSkills reconnection successful!"));
            return true;
        }
        config.debugLog(plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_reconnect_failed", "AuraSkills reconnection failed"));
        return false;
    }
    
    private boolean setupApiConnection() {
        try {
            auraSkillsApiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            skillsClass = Class.forName("dev.aurelium.auraskills.api.skill.Skills");
            
            Method getApiMethod = auraSkillsApiClass.getMethod("get");
            auraSkillsApi = getApiMethod.invoke(null);
            
            Method getSkillMethod = skillsClass.getMethod("valueOf", String.class);
            fishingSkill = getSkillMethod.invoke(null, "FISHING");
            
            isAuraSkillsEnabled = true;
            return true;
        } catch (ClassNotFoundException e) {
            config.debugLog(plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_class_not_found", "AuraSkills class not found, version incompatibility possible: %s", e.getMessage()));
            isAuraSkillsEnabled = false;
        } catch (Exception e) {
            if (e.getCause() instanceof IllegalStateException && e.getCause().getMessage() != null && 
                e.getCause().getMessage().contains("not initialized")) {
                config.debugLog(plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_not_initialized", "AuraSkills API not initialized, will try again later"));
            } else {
                config.debugLog(plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_connection_error", "Error connecting to AuraSkills: %s", e.getMessage()));
            }
        }
        return false;
    }

    public double addFishingExperience(Player player, double fishSize, int fishRarity) {
        if (player == null) return -1;

        if (!checkAuraSkillsConnection()) return -1;

        try {
            Object user = getUserObject(player);
            if (user == null) return -1;
            
            double baseXp = 5.0;
            double sizeMultiplier = Math.max(1.0, fishSize / 100.0);
            double rarityMultiplier = getRarityMultiplier(fishRarity);
            double xpToAdd = baseXp * sizeMultiplier * rarityMultiplier;
            
            if (addSkillXp(user, xpToAdd)) {
                config.debugLog(plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_added_xp", "Added %.1f fishing experience to %s", xpToAdd, player.getName()));
                return xpToAdd;
            }
        } catch (Exception e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_add_xp_error", "Error adding fishing experience: %s", e.getMessage()));
            if (config.isDebugMode()) e.printStackTrace();
        }
        return -1;
    }

    public int getFishingLevel(Player player) {
        if (player == null) return 0;

        if (!checkAuraSkillsConnection()) return 0;

        Object user = getUserObject(player);
        if (user == null) return 0;
        
        try {
            Class<?> userClass = user.getClass();
            Method getLevelMethod = userClass.getMethod("getSkillLevel", skillsClass);
            int level = (int) getLevelMethod.invoke(user, fishingSkill);
            config.debugLog(plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_fishing_level", "%s fishing level: %s", player.getName(), level));
            return level;
        } catch (Exception e) {
            config.debugLog(plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_get_level_error", "Error getting fishing level: %s", e.getMessage()));
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("aura_skills_get_level_error", "Error getting fishing level: %s", e.getMessage()));
        }
        return 0;
    }
    
    private boolean checkAuraSkillsConnection() {
        if (!isAuraSkillsEnabled) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin("AuraSkills");
            if (plugin != null) {
                return tryReconnect();
            }
            return false;
        }
        return true;
    }
    
    private Object getUserObject(Player player) {
        if (auraSkillsApi == null) return null;
        
        try {
            Method getUserMethod = auraSkillsApiClass.getMethod("getUser", UUID.class);
            return getUserMethod.invoke(auraSkillsApi, player.getUniqueId());
        } catch (Exception e) {
            config.debugLog("获取用户对象出错: " + e.getMessage());
            return null;
        }
    }
    
    private boolean addSkillXp(Object user, double xp) {
        try {
            Class<?> userClass = user.getClass();
            
            try {
                Method addXpMethod = userClass.getMethod("addSkillXp", skillsClass, double.class);
                addXpMethod.invoke(user, fishingSkill, xp);
                return true;
            } catch (NoSuchMethodException e) {
                for (Method m : userClass.getMethods()) {
                    if (m.getName().equals("addSkillXp")) {
                        Class<?>[] paramTypes = m.getParameterTypes();
                        if (paramTypes.length == 2) {
                            if (isNumber(paramTypes[1])) {
                                Object[] args = createMethodArgs(paramTypes[1], xp);
                                if (args != null) {
                                    m.invoke(user, args);
                                    return true;
                                }
                            }
                        } else if (paramTypes.length == 3) {
                            if (isNumber(paramTypes[1]) && paramTypes[2] == boolean.class) {
                                Object[] args = createMethodArgs(paramTypes[1], xp, true);
                                if (args != null) {
                                    m.invoke(user, args);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            config.debugLog("加经验时出错: " + e.getMessage());
        }
        return false;
    }
    
    private boolean isNumber(Class<?> type) {
        return type == double.class || type == float.class || type == int.class || type == long.class;
    }
    
    private Object[] createMethodArgs(Class<?> numType, double value, Object... extras) {
        Object numValue;
        if (numType == int.class) {
            numValue = (int) value;
        } else if (numType == long.class) {
            numValue = (long) value;
        } else if (numType == float.class) {
            numValue = (float) value;
        } else {
            numValue = value;
        }
        
        Object[] args = new Object[2 + extras.length];
        args[0] = fishingSkill;
        args[1] = numValue;
        System.arraycopy(extras, 0, args, 2, extras.length);
        return args;
    }

    public double getRareFishBonus(int fishingLevel) {
        double bonus = 1.0 + (fishingLevel * 0.01);
        config.debugLog("钓鱼等级: " + fishingLevel + ", 稀有鱼概率加成: " + String.format("%.2f", bonus) + "倍");
        return bonus;
    }

    private double getRarityMultiplier(int rarity) {
        String rarityName = getRarityNameByLevel(rarity);
        return config.getRarityAuraSkillsXpMultiplier(rarityName);
    }

    private String getRarityNameByLevel(int rarity) {
        if (rarity >= 5) return "legendary";
        if (rarity == 4) return "epic";
        if (rarity == 3) return "rare";
        if (rarity == 2) return "uncommon";
        return "common";
    }

    public boolean isAuraSkillsEnabled() {
        return isAuraSkillsEnabled;
    }
}