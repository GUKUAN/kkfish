package me.kkfish.managers;

import java.util.Set;

import org.bukkit.command.CommandSender;

import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;

/**
 * 竞赛命令处理器：负责 /kkfish compete 的所有子命令。
 * 从 Cmd 抽取，职责单一。
 */
public class CompeteCommandHandler {

    private final kkfish plugin;
    private final MessageManager messageManager;

    public CompeteCommandHandler(kkfish plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    /**
     * 处理竞赛子命令。
     */
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return false;
        }

        Compete competitionManager = plugin.getCompete();
        if (competitionManager == null) {
            sender.sendMessage(messageManager.getMessage("competition_not_initialized", "§cCompetition system not initialized!"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "start":
                return handleStart(sender, args);
            case "stop":
                return handleStop(sender, args);
            case "forcestop":
                return handleForceStop(sender, args);
            case "list":
                return handleList(sender);
            default:
                sender.sendMessage(messageManager.getMessage("competition_unknown_subcommand", "§cUnknown subcommand: %command%", subCommand));
                return false;
        }
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (sender instanceof org.bukkit.entity.Player && !sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§dYou do not have permission to execute this command"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("competition_specify_config_id", "§cPlease specify competition config ID!"));
            return true;
        }

        String configId = args[1];
        int duration = 0;
        if (args.length > 2) {
            try {
                duration = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(messageManager.getMessage("competition_invalid_duration", "§cInvalid duration, using config default"));
            }
        }

        boolean started = plugin.getCompete().startCompetitionManually(configId, duration);
        if (started) {
            sender.sendMessage(messageManager.getMessage("competition_started_success", "§aCompetition started: %id%", configId));
        } else {
            sender.sendMessage(messageManager.getMessage("competition_started_failed", "§cFailed to start competition: config not found or already running"));
        }
        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        if (sender instanceof org.bukkit.entity.Player && !sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§dYou do not have permission to execute this command"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("competition_specify_competition_id", "§cPlease specify competition ID to stop!"));
            return true;
        }

        String competitionId = args[1];
        boolean stopped = plugin.getCompete().stopCompetitionManually(competitionId);
        if (stopped) {
            sender.sendMessage(messageManager.getMessage("competition_stopped_success", "§aCompetition stopped: %id%", competitionId));
        } else {
            sender.sendMessage(messageManager.getMessage("competition_stopped_failed", "§cFailed to stop competition: not found or not running"));
        }
        return true;
    }

    private boolean handleForceStop(CommandSender sender, String[] args) {
        if (sender instanceof org.bukkit.entity.Player && !sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§dYou do not have permission to execute this command"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("competition_specify_competition_id", "§cPlease specify competition ID to force stop!"));
            return true;
        }

        String forceCompetitionId = args[1];
        boolean forceStopped = plugin.getCompete().forceStopCompetitionManually(forceCompetitionId);
        if (forceStopped) {
            sender.sendMessage(messageManager.getMessage("competition_force_stopped_success", "§aCompetition force stopped and settled: %id%", forceCompetitionId));
        } else {
            sender.sendMessage(messageManager.getMessage("competition_force_stopped_failed", "§cFailed to force stop competition: not found or not running"));
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        Compete competitionManager = plugin.getCompete();
        Set<String> configIds = competitionManager.getCompetitionConfigIds();
        if (configIds.isEmpty()) {
            sender.sendMessage(messageManager.getMessage("competition_no_configs", "§eNo competition configs available"));
        } else {
            sender.sendMessage(messageManager.getMessage("competition_config_list_title", "§e===== Competition Config List ====="));
            for (String id : configIds) {
                sender.sendMessage(messageManager.getMessage("competition_config_item", "§f- %id%", id));
            }
        }

        Set<String> activeIds = competitionManager.getActiveCompetitionIds();
        if (!activeIds.isEmpty()) {
            sender.sendMessage(messageManager.getMessage("competition_active_title", "§aActive competitions:"));
            for (String id : activeIds) {
                sender.sendMessage(messageManager.getMessage("competition_active_item", "§f- %id%", id));
            }
        }
        return true;
    }
}
