package me.kkfish.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;

/**
 * 负责鱼上钩后的广播消息发送，包括稀有度/尺寸描述。
 * 从 Fish.java 拆分而来。
 */
public class FishBroadcastService {

    private final kkfish plugin;
    private final Config config;
    private final MessageManager messageManager;

    public FishBroadcastService(kkfish plugin, Config config, MessageManager messageManager) {
        this.plugin = plugin;
        this.config = config;
        this.messageManager = messageManager;
    }

    public void sendFishBroadcast(Player player, String fishName, double fishSize, int fishLevel, double fishValue) {
        boolean enabled = config.getMainConfig().getBoolean("broadcast.enabled", true);
        if (!enabled) return;

        String broadcastRange = config.getMainConfig().getString("broadcast.range", "global");

        String rarityDesc = getRarityDescription(fishLevel);
        String sizeDesc = getSizeDescription(fishSize);

        String broadcastMessage = plugin.getMessageManager().getMessage("fish_caught_broadcast", "§b[钓鱼] %player% 钓到了一条 %size%的%rarity%鱼 %fish%，价值 %value%!");

        broadcastMessage = broadcastMessage.replace("%player%", player.getName());
        broadcastMessage = broadcastMessage.replace("%fish%", fishName);
        broadcastMessage = broadcastMessage.replace("%size%", sizeDesc);
        broadcastMessage = broadcastMessage.replace("%rarity%", rarityDesc);
        broadcastMessage = broadcastMessage.replace("%value%", String.format("%.0f", fishValue));

        switch (broadcastRange.toLowerCase()) {
            case "global":
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!onlinePlayer.equals(player)) {
                        onlinePlayer.sendMessage(broadcastMessage);
                    }
                }
                break;
            case "world":
                for (Player onlinePlayer : player.getWorld().getPlayers()) {
                    if (!onlinePlayer.equals(player)) {
                        onlinePlayer.sendMessage(broadcastMessage);
                    }
                }
                break;
            case "none":
                break;
        }
    }

    private String getSizeDescription(double size) {
        if (size < 1.5) {
            return plugin.getMessageManager().getMessageWithoutPrefix("fish_size_small", "小鱼苗");
        } else if (size < 2.5) {
            return plugin.getMessageManager().getMessageWithoutPrefix("fish_size_medium", "中等大小");
        } else if (size < 3.5) {
            return plugin.getMessageManager().getMessageWithoutPrefix("fish_size_large", "较大");
        } else {
            return plugin.getMessageManager().getMessageWithoutPrefix("fish_size_huge", "巨大");
        }
    }

    private String getRarityDescription(int level) {
        if (level < 1) {
            return plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.common", "普通");
        } else if (level < 2) {
            return plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.uncommon", "优秀");
        } else if (level < 3) {
            return plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.rare", "稀有");
        } else if (level < 4) {
            return plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.epic", "史诗");
        } else {
            return plugin.getMessageManager().getMessageWithoutPrefix("rarity_name.legendary", "传说");
        }
    }
}
