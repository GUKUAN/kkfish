package me.kkfish.misc;

import me.kkfish.kkfish;
import org.bukkit.plugin.java.JavaPlugin;
import me.kkfish.kkfish;
import java.io.IOException;
import me.kkfish.kkfish;
import java.io.InputStream;
import me.kkfish.kkfish;
import java.net.URL;
import me.kkfish.kkfish;
import java.util.Scanner;
import me.kkfish.kkfish;
import java.util.function.Consumer;
import me.kkfish.kkfish;
import org.bukkit.Bukkit;
import me.kkfish.kkfish;
import me.kkfish.utils.SchedulerUtil;

public class UpdateChecker {
    
    private final JavaPlugin plugin;
    private final int resourceId;
    private final String currentVersion;
    private final String SPIGOTMC_RESOURCE_URL = "https://www.spigotmc.org/resources/129074/";
    
    public UpdateChecker(JavaPlugin plugin) {
        this(plugin, 129074);
    }
    
    public UpdateChecker(JavaPlugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
        this.currentVersion = plugin.getDescription().getVersion();
    }
    
    public void checkForUpdates() {
        getVersion(version -> {
            if (version != null && !version.isEmpty()) {
                compareVersions(version);
            } else {
                kkfish.log("§e" + "Unable to fetch the latest version information of the plugin~");
            }
        });
    }
    
    public void getVersion(final Consumer<String> consumer) {
        me.kkfish.utils.SchedulerUtil.runAsync(this.plugin, () -> {
            try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + this.resourceId).openStream(); 
                 Scanner scanner = new Scanner(inputStream)) {
                if (scanner.hasNext()) {
                    consumer.accept(scanner.next());
                } else {
                    consumer.accept(null);
                }
            } catch (IOException exception) {
                kkfish.log(MessageManager.getInstance(plugin).getMessageWithoutPrefix("log.update_check_exception", "Unable to check for updates: " + exception.getMessage(), exception.getMessage()));
                consumer.accept(null);
            }
        });
    }
    
    private void compareVersions(String latestVersion) {
        MessageManager messageManager = MessageManager.getInstance(plugin);
        
        if (latestVersion == null || latestVersion.trim().isEmpty()) {
            String latestMsg = messageManager.getMessageWithoutPrefix("version_latest", "You are currently using the latest version %s.", currentVersion);
            kkfish.log(latestMsg);
            return;
        }
        
        final String trimmedLatestVersion = latestVersion.trim();
        
        String cleanCurrentVersion = currentVersion.replaceAll("[^0-9.]", "");
        String cleanLatestVersion = trimmedLatestVersion.replaceAll("[^0-9.]", "");
        
        boolean isNewVersionAvailable = !cleanCurrentVersion.equals(cleanLatestVersion) && isNewerVersion(trimmedLatestVersion, currentVersion);
        
        if (isNewVersionAvailable) {
            final JavaPlugin finalPlugin = plugin;
            final MessageManager finalMessageManager = messageManager;
            final String finalCurrentVersion = currentVersion;
            
            me.kkfish.utils.SchedulerUtil.runSync(finalPlugin, () -> {
                String updateMsg = finalMessageManager.getMessageWithoutPrefix("update_found", "New version found! Current version: %s, latest version: %s", finalCurrentVersion, trimmedLatestVersion);
                String updateUrlMsg = finalMessageManager.getMessageWithoutPrefix("update_url", "Please visit SpigotMC to update the plugin: %s", SPIGOTMC_RESOURCE_URL);
                
                kkfish.log(updateMsg);
                kkfish.log(updateUrlMsg);
                
                for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("kkfish.admin")) {
                        player.sendMessage(finalMessageManager.getMessage("update_found", "New version found! Current version: %s, latest version: %s", finalCurrentVersion, trimmedLatestVersion));
                        player.sendMessage(finalMessageManager.getMessage("update_url", "Please visit SpigotMC to update the plugin: %s", SPIGOTMC_RESOURCE_URL));
                    }
                }
            });
        } else {
            String latestMsg = messageManager.getMessageWithoutPrefix("version_latest", "You are currently using the latest version %s.", currentVersion);
            kkfish.log(latestMsg);
        }
    }
    
    private boolean isNewerVersion(String newVersion, String oldVersion) {
        try {
            String cleanNewVersion = newVersion.replaceAll("[^0-9.]", "");
            String cleanOldVersion = oldVersion.replaceAll("[^0-9.]", "");
            
            String[] newParts = cleanNewVersion.split("\\.");
            String[] oldParts = cleanOldVersion.split("\\.");
            
            int maxLength = Math.max(newParts.length, oldParts.length);
            for (int i = 0; i < maxLength; i++) {
                int newPart = i < newParts.length ? Integer.parseInt(newParts[i]) : 0;
                int oldPart = i < oldParts.length ? Integer.parseInt(oldParts[i]) : 0;
                
                if (newPart > oldPart) {
                    return true;
                } else if (newPart < oldPart) {
                    return false;
                }
            }
            
            return false;
        } catch (NumberFormatException e) {
            kkfish.log(MessageManager.getInstance(plugin).getMessageWithoutPrefix("log.command_version_parse_failed", "§eError parsing version number, using simple comparison: " + e.getMessage(), e.getMessage()));
            return !newVersion.equals(oldVersion);
        }
    }
}