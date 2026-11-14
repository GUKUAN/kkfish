package me.kkfish.handlers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.kkfish.kkfish;
import me.kkfish.managers.Config;

import java.lang.reflect.*;
import java.util.Locale;
import java.util.UUID;

public class AuraSkills {

    private final kkfish plugin;
    private Config config;
    private boolean isAuraSkillsEnabled = false;
    private Object auraSkillsApi = null; // 用Object避免硬依赖
    private Class<?> auraSkillsApiClass = null;
    private Class<?> skillsUserClass = null;
    private Class<?> skillsClass = null;
    private Class<?> skillClass = null;
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
            try {
                skillsUserClass = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser");
            } catch (ClassNotFoundException ignored) {
            }
            skillsClass = Class.forName("dev.aurelium.auraskills.api.skill.Skills");
            try {
                skillClass = Class.forName("dev.aurelium.auraskills.api.skill.Skill");
            } catch (ClassNotFoundException ignored) {
            }
            
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

    public double addFishingExperience(Player player, double fishSize, double fishSizeMin,
                                       double fishSizeMax, String fishLevel) {
        if (player == null) return -1;

        if (!checkAuraSkillsConnection()) return -1;

        try {
            Object user = getUserObject(player);
            if (user == null) return -1;
            
            double baseXp = config.getAuraSkillsBaseXp();
            double rarityMultiplier = getRarityMultiplier(fishLevel);
            double xpToAdd = calculateFishingXp(baseXp, fishSize, fishSizeMin, fishSizeMax, rarityMultiplier);
            
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
            Class<?> userClass = skillsUserClass != null ? skillsUserClass : user.getClass();
            Method getLevelMethod = findSkillMethod(userClass, "getSkillLevel");
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
            Class<?> userClass = skillsUserClass != null ? skillsUserClass : user.getClass();
            if (skillClass != null) {
                try {
                    Method addXpMethod = findSkillMethod(userClass, "addSkillXp", skillClass, double.class);
                    addXpMethod.invoke(user, fishingSkill, xp);
                    return true;
                } catch (NoSuchMethodException ignored) {
                }
            }

            try {
                Method addXpMethod = findSkillMethod(userClass, "addSkillXp", skillsClass, double.class);
                addXpMethod.invoke(user, fishingSkill, xp);
                return true;
            } catch (NoSuchMethodException ignored) {
            }

            for (Method method : userClass.getMethods()) {
                Class<?>[] paramTypes = method.getParameterTypes();
                if (!method.getName().equals("addSkillXp") || paramTypes.length < 2
                        || !paramTypes[0].isInstance(fishingSkill) || !isNumber(paramTypes[1])) {
                    continue;
                }
                if (paramTypes.length == 2) {
                    method.invoke(user, createMethodArgs(paramTypes[1], xp));
                    return true;
                }
                if (paramTypes.length == 3 && paramTypes[2] == boolean.class) {
                    method.invoke(user, createMethodArgs(paramTypes[1], xp, true));
                    return true;
                }
            }
        } catch (Exception e) {
            config.debugLog("加经验时出错: " + e.getMessage());
        }
        return false;
    }

    static Method findSkillMethod(Class<?> userClass, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        return userClass.getMethod(methodName, parameterTypes);
    }

    private Method findSkillMethod(Class<?> userClass, String methodName) throws NoSuchMethodException {
        if (skillClass != null) {
            try {
                return findSkillMethod(userClass, methodName, skillClass);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return findSkillMethod(userClass, methodName, skillsClass);
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

    private double getRarityMultiplier(String fishLevel) {
        return config.getRarityAuraSkillsXpMultiplier(getRarityNameByLevel(fishLevel));
    }

    static double calculateFishingXp(double baseXp, double fishSize, double fishSizeMin,
                                     double fishSizeMax, double rarityMultiplier) {
        double averageFishSize = (fishSizeMin + fishSizeMax) / 2.0;
        double sizeMultiplier = averageFishSize > 0 ? fishSize / averageFishSize : 1.0;
        return baseXp * sizeMultiplier * rarityMultiplier;
    }

    static String getRarityNameByLevel(String fishLevel) {
        if (fishLevel == null || fishLevel.trim().isEmpty()) return "common";
        String rarityName = fishLevel.trim().toLowerCase(Locale.ROOT);
        int weightSeparator = rarityName.indexOf(':');
        return weightSeparator >= 0 ? rarityName.substring(0, weightSeparator).trim() : rarityName;
    }

    public boolean isAuraSkillsEnabled() {
        return isAuraSkillsEnabled;
    }
}
