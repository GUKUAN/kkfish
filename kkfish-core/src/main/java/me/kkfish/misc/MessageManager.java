package me.kkfish.misc;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.ChatColor;

import me.kkfish.kkfish;
import me.kkfish.managers.Config;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MessageManager {
    private static MessageManager instance;
    private final JavaPlugin plugin;
    private FileConfiguration msgConfig;
    private final Map<String, String> msgMap = new HashMap<>();
    private File msgFile;
    private String prefix = "&7&lkkfish > ";
    private String currLang = "zh_cn";
    private final Map<UUID, String> playerLangCache = new HashMap<>();
    
    private MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        updateLang();
        loadMessages();
    }

    public String getPrefix() {
        return prefix;
    }

    public static synchronized MessageManager getInstance(JavaPlugin plugin) {
        if (instance == null) {
            instance = new MessageManager(plugin);
        }
        return instance;
    }

    public void updateLang() {
        kkfish kkPlugin = (kkfish) plugin;
        Config cfg = kkPlugin.getCustomConfig();
        if (cfg != null) {
            FileConfiguration mainCfg = cfg.getMainConfig();
            if (mainCfg.contains("language.current")) {
                currLang = mainCfg.getString("language.current", "zh_cn");
            }
        }
        
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }
        msgFile = new File(langFolder, "message_" + currLang + ".yml");
    }

    public void loadMessages() {
        updateLang();
        
        if (!msgFile.exists()) {
            String resourcePath = "lang/message_" + currLang + ".yml";
            try {
                plugin.saveResource(resourcePath, false);
            } catch (IllegalArgumentException e) {
                try {
                    plugin.saveResource("lang/message_zh_cn.yml", false);
                    msgFile = new File(new File(plugin.getDataFolder(), "lang"), "message_zh_cn.yml");
                } catch (IllegalArgumentException ex) {
                    kkfish.log("§e" + getMessageWithoutPrefix("log.language_load_failed", "Unable to load language file, message functionality may not work properly"));
                    return;
                }
            }
        }

        completeLanguageFile();

        msgConfig = YamlConfiguration.loadConfiguration(msgFile);
        msgMap.clear();
        playerLangCache.clear();

        if (msgConfig.contains("prefix")) {
            prefix = msgConfig.getString("prefix", "[kkfish] ");
            prefix = prefix.replace('&', '§');
        }

        for (String key : msgConfig.getKeys(true)) {
            if (!key.equals("prefix") && msgConfig.isString(key)) {
                msgMap.put(key, msgConfig.getString(key));
            }
        }
    }

    public String getPlayerLang(Player player) {
        if (player == null) return currLang;
        
        UUID playerId = player.getUniqueId();
        if (playerLangCache.containsKey(playerId)) {
            return playerLangCache.get(playerId);
        }
        
        kkfish kkPlugin = (kkfish) plugin;
        String savedLang = kkPlugin.getDB().getPlayerLanguage(playerId.toString());
        String lang = chooseMessageLang(currLang, savedLang);
        playerLangCache.put(playerId, lang);
        return lang;
    }

    static String chooseMessageLang(String currentLang, String playerLang) {
        if (currentLang == null || currentLang.isEmpty()) {
            currentLang = "zh_cn";
        }
        if (playerLang == null || playerLang.isEmpty()) {
            return currentLang;
        }
        if ("zh_cn".equals(playerLang) && !"zh_cn".equals(currentLang)) {
            return currentLang;
        }
        return playerLang;
    }
    
    public void setPlayerLang(Player player, String lang) {
        if (player == null || lang == null || lang.isEmpty()) return;
        
        UUID playerId = player.getUniqueId();
        playerLangCache.put(playerId, lang);
        
        kkfish kkPlugin = (kkfish) plugin;
        kkPlugin.getDB().setPlayerLanguage(playerId.toString(), lang);
    }
    
    public void clearPlayerLangCache(Player player) {
        if (player == null) return;
        playerLangCache.remove(player.getUniqueId());
    }
    
    public String getMessage(String key, String defaultValue) {
        String message = msgMap.getOrDefault(key, defaultValue);
        message = ChatColor.translateAlternateColorCodes('&', message);
        if (message.startsWith(prefix)) return message;
        return prefix + message;
    }
    
    public String getMessage(Player player, String key, String defaultValue) {
        if (player == null) return getMessage(key, defaultValue);
        
        String playerLang = getPlayerLang(player);
        
        if (playerLang.equals(currLang)) return getMessage(key, defaultValue);
        
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File playerLangFile = new File(langFolder, "message_" + playerLang + ".yml");
        
        if (!playerLangFile.exists()) {
            return getMessage(key, defaultValue);
        }
        
        try {
            FileConfiguration playerLangConfig = YamlConfiguration.loadConfiguration(playerLangFile);
            String message = playerLangConfig.getString(key, defaultValue);
            
            message = ChatColor.translateAlternateColorCodes('&', message);
            
            String playerPrefix = playerLangConfig.getString("prefix", prefix);
            playerPrefix = ChatColor.translateAlternateColorCodes('&', playerPrefix);
            
            if (message.startsWith(playerPrefix)) return message;
            return playerPrefix + message;
        } catch (Exception e) {
            kkfish.log("§e" + getMessageWithoutPrefix("log.player_language_load_failed", "Failed to load player language file: ") + e.getMessage());
            return getMessage(key, defaultValue);
        }
    }
    
    public String getMessage(String key, String defaultValue, Object... args) {
        String message = msgMap.getOrDefault(key, defaultValue);
        message = String.format(message, args);
        message = ChatColor.translateAlternateColorCodes('&', message);
        if (message.startsWith(prefix)) return message;
        return prefix + message;
    }
    
    public String getMessage(Player player, String key, String defaultValue, Object... args) {
        if (player == null) return getMessage(key, defaultValue, args);
        
        String playerLang = getPlayerLang(player);
        
        if (playerLang.equals(currLang)) return getMessage(key, defaultValue, args);
        
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File playerLangFile = new File(langFolder, "message_" + playerLang + ".yml");
        
        if (!playerLangFile.exists()) {
            return getMessage(key, defaultValue, args);
        }
        
        try {
            FileConfiguration playerLangConfig = YamlConfiguration.loadConfiguration(playerLangFile);
            String message = playerLangConfig.getString(key, defaultValue);
            message = String.format(message, args);
            
            message = ChatColor.translateAlternateColorCodes('&', message);
            
            String playerPrefix = playerLangConfig.getString("prefix", prefix);
            playerPrefix = ChatColor.translateAlternateColorCodes('&', playerPrefix);
            
            if (message.startsWith(playerPrefix)) return message;
            return playerPrefix + message;
        } catch (Exception e) {
            kkfish.log("§e" + getMessageWithoutPrefix("log.player_language_load_failed", "Failed to load player language file: ") + e.getMessage());
            return getMessage(key, defaultValue, args);
        }
    }
    
    public String getMessageWithoutPrefix(String key, String defaultValue) {
        String message = msgMap.getOrDefault(key, defaultValue);
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public String getMessageWithoutPrefix(Player player, String key, String defaultValue) {
        if (player == null) return getMessageWithoutPrefix(key, defaultValue);
        
        String playerLang = getPlayerLang(player);
        
        if (playerLang.equals(currLang)) return getMessageWithoutPrefix(key, defaultValue);
        
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File playerLangFile = new File(langFolder, "message_" + playerLang + ".yml");
        
        if (!playerLangFile.exists()) {
            return getMessageWithoutPrefix(key, defaultValue);
        }
        
        try {
            FileConfiguration playerLangConfig = YamlConfiguration.loadConfiguration(playerLangFile);
            String message = playerLangConfig.getString(key, defaultValue);
            
            return ChatColor.translateAlternateColorCodes('&', message);
        } catch (Exception e) {
            kkfish.log("§e" + getMessageWithoutPrefix("log.player_language_load_failed", "Failed to load player language file: ") + e.getMessage());
            return getMessageWithoutPrefix(key, defaultValue);
        }
    }
    
    public String getMessageWithoutPrefix(String key, String defaultValue, Object... args) {
        String message = msgMap.getOrDefault(key, defaultValue);
        message = String.format(message, args);
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    public String getMessageWithoutPrefix(Player player, String key, String defaultValue, Object... args) {
        if (player == null) return getMessageWithoutPrefix(key, defaultValue, args);
        
        String playerLang = getPlayerLang(player);
        
        if (playerLang.equals(currLang)) return getMessageWithoutPrefix(key, defaultValue, args);
        
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File playerLangFile = new File(langFolder, "message_" + playerLang + ".yml");
        
        if (!playerLangFile.exists()) {
            return getMessageWithoutPrefix(key, defaultValue, args);
        }
        
        try {
            FileConfiguration playerLangConfig = YamlConfiguration.loadConfiguration(playerLangFile);
            String message = playerLangConfig.getString(key, defaultValue);
            message = String.format(message, args);
            
            return ChatColor.translateAlternateColorCodes('&', message);
        } catch (Exception e) {
            kkfish.log("§e" + getMessageWithoutPrefix("log.player_language_load_failed", "Failed to load player language file: ") + e.getMessage());
            return getMessageWithoutPrefix(key, defaultValue, args);
        }
    }

    public List<String> getMessageList(String key, List<String> defaultValue) {
        if (msgConfig.contains(key)) {
            return msgConfig.getStringList(key);
        }
        return defaultValue;
    }
    
    public List<String> getMessageList(Player player, String key, List<String> defaultValue) {
        if (player == null) return getMessageList(key, defaultValue);
        
        String playerLang = getPlayerLang(player);
        
        if (playerLang.equals(currLang)) return getMessageList(key, defaultValue);
        
        File langFolder = new File(plugin.getDataFolder(), "lang");
        File playerLangFile = new File(langFolder, "message_" + playerLang + ".yml");
        
        if (!playerLangFile.exists()) {
            return getMessageList(key, defaultValue);
        }
        
        try {
            FileConfiguration playerLangConfig = YamlConfiguration.loadConfiguration(playerLangFile);
            if (playerLangConfig.contains(key)) {
                return playerLangConfig.getStringList(key);
            }
            return defaultValue;
        } catch (Exception e) {
            kkfish.log("§e" + getMessageWithoutPrefix("log.player_language_load_failed", "Failed to load player language file: ") + e.getMessage());
            return getMessageList(key, defaultValue);
        }
    }

    public void saveMessage(String key, String value) {
        msgConfig.set(key, value);
        try {
            msgConfig.save(msgFile);
            msgMap.put(key, value);
        } catch (IOException e) {
            kkfish.log("§e" + getMessageWithoutPrefix("log.save_message_failed", "Failed to save message configuration: ") + e.getMessage());
        }
    }
    
    public void completeLanguageFile() {
        completeLanguageFile(currLang);
    }

    public boolean completeLanguageFile(String targetLang) {
        if (targetLang == null || targetLang.isEmpty()) {
            targetLang = currLang;
        }

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        File defaultLangFile = new File(langFolder, "message_en_us.yml");
        if (!defaultLangFile.exists()) {
            try {
                plugin.saveResource("lang/message_en_us.yml", false);
            } catch (IllegalArgumentException e) {
                kkfish.log("§e" + "Default language file (en_us) not found");
                return false;
            }
        }

        File targetLangFile = new File(langFolder, "message_" + targetLang + ".yml");
        if (!targetLangFile.exists()) {
            try {
                plugin.saveResource("lang/message_" + targetLang + ".yml", false);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        try {
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(defaultLangFile);
            FileConfiguration targetConfig = YamlConfiguration.loadConfiguration(targetLangFile);

            Set<String> missingKeys = new HashSet<>();
            for (String key : defaultConfig.getKeys(true)) {
                if (defaultConfig.isString(key) && !targetConfig.contains(key)) {
                    missingKeys.add(key);
                }
            }

            if (missingKeys.isEmpty()) {
                return true;
            }

            for (String key : missingKeys) {
                targetConfig.set(key, defaultConfig.get(key));
            }

            targetConfig.save(targetLangFile);
            kkfish.log(this.getMessageWithoutPrefix("log.message_complete_keys_added", "Added " + missingKeys.size() + " missing language keys to " + targetLang + " language file", missingKeys.size(), targetLang));
            return true;
        } catch (Exception e) {
            kkfish.log(this.getMessageWithoutPrefix("log.message_complete_failed", "§eFailed to complete language file: " + e.getMessage(), e.getMessage()));
            return false;
        }
    }

    public void completeAllLanguageFiles() {
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            return;
        }

        File[] langFiles = langFolder.listFiles((dir, name) -> name.startsWith("message_") && name.endsWith(".yml"));
        if (langFiles == null || langFiles.length == 0) {
            return;
        }

        for (File langFile : langFiles) {
            String fileName = langFile.getName();
            String langCode = fileName.replace("message_", "").replace(".yml", "");
            if (!langCode.equals("en_us")) {
                completeLanguageFile(langCode);
            }
        }
    }
}
