package me.kkfish.misc;

import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import me.kkfish.kkfish;
import me.kkfish.utils.XSeriesUtil;

public class SoundManager {

    private final kkfish plugin;

    public SoundManager(kkfish plugin) {
        this.plugin = plugin;
    }

    public void playCastSound(Location location) {
        playSound(location, "cast.name", "cast.volume", "cast.pitch", "cast.enabled");
    }
    
    public void playPrepareSound(Location location) {
        playSound(location, "cast.name", "cast.volume", "cast.pitch", "cast.enabled");
    }
    
    public void playChargeTickSound(Location location) {
        playSound(location, "minigame.name", "minigame.volume", "minigame.pitch", "minigame.enabled");
    }
    
    public void playPerfectCastSound(Location location) {
        playSound(location, "success.name", "success.volume", "success.pitch", "success.enabled");
    }
    
    public void playGoodCastSound(Location location) {
        playSound(location, "success.name", "success.volume", "success.pitch", "success.enabled");
    }
    
    public void playNormalCastSound(Location location) {
        playSound(location, "cast.name", "cast.volume", "cast.pitch", "cast.enabled");
    }
    
    public void playFastCastSound(Location location) {
        playSound(location, "cast.name", "cast.volume", "cast.pitch", "cast.enabled");
    }

    public void playBiteSound(Location location) {
        playSound(location, "bite_hint.name", "bite_hint.volume", "bite_hint.pitch", "bite_hint.enabled");
    }
    
    public void playWaterSplashSound(Location location) {
        playSound(location, "cast.name", "cast.volume", "cast.pitch", "cast.enabled");
    }

    public void playSuccessSound(Location location) {
        playSound(location, "success.name", "success.volume", "success.pitch", "success.enabled");
    }
    
    public void playReelSound(Location location) {
        playSound(location, "reel.name", "reel.volume", "reel.pitch", "reel.enabled");
    }

    public void playFailSound(Location location) {
        playSound(location, "fail.name", "fail.volume", "fail.pitch", "fail.enabled");
    }

    public void playMinigameSound(Location location) {
        playSound(location, "minigame.name", "minigame.volume", "minigame.pitch", "minigame.enabled");
    }

    private void playSound(Location location, String soundPath, String volumePath, String pitchPath, String enabledPath) {
        FileConfiguration config = plugin.getCustomConfig().getSoundConfig();
        
        if (!config.getBoolean("settings.enabled", true)) {
            return;
        }
        
        if (!config.getBoolean(enabledPath, true)) {
            return;
        }
        
        String soundName = config.getString(soundPath);
        float volume = (float) config.getDouble(volumePath, 1.0);
        float pitch = (float) config.getDouble(pitchPath, 1.0);
        
        if (soundName == null || soundName.isEmpty()) {
            if (soundPath.contains("perfectcast")) {
                soundName = "ENTITY_PLAYER_LEVELUP";
            } else if (soundPath.contains("goodcast")) {
                soundName = "BLOCK_NOTE_BLOCK_PLING";
            } else if (soundPath.contains("normalcast")) {
                soundName = "ENTITY_FISHING_BOBBER_THROW";
            } else if (soundPath.contains("fastcast")) {
                soundName = "ENTITY_FISHING_BOBBER_THROW";
            } else if (soundPath.contains("cast")) {
                soundName = "ENTITY_FISHING_BOBBER_THROW";
            } else if (soundPath.contains("prepare")) {
                soundName = "BLOCK_NOTE_BLOCK_HARP";
            } else if (soundPath.contains("chargetick")) {
                soundName = "BLOCK_NOTE_BLOCK_BIT";
            } else if (soundPath.contains("bite")) {
                soundName = "ENTITY_FISHING_BOBBER_SPLASH";
            } else if (soundPath.contains("splash")) {
                soundName = "ENTITY_FISHING_BOBBER_SPLASH";
            } else if (soundPath.contains("reel")) {
                soundName = "ENTITY_FISHING_BOBBER_RETRIEVE";
            } else if (soundPath.contains("success")) {
                soundName = "ENTITY_PLAYER_LEVELUP";
            } else if (soundPath.contains("fail")) {
                soundName = "ENTITY_VILLAGER_NO";
            } else if (soundPath.contains("minigame")) {
                soundName = "BLOCK_NOTE_BLOCK_PLING";
            }
        }
        
        try {
            XSeriesUtil.playSound(location, soundName, volume, pitch);
        } catch (Exception e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("sound_play_error", "Unable to play sound: %s, sound does not exist.", soundName));
        }
    }

    public void stopAllSounds(Location location) {
    }

    public void setSoundVolume(String soundType, double volume) {
        FileConfiguration config = plugin.getCustomConfig().getSoundConfig();
        String volumePath = soundType + ".volume";
        config.set(volumePath, volume);
        plugin.getCustomConfig().saveConfigs();
    }

    public void setSoundPitch(String soundType, double pitch) {
        FileConfiguration config = plugin.getCustomConfig().getSoundConfig();
        String pitchPath = soundType + ".pitch";
        config.set(pitchPath, pitch);
        plugin.getCustomConfig().saveConfigs();
    }

    public void toggleSound(boolean enabled) {
        FileConfiguration config = plugin.getCustomConfig().getSoundConfig();
        config.set("settings.enabled", enabled);
        plugin.getCustomConfig().saveConfigs();
    }

    public boolean isSoundEnabled() {
        return plugin.getCustomConfig().getSoundConfig().getBoolean("settings.enabled", true);
    }
}