package me.kkfish.competition;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import me.kkfish.scheduler.SchedulerTask;
import org.bukkit.boss.BossBar;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import me.kkfish.kkfish;
import me.kkfish.utils.SchedulerUtil;
import me.kkfish.utils.ActionBarUtil;
import me.kkfish.utils.ActionBarUtil.MessageType;
import me.kkfish.managers.Compete;

public abstract class Competition {

    protected final Compete manager;
    protected final CompetitionConfig config;
    protected final int duration;
    protected long startTime;
    protected long endTime;
    protected final Map<UUID, CompetitionData> playerData = new ConcurrentHashMap<>();
    protected BossBar bossBar;
    protected SchedulerTask countdownTask;
    private boolean ended = false;

    public Competition(Compete manager, CompetitionConfig config, int duration) {
        this.manager = manager;
        this.config = config;
        this.duration = duration;
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
        this.endTime = startTime + (duration * 1000L);
        
        String message = manager.getPlugin().getMessageManager().getMessage("competition_start", "&e&l比赛开始! &r&e%name% 已启动，持续 %duration%");
        message = message.replace("%name%", config.getName()).replace("%duration%", formatDuration(duration));
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        
        if (config.getDisplayConfig().isBossBarEnabled()) {
            bossBar = Bukkit.createBossBar(
                ChatColor.translateAlternateColorCodes('&', config.getDisplayConfig().getBossBarCountdownFormat().replace("%time%", formatDuration(duration))),
                config.getDisplayConfig().getBossBarColor(),
                config.getDisplayConfig().getBossBarStyle()
            );
            manager.getActiveBossBars().add(bossBar);
            
            for (Player player : Bukkit.getOnlinePlayers()) {
                bossBar.addPlayer(player);
            }
        }
        
        startCountdownTask();
    }

    public void end() {
        cleanupAndAnnounce(false);
    }
    
    public void forceEnd() {
        cleanupAndAnnounce(true);
    }
    
    private void cleanupAndAnnounce(boolean forceSettle) {
        if (ended) return;
        ended = true;
        
        if (countdownTask != null) {
            SchedulerUtil.cancelTask(countdownTask);
        }
        
        if (bossBar != null) {
            bossBar.removeAll();
            manager.getActiveBossBars().remove(bossBar);
        }
        
        String message = manager.getPlugin().getMessageManager().getMessage("competition_end", "&e&l比赛结束! &r&e%name% 已结束");
        message = message.replace("%name%", config.getName());
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
        
        announceResults(forceSettle);
    }

    public abstract void recordCatch(Player player, String fishName, double value);

    public abstract void sortData(List<CompetitionData> dataList);

    public abstract String getPlayerScoreValue(CompetitionData data);

    
    public int getRemainingSeconds() {
        long now = System.currentTimeMillis();
        long leftMs = endTime - now;
        return Math.max(0, (int)(leftMs / 1000));
    }

    private void startCountdownTask() {
        if (countdownTask != null) {
            countdownTask.cancel();
        }
        
        final CompetitionConfig config = this.config;
        final Competition competition = this;
        final BossBar bossBar = this.bossBar;
        
        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                int remainingSeconds = competition.getRemainingSeconds();
                if (remainingSeconds <= 0) {
                    manager.stopCompetitionManually(config.getId());
                    SchedulerUtil.cancelTask(countdownTask);
                    return;
                }
            
                if (bossBar != null) {
                    updateBossBar(bossBar, remainingSeconds, config.getDuration(), config);
                }

                if (config.getDisplayConfig().isTitleEnabled() && (remainingSeconds <= 10 || remainingSeconds % 60 == 0)) {
                    String title = ChatColor.translateAlternateColorCodes('&', config.getDisplayConfig().getTitleCountdownFormat().replace("%time%", formatDuration(remainingSeconds)));
                    String subtitle = "";
                    int fadeIn = 10;
                    int stay = 40;
                    int fadeOut = 10;
                    
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                    }
                }

                if (config.getDisplayConfig().isActionBarEnabled() && (remainingSeconds <= 10 || remainingSeconds % 10 == 0)) {
                    String message = ChatColor.translateAlternateColorCodes('&', config.getDisplayConfig().getActionBarCountdownFormat().replace("%time%", formatDuration(remainingSeconds)));
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        ActionBarUtil.sendActionBarPersistent(manager.getPlugin(), player, message, 40L, MessageType.COMPETITION);
                    }
                }

                if (config.getDisplayConfig().isScoreboardEnabled()) {
                    if (remainingSeconds % 2 == 0 || remainingSeconds <= 10) {
                        manager.updateScoreboard(config.getId());
                    }
                }
            }
        };
        
        countdownTask = SchedulerUtil.scheduleTask(manager.getPlugin(), runnable, 0L, 20L);
        if (countdownTask != null) {
            manager.getScheduledTasks().add(countdownTask);
        }
    }

    private void updateBossBar(BossBar bossBar, int remainingSeconds, int totalSeconds, CompetitionConfig config) {
        double progress = (double) remainingSeconds / totalSeconds;
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;
        
        bossBar.setProgress(progress);
        
        String title = ChatColor.translateAlternateColorCodes('&', config.getDisplayConfig().getBossBarCountdownFormat().replace("%time%", formatDuration(remainingSeconds)));
        bossBar.setTitle(title);
    }

    protected void announceResults() {
        announceResults(false);
    }

    protected void announceResults(boolean forceSettle) {
        if (playerData.isEmpty()) {
            String message = manager.getPlugin().getMessageManager().getMessage("competition_no_results", "&c本次比赛没有参与者");
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }
        
        if (!forceSettle && config.getMinPlayers() > 0 && playerData.size() < config.getMinPlayers()) {
            String message = manager.getPlugin().getMessageManager().getMessage("competition_insufficient_players", "&c本次比赛参与者不足 %min_players% 人，不计算排名");
            message = message.replace("%min_players%", String.valueOf(config.getMinPlayers()));
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
            return;
        }
        
        String title = manager.getPlugin().getMessageManager().getMessage("competition_results_title", "&e&l比赛结果:");
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', title));
        
        List<CompetitionData> sortedData = new ArrayList<>(playerData.values());
        sortData(sortedData);
        
        int rank = 1;
        for (CompetitionData data : sortedData) {
            Player player = Bukkit.getPlayer(data.getPlayerUUID());
            
            String messageFormat = getResultMessageFormat();
            String value = getPlayerScoreValue(data);
            
            String message = messageFormat
                .replace("%rank%", String.valueOf(rank))
                .replace("%player%", data.getPlayerName())
                .replace("%value%", value);
            
            Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
            
            if (config.getRewards().containsKey(rank) && player != null && player.isOnline()) {
                for (String command : config.getRewards().get(rank)) {
                    String processedCommand = command.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                }
            }
            
            rank++;
        }
    }

    protected abstract String getResultMessageFormat();

    protected String formatDuration(int seconds) {
        return CompetitionUtils.formatDuration(seconds, manager.getPlugin().getMessageManager());
    }

    public CompetitionConfig getConfig() {
        return config;
    }

    public Map<UUID, CompetitionData> getPlayerData() {
        return playerData;
    }
}
