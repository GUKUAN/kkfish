package me.kkfish.managers;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;
import me.kkfish.misc.UpdateChecker;

/**
 * 管理员命令处理器：负责 unlock/lock/version/toggle 命令。
 * 从 Cmd 抽取，职责单一。
 */
public class AdminCommandHandler {

    private final kkfish plugin;
    private final MessageManager messageManager;

    public AdminCommandHandler(kkfish plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    /**
     * 为目标玩家解锁鱼类图鉴。
     */
    public void unlockFishForPlayer(CommandSender sender, Player targetPlayer, String fishName) {
        Config configManager = plugin.getCustomConfig();
        DB dbManager = plugin.getDB();

        if ("all".equalsIgnoreCase(fishName)) {
            if (!configManager.getFishConfig().contains("fish")) {
                sender.sendMessage(plugin.getMessageManager().getMessage("no_fish_data", "§c配置文件中没有鱼类数据！"));
                return;
            }

            int unlockedCount = 0;
            for (String fish : configManager.getFishConfig().getConfigurationSection("fish").getKeys(false)) {
                double unlockSize = configManager.getFishConfig().getDouble("fish." + fish + ".min-size", 30.0) +
                                  (configManager.getFishConfig().getDouble("fish." + fish + ".max-size", 60.0) -
                                   configManager.getFishConfig().getDouble("fish." + fish + ".min-size", 30.0)) * 0.5;

                dbManager.unlockFishForPlayer(targetPlayer.getUniqueId().toString(), fish, unlockSize);
                unlockedCount++;
            }

            sender.sendMessage(plugin.getMessageManager().getMessage("unlock_all_success_op", "§a已成功为玩家%s解锁了%s种鱼类图鉴！", targetPlayer.getName(), unlockedCount));
            targetPlayer.sendMessage(plugin.getMessageManager().getMessage("unlock_all_success_player", "§a管理员已为你解锁了所有鱼类图鉴！"));
        } else {
            if (!configManager.getFishConfig().contains("fish." + fishName)) {
                sender.sendMessage(plugin.getMessageManager().getMessage("fish_not_found", "§c未找到鱼类: %s", fishName));
                return;
            }

            double unlockSize = configManager.getFishConfig().getDouble("fish." + fishName + ".min-size", 30.0) +
                              (configManager.getFishConfig().getDouble("fish." + fishName + ".max-size", 60.0) -
                               configManager.getFishConfig().getDouble("fish." + fishName + ".min-size", 30.0)) * 0.5;

            dbManager.unlockFishForPlayer(targetPlayer.getUniqueId().toString(), fishName, unlockSize);

            sender.sendMessage(plugin.getMessageManager().getMessage("unlock_success_op", "§a已成功为玩家%s解锁了鱼类图鉴: %s", targetPlayer.getName(), fishName));
            targetPlayer.sendMessage(plugin.getMessageManager().getMessage("unlock_success_player", "§a管理员已为你解锁了鱼类图鉴: %s", fishName));
        }
    }

    /**
     * 为目标玩家锁定鱼类图鉴。
     */
    public void lockFishForPlayer(CommandSender sender, Player targetPlayer, String fishName) {
        Config configManager = plugin.getCustomConfig();
        DB dbManager = plugin.getDB();

        if ("all".equalsIgnoreCase(fishName)) {
            dbManager.lockFishForPlayer(targetPlayer.getUniqueId().toString(), fishName);
            sender.sendMessage(plugin.getMessageManager().getMessage("lock_all_success_op", "§a已成功为玩家%s锁定了所有鱼类图鉴！", targetPlayer.getName()));
            targetPlayer.sendMessage(plugin.getMessageManager().getMessage("lock_all_success_player", "§a管理员已为你锁定了所有鱼类图鉴！"));
        } else {
            if (!configManager.getFishConfig().contains("fish." + fishName)) {
                sender.sendMessage(plugin.getMessageManager().getMessage("fish_not_found", "§c未找到鱼类: %s", fishName));
                return;
            }

            dbManager.lockFishForPlayer(targetPlayer.getUniqueId().toString(), fishName);

            sender.sendMessage(plugin.getMessageManager().getMessage("lock_success_op", "§a已成功为玩家%s锁定了鱼类图鉴: %s", targetPlayer.getName(), fishName));
            targetPlayer.sendMessage(plugin.getMessageManager().getMessage("lock_success_player", "§a管理员已为你锁定了鱼类图鉴: %s", fishName));
        }
    }

    /**
     * 检查插件版本更新。
     */
    public void checkVersion(CommandSender sender) {
        final String currentVersion = plugin.getDescription().getVersion();
        sender.sendMessage(messageManager.getMessage("checking_update", "§e正在检查更新..."));

        final UpdateChecker updateChecker = new UpdateChecker(plugin);
        final CommandSender finalSender = sender;
        final kkfish finalPlugin = plugin;
        updateChecker.getVersion(latestVersion -> {
            me.kkfish.utils.SchedulerUtil.runSync(finalPlugin, () -> {
                if (latestVersion != null && !latestVersion.isEmpty()) {
                    final String trimmedLatestVersion = latestVersion.trim();
                    finalSender.sendMessage(messageManager.getMessage("current_version", "§aCurrent version: %s", currentVersion));
                    finalSender.sendMessage(messageManager.getMessage("latest_version", "§aLatest version: %s", trimmedLatestVersion));

                    boolean isNewVersionAvailable = !currentVersion.equals(trimmedLatestVersion) && isNewerVersion(trimmedLatestVersion, currentVersion);

                    if (isNewVersionAvailable) {
                        finalSender.sendMessage(messageManager.getMessage("update_found", "§6New version found %s!", trimmedLatestVersion));
                        finalSender.sendMessage(messageManager.getMessage("update_url", "§6Download URL: %s", "https://www.spigotmc.org/resources/kkfish-1-16-1-21-a-perfect-fishing-plugin.129074/"));
                    } else {
                        finalSender.sendMessage(messageManager.getMessage("version_latest", "You are currently using the latest version %s.", currentVersion));
                    }
                } else {
                    finalSender.sendMessage(messageManager.getMessage("update_parse_failed", "§cFailed to parse SpigotMC version information~"));
                }
            });
        });
    }

    /**
     * 比较版本号，判断 newVersion 是否大于 oldVersion。
     */
    public boolean isNewerVersion(String newVersion, String oldVersion) {
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
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.command_version_parse_failed", "Error parsing version number, using simple comparison method: ") + e.getMessage());
            return !newVersion.equals(oldVersion);
        }
    }

    /**
     * 处理 toggle 命令：切换钓鱼模式（原版/插件）。
     */
    public void handleModeCommand(CommandSender sender, String[] args) {
        if (!plugin.getCustomConfig().isCommandSwitchEnabled()) {
            sender.sendMessage(messageManager.getMessage("mode_switch_command_disabled", "&c指令切换钓鱼模式功能已被禁用"));
            return;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.getMessage("command_in_game_only", "&d此命令只能在游戏内使用！"));
            return;
        }

        Player player = (Player) sender;
        String worldName = player.getWorld().getName();

        if (!plugin.getCustomConfig().isWorldAllowed(worldName)) {
            sender.sendMessage(messageManager.getMessage("mode_switch_world_not_allowed", "&c当前世界不允许切换钓鱼模式"));
            return;
        }

        boolean currentIsVanilla = plugin.isPlayerInVanillaMode(player.getUniqueId());
        boolean newIsVanilla = !currentIsVanilla;

        plugin.setPlayerFishingMode(player.getUniqueId(), newIsVanilla);

        String modeKey = newIsVanilla ? "mode_switch_mode_vanilla" : "mode_switch_mode_plugin";
        String modeName = messageManager.getMessageWithoutPrefix(modeKey, newIsVanilla ? "原版钓鱼模式" : "插件钓鱼模式");
        sender.sendMessage(messageManager.getMessage("mode_switch_success", "&a钓鱼模式切换成功！当前模式: %s", modeName));
    }

    /**
     * 判断玩家是否手持鱼竿（含自定义鱼竿检测）。
     */
    public boolean hasFishingRod(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        if (item.getType() == Material.FISHING_ROD) {
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean hasCustomModelData = false;
            boolean isCustomFishingRod = false;

            try {
                java.lang.reflect.Method hasCustomModelDataMethod = meta.getClass().getMethod("hasCustomModelData");
                hasCustomModelData = (boolean) hasCustomModelDataMethod.invoke(meta);
                if (hasCustomModelData) {
                    isCustomFishingRod = true;
                }
            } catch (Exception e) {
                try {
                    java.lang.reflect.Method getItemTagMethod = item.getClass().getMethod("getItemTag");
                    Object nbtTag = getItemTagMethod.invoke(item);

                    if (nbtTag != null) {
                        java.lang.reflect.Method hasKeyMethod = nbtTag.getClass().getMethod("hasKey", String.class);
                        boolean hasFishingRodTag = (boolean) hasKeyMethod.invoke(nbtTag, "FishingRod");
                        if (hasFishingRodTag) {
                            isCustomFishingRod = true;
                        }
                    }
                } catch (Exception ex) {
                }
            }

            return isCustomFishingRod;
        }

        return false;
    }
}
