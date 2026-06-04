package me.kkfish.utils;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import me.kkfish.scheduler.SchedulerTask;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ActionBarUtil {

    public enum MessageType {
        GENERAL,
        FISHING,
        MINIGAME,
        COMPETITION
    }
    
    private static Map<UUID, Map<MessageType, SchedulerTask>> persistentMessageTasks = new HashMap<>();

    public static void sendActionBar(Player player, String message) {
        if (player == null || message == null) return;
        
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
        } catch (Exception e) {
            player.sendMessage(message);
        }
    }
    
    public static void sendActionBarDelayed(JavaPlugin plugin, Player player, String message, long delayTicks) {
        me.kkfish.utils.SchedulerUtil.runEntityTaskDelayed(plugin, player, () -> {
            sendActionBar(player, message);
        }, delayTicks);
    }
    
    public static void sendActionBarPersistent(JavaPlugin plugin, Player player, String message, long durationTicks) {
        sendActionBarPersistent(plugin, player, message, durationTicks, MessageType.GENERAL);
    }
    
    public static void sendActionBarPersistent(JavaPlugin plugin, Player player, String message, long durationTicks, MessageType type) {
        if (player == null || message == null) return;

        UUID playerId = player.getUniqueId();

        if (!persistentMessageTasks.containsKey(playerId)) {
            persistentMessageTasks.put(playerId, new HashMap<>());
        }

        Map<MessageType, SchedulerTask> playerTasks = persistentMessageTasks.get(playerId);

        if (playerTasks.containsKey(type)) {
            SchedulerTask existing = playerTasks.get(type);
            existing.cancel();
        }

        final SchedulerTask[] taskRef = new SchedulerTask[1];
        BukkitRunnable task = new BukkitRunnable() {
            private long startTime = System.currentTimeMillis();
            private long durationMillis = durationTicks * 50;

            @Override
            public void run() {
                if (System.currentTimeMillis() - startTime > durationMillis) {
                    if (persistentMessageTasks.containsKey(playerId) &&
                        persistentMessageTasks.get(playerId).containsKey(type)) {
                        persistentMessageTasks.get(playerId).remove(type);
                        if (persistentMessageTasks.get(playerId).isEmpty()) {
                            persistentMessageTasks.remove(playerId);
                        }
                    }
                    if (taskRef[0] != null) {
                        taskRef[0].cancel();
                    }
                    return;
                }

                sendActionBar(player, message);
            }
        };

        sendActionBar(player, message);

        taskRef[0] = me.kkfish.utils.SchedulerUtil.runEntityTaskTimer(plugin, player, task, 1, 1);
        playerTasks.put(type, taskRef[0]);
    }
    
    public static void cancelPersistentMessage(JavaPlugin plugin, Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        if (persistentMessageTasks.containsKey(playerId)) {
            Map<MessageType, SchedulerTask> playerTasks = persistentMessageTasks.get(playerId);
            for (SchedulerTask task : playerTasks.values()) {
                task.cancel();
            }
            persistentMessageTasks.remove(playerId);
        }
    }

    public static void cancelPersistentMessage(JavaPlugin plugin, Player player, MessageType type) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        if (persistentMessageTasks.containsKey(playerId)) {
            Map<MessageType, SchedulerTask> playerTasks = persistentMessageTasks.get(playerId);
            if (playerTasks.containsKey(type)) {
                SchedulerTask task = playerTasks.get(type);
                task.cancel();
                playerTasks.remove(type);
                if (playerTasks.isEmpty()) {
                    persistentMessageTasks.remove(playerId);
                }
            }
        }
    }
}