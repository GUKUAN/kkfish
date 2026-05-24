package me.kkfish.competition;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BarColor;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class CompetitionConfig {
    private final String id;
    private final String name;
    private final boolean enabled;
    private final String type;
    private final String schedule;
    private final int minPlayers;
    private final int duration;
    private final Map<Integer, List<String>> rewards = new HashMap<>();
    private final Map<Integer, List<String>> rewardDisplayInfo = new HashMap<>();
    private final DisplayConfig displayConfig;
    private final Map<String, Double> fishList = new HashMap<>();
    private final double pointMultiplier;
    
    public CompetitionConfig(String id, FileConfiguration config) {
        this.id = id;
        String path = "competitions." + id;
        
        this.name = config.getString(path + ".name", id);
        this.enabled = config.getBoolean(path + ".enabled", true);
        this.type = config.getString(path + ".type", "AMOUNT");
        this.schedule = config.getString(path + ".schedule", "");
        this.minPlayers = config.getInt(path + ".min-players", 0);
        this.duration = config.getInt(path + ".duration", 3600);
        
        List<String> fishListConfig = config.getStringList(path + ".fish-list");
        for (String fishEntry : fishListConfig) {
            String[] parts = fishEntry.split(":");
            if (parts.length == 2) {
                try {
                    String fishName = parts[0].trim();
                    double weight = Double.parseDouble(parts[1].trim());
                    fishList.put(fishName, weight);
                } catch (NumberFormatException ignored) {}
            }
        }
        
        this.pointMultiplier = config.getDouble(path + ".point-multiplier", 1.0);
        
        if (config.contains(path + ".rewards") && config.getConfigurationSection(path + ".rewards") != null) {
            for (String rankStr : config.getConfigurationSection(path + ".rewards").getKeys(false)) {
                try {
                    int rank = Integer.parseInt(rankStr);
                    List<String> rewardCommands = config.getStringList(path + ".rewards." + rankStr);
                    if (!rewardCommands.isEmpty()) {
                        rewards.put(rank, rewardCommands);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        
        if (config.contains(path + ".reward-display-info") && config.getConfigurationSection(path + ".reward-display-info") != null) {
            for (String rankStr : config.getConfigurationSection(path + ".reward-display-info").getKeys(false)) {
                try {
                    int rank = Integer.parseInt(rankStr);
                    List<String> displayInfo = config.getStringList(path + ".reward-display-info." + rankStr);
                    if (!displayInfo.isEmpty()) {
                        rewardDisplayInfo.put(rank, displayInfo);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        
        this.displayConfig = new DisplayConfig(path, config);
    }
    
    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public String getType() { return type; }
    public String getSchedule() { return schedule; }
    public int getMinPlayers() { return minPlayers; }
    public int getDuration() { return duration; }
    public Map<Integer, List<String>> getRewards() { return new HashMap<>(rewards); }
    public Map<Integer, List<String>> getRewardDisplayInfo() { return new HashMap<>(rewardDisplayInfo); }
    public DisplayConfig getDisplayConfig() { return displayConfig; }
    public Map<String, Double> getFishList() { return new HashMap<>(fishList); }
    public double getPointMultiplier() { return pointMultiplier; }
    public boolean hasFishList() { return !fishList.isEmpty(); }

    public class DisplayConfig {
        private final boolean titleEnabled;
        private final String titleCountdownFormat;
        private final String titleStatusFormat;
        private final int titleStatusDuration;
        
        private final boolean actionBarEnabled;
        private final String actionBarCountdownFormat;
        private final String actionBarStatusFormat;
        private final int actionBarStatusDuration;
        
        private final boolean bossBarEnabled;
        private final String bossBarCountdownFormat;
        private final String bossBarStatusFormat;
        private final int bossBarStatusDuration;
        private final BarColor bossBarColor;
        private final BarStyle bossBarStyle;
        
        private final boolean scoreboardEnabled;
        private final String scoreboardTitle;
        private final Map<String, Integer> scoreboardLines;
        
        public DisplayConfig(String path, FileConfiguration config) {
            this.titleEnabled = config.getBoolean(path + ".display.title.enabled", true);
            this.titleCountdownFormat = config.getString(path + ".display.title.countdown-format", "&e&l比赛倒计时: &r&6%time%");
            this.titleStatusFormat = config.getString(path + ".display.title.status-format", "&e&l比赛进行中: &r&6%status%");
            this.titleStatusDuration = config.getInt(path + ".display.title.status-duration", 10);
            
            this.actionBarEnabled = config.getBoolean(path + ".display.actionbar.enabled", true);
            this.actionBarCountdownFormat = config.getString(path + ".display.actionbar.countdown-format", "&e比赛倒计时: &6%time%");
            this.actionBarStatusFormat = config.getString(path + ".display.actionbar.status-format", "&e比赛进行中: &6%status%");
            this.actionBarStatusDuration = config.getInt(path + ".display.actionbar.status-duration", 10);
            
            this.bossBarEnabled = config.getBoolean(path + ".display.bossbar.enabled", true);
            this.bossBarCountdownFormat = config.getString(path + ".display.bossbar.countdown-format", "&e&l钓鱼比赛 - 倒计时: &6%time%");
            this.bossBarStatusFormat = config.getString(path + ".display.bossbar.status-format", "&e&l钓鱼比赛 - %status%");
            this.bossBarStatusDuration = config.getInt(path + ".display.bossbar.status-duration", 10);
            
            BarColor tempBossBarColor = BarColor.BLUE;
            String colorStr = config.getString(path + ".display.bossbar.color", "BLUE");
            try {
                tempBossBarColor = BarColor.valueOf(colorStr);
            } catch (IllegalArgumentException e) {
                Bukkit.getConsoleSender().sendMessage("§c[KKFish] Invalid boss bar color: " + colorStr + ", using default BLUE");
            }
            this.bossBarColor = tempBossBarColor;
            
            BarStyle tempBossBarStyle = BarStyle.SEGMENTED_10;
            String styleStr = config.getString(path + ".display.bossbar.style", "SEGMENTED_10");
            try {
                tempBossBarStyle = BarStyle.valueOf(styleStr);
            } catch (IllegalArgumentException e) {
                Bukkit.getConsoleSender().sendMessage("§c[KKFish] Invalid boss bar style: " + styleStr + ", using default SEGMENTED_10");
            }
            this.bossBarStyle = tempBossBarStyle;
            
            this.scoreboardEnabled = config.getBoolean(path + ".display.scoreboard.enabled", true);
            this.scoreboardTitle = config.getString(path + ".display.scoreboard.title", "&e&l钓鱼比赛");
            
            this.scoreboardLines = new HashMap<>();
            if (config.contains(path + ".display.scoreboard.lines") && config.getConfigurationSection(path + ".display.scoreboard.lines") != null) {
                for (String line : config.getConfigurationSection(path + ".display.scoreboard.lines").getKeys(false)) {
                    int interval = config.getInt(path + ".display.scoreboard.lines." + line, 5);
                    scoreboardLines.put(line, interval);
                }
            }
        }
        
        public boolean isTitleEnabled() { return titleEnabled; }
        public String getTitleCountdownFormat() { return titleCountdownFormat; }
        public String getTitleStatusFormat() { return titleStatusFormat; }
        public int getTitleStatusDuration() { return titleStatusDuration; }
        
        public boolean isActionBarEnabled() { return actionBarEnabled; }
        public String getActionBarCountdownFormat() { return actionBarCountdownFormat; }
        public String getActionBarStatusFormat() { return actionBarStatusFormat; }
        public int getActionBarStatusDuration() { return actionBarStatusDuration; }
        
        public boolean isBossBarEnabled() { return bossBarEnabled; }
        public String getBossBarCountdownFormat() { return bossBarCountdownFormat; }
        public String getBossBarStatusFormat() { return bossBarStatusFormat; }
        public int getBossBarStatusDuration() { return bossBarStatusDuration; }
        public BarColor getBossBarColor() { return bossBarColor; }
        public BarStyle getBossBarStyle() { return bossBarStyle; }
        
        public boolean isScoreboardEnabled() { return scoreboardEnabled; }
        public String getScoreboardTitle() { return scoreboardTitle; }
        public Map<String, Integer> getScoreboardLines() { return new HashMap<>(scoreboardLines); }
    }
}
