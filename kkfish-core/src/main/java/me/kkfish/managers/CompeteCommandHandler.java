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
            sender.sendMessage(messageManager.getMessage("competition_not_initialized", "§c比赛功能未初始化!"));
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
                sender.sendMessage(messageManager.getMessage("competition_unknown_subcommand", "§c未知的子命令: %command%", subCommand));
                return false;
        }
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (sender instanceof org.bukkit.entity.Player && !sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("competition_specify_config_id", "§c请指定比赛配置ID!"));
            return true;
        }

        String configId = args[1];
        int duration = 0;
        if (args.length > 2) {
            try {
                duration = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(messageManager.getMessage("competition_invalid_duration", "§c无效的持续时间，将使用配置中的默认值"));
            }
        }

        boolean started = plugin.getCompete().startCompetitionManually(configId, duration);
        if (started) {
            sender.sendMessage(messageManager.getMessage("competition_started_success", "§a成功启动比赛: %id%", configId));
        } else {
            sender.sendMessage(messageManager.getMessage("competition_started_failed", "§c启动比赛失败: 配置不存在或比赛已在进行中"));
        }
        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        if (sender instanceof org.bukkit.entity.Player && !sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("competition_specify_competition_id", "§c请指定要停止的比赛ID!"));
            return true;
        }

        String competitionId = args[1];
        boolean stopped = plugin.getCompete().stopCompetitionManually(competitionId);
        if (stopped) {
            sender.sendMessage(messageManager.getMessage("competition_stopped_success", "§a成功停止比赛: %id%", competitionId));
        } else {
            sender.sendMessage(messageManager.getMessage("competition_stopped_failed", "§c停止比赛失败: 比赛不存在或未在进行中"));
        }
        return true;
    }

    private boolean handleForceStop(CommandSender sender, String[] args) {
        if (sender instanceof org.bukkit.entity.Player && !sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("competition_specify_competition_id", "§c请指定要强制停止的比赛ID!"));
            return true;
        }

        String forceCompetitionId = args[1];
        boolean forceStopped = plugin.getCompete().forceStopCompetitionManually(forceCompetitionId);
        if (forceStopped) {
            sender.sendMessage(messageManager.getMessage("competition_force_stopped_success", "§a成功强制停止并结算比赛: %id%", forceCompetitionId));
        } else {
            sender.sendMessage(messageManager.getMessage("competition_force_stopped_failed", "§c强制停止比赛失败: 比赛不存在或未在进行中"));
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        Compete competitionManager = plugin.getCompete();
        Set<String> configIds = competitionManager.getCompetitionConfigIds();
        if (configIds.isEmpty()) {
            sender.sendMessage(messageManager.getMessage("competition_no_configs", "§e当前没有比赛配置"));
        } else {
            sender.sendMessage(messageManager.getMessage("competition_config_list_title", "§e===== 比赛配置列表 ====="));
            for (String id : configIds) {
                sender.sendMessage(messageManager.getMessage("competition_config_item", "§f- %id%", id));
            }
        }

        Set<String> activeIds = competitionManager.getActiveCompetitionIds();
        if (!activeIds.isEmpty()) {
            sender.sendMessage(messageManager.getMessage("competition_active_title", "§a正在进行的比赛:"));
            for (String id : activeIds) {
                sender.sendMessage(messageManager.getMessage("competition_active_item", "§f- %id%", id));
            }
        }
        return true;
    }
}
