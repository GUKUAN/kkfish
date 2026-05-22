package me.kkfish.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

import java.util.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.concurrent.ConcurrentHashMap;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import me.kkfish.kkfish;
import me.kkfish.utils.SchedulerUtil;
import me.kkfish.utils.ActionBarUtil;
import me.kkfish.utils.ActionBarUtil.MessageType;
import me.kkfish.competition.*;
import me.kkfish.competition.types.*;

public class Compete {

    private final kkfish plugin;
    private final Map<String, CompetitionConfig> competitionConfigs = new ConcurrentHashMap<>();
    private final Map<String, Competition> activeCompetitions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledCompetition> scheduledCompetitions = new ConcurrentHashMap<>();
    protected final Set<BukkitTask> scheduledTasks = ConcurrentHashMap.newKeySet();
    protected final Set<BossBar> activeBossBars = ConcurrentHashMap.newKeySet();
    private final Map<String, Scoreboard> activeScoreboards = new ConcurrentHashMap<>();
    
    private final StringBuilder sb = new StringBuilder();
    private final ArrayList<Map.Entry<UUID, CompetitionData>> sortedEntries = new ArrayList<>();
    private final ArrayList<CompetitionData> sortedData = new ArrayList<>();
    private final ArrayList<String> sortedKeys = new ArrayList<>();

    public Compete(kkfish plugin) {
        this.plugin = plugin;
        loadConfigs();
        setupScheduledCompetitions();
    }

    public void loadConfigs() {
        competitionConfigs.clear();

        FileConfiguration config = plugin.getCustomConfig().getCompeteConfig();
        if (config.contains("competitions")) {
            for (String id : config.getConfigurationSection("competitions").getKeys(false)) {
                CompetitionConfig cfg = new CompetitionConfig(id, config);
                if (cfg.isEnabled()) {
                    competitionConfigs.put(id, cfg);
                    plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("log.competition_load", "Loading competition configuration: %s", id));
                }
            }
        }
    }

    public void setupScheduledCompetitions() {
        cancelAllTasks();
        scheduledCompetitions.clear();
        
        for (CompetitionConfig config : competitionConfigs.values()) {
            if (config.getSchedule() != null && !config.getSchedule().isEmpty()) {
                ScheduledCompetition scheduledComp = new ScheduledCompetition(config);
                scheduledCompetitions.put(config.getId(), scheduledComp);
                scheduledComp.schedule();
            }
        }
    }
    
    public boolean startCompetitionManually(String competitionId, int durationSeconds) {
        if (!competitionConfigs.containsKey(competitionId)) {
            return false;
        }
        
        CompetitionConfig config = competitionConfigs.get(competitionId);
        if (!config.isEnabled()) {
            return false;
        }
        
        int actualDuration = durationSeconds > 0 ? durationSeconds : config.getDuration();
        
        if (activeCompetitions.containsKey(competitionId)) {
            return false;
        }
        
        Competition competition = createCompetition(config, actualDuration);
        activeCompetitions.put(competitionId, competition);
        competition.start();
        
        createScoreboard(competitionId);
        
        return true;
    }
    
    private Competition createCompetition(CompetitionConfig config, int duration) {
        switch (config.getType()) {
            case "AMOUNT":
                return new AmountCompetition(this, config, duration);
            case "TOTAL_VALUE":
                return new TotalValueCompetition(this, config, duration);
            case "SINGLE_VALUE":
                return new SingleValueCompetition(this, config, duration);
            case "POINTS_ONLY":
                return new PointsOnlyCompetition(this, config, duration);
            default:
                return new AmountCompetition(this, config, duration);
        }
    }

    public CompetitionConfig getCompetitionConfig(String competitionId) {
        return competitionConfigs.get(competitionId);
    }

    public boolean stopCompetitionManually(String competitionId) {
        if (!activeCompetitions.containsKey(competitionId)) {
            return false;
        }
        
        Competition competition = activeCompetitions.get(competitionId);
        competition.end();
        activeCompetitions.remove(competitionId);
        
        removeScoreboard(competitionId);
        
        return true;
    }
    
    public boolean forceStopCompetitionManually(String competitionId) {
        if (!activeCompetitions.containsKey(competitionId)) {
            return false;
        }
        
        Competition competition = activeCompetitions.get(competitionId);
        competition.forceEnd();
        activeCompetitions.remove(competitionId);
        
        removeScoreboard(competitionId);
        
        return true;
    }
    
    public void recordPlayerCatch(Player player, String fishName, double value) {
        for (Map.Entry<String, Competition> entry : activeCompetitions.entrySet()) {
            Competition competition = entry.getValue();
            competition.recordCatch(player, fishName, value);
        }
    }
    
    public Set<String> getCompetitionConfigIds() {
        return new HashSet<>(competitionConfigs.keySet());
    }
    
    public Collection<CompetitionConfig> getCompetitionConfigs() {
        return new ArrayList<>(competitionConfigs.values());
    }
    
    public Set<String> getActiveCompetitionIds() {
        return new HashSet<>(activeCompetitions.keySet());
    }
    
    public Set<String> getScheduledCompetitionIds() {
        return new HashSet<>(scheduledCompetitions.keySet());
    }
    
    private void cancelAllTasks() {
        for (BukkitTask task : scheduledTasks) {
            if (!task.isCancelled()) {
                SchedulerUtil.cancelTask(task);
            }
        }
        scheduledTasks.clear();
        
        for (BossBar bar : activeBossBars) {
            bar.removeAll();
        }
        activeBossBars.clear();
    }
    
    public void cleanup() {
        cancelAllTasks();
        
        for (Competition competition : activeCompetitions.values()) {
            competition.end();
        }
        activeCompetitions.clear();
    }

    public void createScoreboard(String competitionId) {
        if (!activeCompetitions.containsKey(competitionId)) {
            return;
        }
        
        Competition competition = activeCompetitions.get(competitionId);
        CompetitionConfig config = competition.getConfig();
        
        if (!config.getDisplayConfig().isScoreboardEnabled()) {
            return;
        }
        
        try {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            Scoreboard scoreboard = manager.getNewScoreboard();
            Objective objective;
            
            String rawTitle = config.getDisplayConfig().getScoreboardTitle();
            String translatedTitle = ChatColor.translateAlternateColorCodes('&', rawTitle);
            if (translatedTitle.length() > 32) {
                translatedTitle = translatedTitle.substring(0, 32);
                int lastColorCodeIndex = translatedTitle.lastIndexOf('§');
                if (lastColorCodeIndex >= 30) {
                    translatedTitle = translatedTitle.substring(0, lastColorCodeIndex);
                }
            }
            
            try {
                objective = scoreboard.registerNewObjective("competition", "dummy", translatedTitle);
            } catch (NoSuchMethodError e) {
                objective = scoreboard.registerNewObjective("competition", "dummy");
                objective.setDisplayName(translatedTitle);
            }
            
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            
            Score blank1 = objective.getScore(" ");
            blank1.setScore(10);
            
            Score status = objective.getScore(ChatColor.YELLOW + plugin.getMessageManager().getMessageWithoutPrefix("competition_status_running", "Competition in progress"));
            status.setScore(9);
            
            Score time = objective.getScore(ChatColor.GREEN + plugin.getMessageManager().getMessageWithoutPrefix("competition_time_calculating", "Time remaining: Calculating"));
            time.setScore(8);
            
            Score blank2 = objective.getScore("  ");
            blank2.setScore(7);
            
            Score title = objective.getScore(ChatColor.AQUA + plugin.getMessageManager().getMessageWithoutPrefix("competition_leaderboard", "Leaderboard"));
            title.setScore(6);
            
            for (int i = 0; i < 5; i++) {
                Score placeholder = objective.getScore(ChatColor.GRAY + "- " + plugin.getMessageManager().getMessageWithoutPrefix("competition_waiting_data", "Waiting for data..."));
                placeholder.setScore(5 - i);
            }
            
            Score blank3 = objective.getScore("   ");
            blank3.setScore(0);
            
            activeScoreboards.put(competitionId, scoreboard);
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.setScoreboard(scoreboard);
            }
            
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("competition_scoreboard_create_error", "Failed to create scoreboard: %s", e.getMessage()));
        }
    }
    
    public void updateScoreboard(String competitionId) {
        if (!activeScoreboards.containsKey(competitionId)) {
            return;
        }

        Competition competition = activeCompetitions.get(competitionId);
        if (competition == null) {
            return;
        }

        Scoreboard scoreboard = activeScoreboards.get(competitionId);
        try {
            Objective objective = scoreboard.getObjective(DisplaySlot.SIDEBAR);
            if (objective == null) {
                objective = scoreboard.registerNewObjective("competition", "dummy", ChatColor.translateAlternateColorCodes('&', competition.getConfig().getDisplayConfig().getScoreboardTitle()));
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            }

            clearScoreboardEntries(scoreboard);
            int scoreValue = buildScoreboardContent(objective, competition);
            addScoreboardPlaceholders(objective, scoreValue);
            
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("competition_scoreboard_update_error", "Failed to update scoreboard: %s", e.getMessage()));
        }
    }
    
    private void clearScoreboardEntries(Scoreboard scoreboard) {
        for (String entry : new ArrayList<>(scoreboard.getEntries())) {
            scoreboard.resetScores(entry);
        }
    }
    
    private int buildScoreboardContent(Objective objective, Competition competition) {
        int scoreValue = 15;
        
        Map<String, Integer> lines = competition.getConfig().getDisplayConfig().getScoreboardLines();
        
        sortedKeys.clear();
        sortedKeys.addAll(lines.keySet());
        
        sortedKeys.sort((a, b) -> {
            int scoreA = lines.get(a);
            int scoreB = lines.get(b);
            
            if (scoreA != scoreB) {
                return scoreB - scoreA;
            }
            
            String lineA = a.toLowerCase();
            String lineB = b.toLowerCase();
            
            if (lineA.contains("1st")) return -1;
            if (lineB.contains("1st")) return 1;
            if (lineA.contains("2nd")) return -1;
            if (lineB.contains("2nd")) return 1;
            if (lineA.contains("3rd")) return -1;
            if (lineB.contains("3rd")) return 1;
            
            return 0;
        });

        sortedData.clear();
        sortedData.addAll(competition.getPlayerData().values());
        competition.sortData(sortedData);

        for (String key : sortedKeys) {
            String line = ChatColor.translateAlternateColorCodes('&', key);
            line = replacePlaceholders(line, competition, sortedData);
            
            Score score = objective.getScore(line);
            score.setScore(scoreValue--);
        }
        
        return scoreValue;
    }
    
    private String replacePlaceholders(String line, Competition competition, List<CompetitionData> sortedData) {
        line = line.replace("%time%", formatDuration(competition.getRemainingSeconds()));
        line = line.replace("%players%", String.valueOf(competition.getPlayerData().size()));
        line = line.replace("%competition%", competition.getConfig().getName());
        
        for (int i = 0; i < sortedData.size() && i < 10; i++) {
            CompetitionData data = sortedData.get(i);
            int rank = i + 1;
            String playerName = data.getPlayerName();
            String value = competition.getPlayerScoreValue(data);
            
            line = line.replace("%player_" + rank + "%", playerName);
            line = line.replace("%value_" + rank + "%", value);
        }
        
        for (int i = 1; i <= 10; i++) {
            if (!line.contains("%player_" + i + "%")) continue;
            line = line.replace("%player_" + i + "%", "--");
            line = line.replace("%value_" + i + "%", "--");
        }
        
        return line;
    }
    
    private String getRankPrefix(int rank) {
        switch (rank) {
            case 1:
                return ChatColor.GOLD + "[#1] " + ChatColor.YELLOW;
            case 2:
                return ChatColor.GRAY + "[#2] " + ChatColor.WHITE;
            case 3:
                return ChatColor.GOLD + "[#3] " + ChatColor.RED;
            default:
                return ChatColor.DARK_GRAY + "[#" + rank + "] " + ChatColor.GRAY;
        }
    }
    
    private void addScoreboardPlaceholders(Objective objective, int scoreValue) {
        while (scoreValue >= 0) {
            String placeholderText = plugin.getMessageManager().getMessageWithoutPrefix("competition_rank_placeholder", "--");
            Score placeholder = objective.getScore(ChatColor.GRAY + placeholderText);
            placeholder.setScore(scoreValue--);
        }
    }
    
    public void removeScoreboard(String competitionId) {
        if (!activeScoreboards.containsKey(competitionId)) {
            return;
        }
        
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard defaultScoreboard = manager.getNewScoreboard();
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(defaultScoreboard);
        }
        
        activeScoreboards.remove(competitionId);
    }
    
    private String formatDuration(int seconds) {
        int days = seconds / 86400;
        int hours = (seconds % 86400) / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        
        sb.setLength(0);
        if (days > 0) {
            sb.append(days).append(plugin.getMessageManager().getMessageWithoutPrefix("time.day", "天"));
        }
        if (hours > 0) {
            sb.append(hours).append(plugin.getMessageManager().getMessageWithoutPrefix("time.hour", "时"));
        }
        if (minutes > 0) {
            sb.append(minutes).append(plugin.getMessageManager().getMessageWithoutPrefix("time.minute", "分"));
        }
        if (secs > 0 || sb.length() == 0) {
            sb.append(secs).append(plugin.getMessageManager().getMessageWithoutPrefix("time.second", "秒"));
        }

        return sb.toString();
    }

    public class ScheduledCompetition {
        private final CompetitionConfig config;
        private BukkitTask task;
        
        public ScheduledCompetition(CompetitionConfig config) {
            this.config = config;
        }
        
        public void schedule() {
            if (config.getSchedule().contains("every")) {
                scheduleRecurring();
            } else if (config.getSchedule().contains("-")) {
                scheduleSpecificDate();
            }
        }
        
        private ZoneId getTimeZone() {
            String timezone = plugin.getCustomConfig().getTimezone();
            if (timezone == null || timezone.isEmpty()) {
                return ZoneId.systemDefault();
            }
            try {
                return ZoneId.of(timezone);
            } catch (Exception e) {
                plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("timezone_invalid", "Invalid timezone '%s', using system default", timezone));
                return ZoneId.systemDefault();
            }
        }
        
        private void scheduleRecurring() {
            try {
                String[] parts = config.getSchedule().split(" ");
                String dayPart = parts[1];
                String[] timeParts = parts[2].split(":");
                int hour = Integer.parseInt(timeParts[0]);
                int minute = Integer.parseInt(timeParts[1]);
                
                ZoneId zoneId = getTimeZone();
                ZonedDateTime now = ZonedDateTime.now(zoneId);
                ZonedDateTime nextRun;
                
                if (dayPart.equalsIgnoreCase("day")) {
                    nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
                    if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
                        nextRun = nextRun.plusDays(1);
                    }
                    scheduleDaily(nextRun);
                } else {
                    int dayOfWeek = Integer.parseInt(dayPart) - 1;
                    scheduleWeekly(dayOfWeek, hour, minute, zoneId);
                }
                
            } catch (Exception e) {
                plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("competition_schedule_parse_error", "Failed to parse recurring competition time: %s - %s", config.getSchedule(), e.getMessage()));
            }
        }
        
        private void scheduleDaily(ZonedDateTime nextRun) {
            ZoneId zoneId = getTimeZone();
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            long delay = java.time.Duration.between(now, nextRun).toMillis();
            
            final BukkitRunnable runnable = new BukkitRunnable() {
                @Override
                public void run() {
                    if (config.getMinPlayers() > 0 && Bukkit.getOnlinePlayers().size() < config.getMinPlayers()) {
                        scheduleDaily(nextRun.plusDays(1));
                        return;
                    }
                    
                    startCompetitionManually(config.getId(), config.getDuration());
                    ZonedDateTime next = ZonedDateTime.now(zoneId).plusDays(1).withHour(nextRun.getHour()).withMinute(nextRun.getMinute()).withSecond(0);
                    scheduleDaily(next);
                }
            };
            
            scheduleCompetitionTask(runnable, delay, true);
        }
        
        private void scheduleWeekly(int dayOfWeek, int hour, int minute, ZoneId zoneId) {
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            ZonedDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
            
            int currentDayOfWeek = now.getDayOfWeek().getValue() % 7;
            int daysUntilTarget = (dayOfWeek - currentDayOfWeek + 7) % 7;
            
            if (daysUntilTarget == 0 && (nextRun.isBefore(now) || nextRun.isEqual(now))) {
                daysUntilTarget = 7;
            }
            
            nextRun = nextRun.plusDays(daysUntilTarget);
            
            long delay = java.time.Duration.between(now, nextRun).toMillis();
            
            final ZonedDateTime finalNextRun = nextRun;
            final BukkitRunnable runnable = new BukkitRunnable() {
                @Override
                public void run() {
                    if (config.getMinPlayers() > 0 && Bukkit.getOnlinePlayers().size() < config.getMinPlayers()) {
                        scheduleWeekly(dayOfWeek, hour, minute, zoneId);
                        return;
                    }
                    
                    startCompetitionManually(config.getId(), config.getDuration());
                    scheduleWeekly(dayOfWeek, hour, minute, zoneId);
                }
            };
            
            scheduleCompetitionTask(runnable, delay, true);
        }
        
        private void scheduleSpecificDate() {
            try {
                ZoneId zoneId = getTimeZone();
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                dateFormat.setTimeZone(java.util.TimeZone.getTimeZone(zoneId));
                Date date = dateFormat.parse(config.getSchedule());
                
                long delay = date.getTime() - System.currentTimeMillis();
                
                if (delay <= 0) {
                    plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("log.competition_date_passed", "Specified competition date has passed: %s", config.getSchedule()));
                    return;
                }
                
                final BukkitRunnable runnable = new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (config.getMinPlayers() > 0 && Bukkit.getOnlinePlayers().size() < config.getMinPlayers()) {
                            plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("competition_cancelled_insufficient_players", "Competition %s cancelled due to insufficient players", config.getName()));
                            return;
                        }
                        
                        startCompetitionManually(config.getId(), config.getDuration());
                    }
                };
                
                scheduleCompetitionTask(runnable, delay, false);
                
            } catch (ParseException e) {
                plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("competition_date_parse_error", "Failed to parse specific date competition time: %s - %s", config.getSchedule(), e.getMessage()));
            }
        }
        
        private void scheduleCompetitionTask(BukkitRunnable runnable, long delay, boolean isRecurring) {
            task = runnable.runTaskLater(plugin, delay / 50);
            scheduledTasks.add(task);
            scheduleCompetitionNotification(delay);
        }
        
        private void scheduleCompetitionNotification(long delay) {
            int minutes = 5;
            long notificationDelay = delay - (minutes * 60 * 1000);
            
            try {
                if (notificationDelay > 0) {
                    SchedulerUtil.scheduleTask(plugin, new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!plugin.getServer().getOnlinePlayers().isEmpty()) {
                                plugin.getServer().getOnlinePlayers().forEach(player -> {
                                    String message = plugin.getMessageManager().getMessage(player, "competition.starting_soon", "Competition will start in %s minutes!", String.valueOf(minutes));
                                    player.sendMessage(message);
                                });
                            }
                        }
                    }, notificationDelay / 50, 0);
                }
            } catch (Exception e) {
                plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("competition_notification_schedule_error", "Failed to schedule competition start notification task: %s", e.getMessage()));
            }
        }
    }

    public kkfish getPlugin() {
        return plugin;
    }

    public Scoreboard getScoreboard(String competitionId) {
        return activeScoreboards.get(competitionId);
    }
    

    public Set<BossBar> getActiveBossBars() {
        return activeBossBars;
    }
    
    public Set<BukkitTask> getScheduledTasks() {
        return scheduledTasks;
    }
}
