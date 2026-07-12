package me.kkfish.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import me.kkfish.economy.EconomyService;
import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;

/**
 * 命令分发器：负责 /kkfish 命令的参数解析和子命令分发，
 * 具体逻辑委托给 {@link SellCommandHandler}、{@link GiveCommandHandler}、
 * {@link CompeteCommandHandler}、{@link ConfigCommandHandler}、{@link AdminCommandHandler}。
 *
 * <p>本类只负责：
 * <ul>
 *   <li>子命令分发</li>
 *   <li>Tab 自动补全</li>
 *   <li>help 消息</li>
 * </ul></p>
 */
public class Cmd implements CommandExecutor, TabCompleter {

    private final kkfish plugin;
    private final MessageManager messageManager;
    private final List<String> subCommands = Arrays.asList(
            "help", "reload", "debug", "give", "gui", "version", "sell", "compete", "add", "unlock", "lock", "sellgui", "toggle");

    // 子命令处理器
    private final SellCommandHandler sellHandler;
    private final GiveCommandHandler giveHandler;
    private final CompeteCommandHandler competeHandler;
    private final ConfigCommandHandler configHandler;
    private final AdminCommandHandler adminHandler;

    public Cmd(kkfish plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();

        // 构造子处理器
        this.sellHandler = new SellCommandHandler(plugin);
        this.giveHandler = new GiveCommandHandler(plugin);
        this.competeHandler = new CompeteCommandHandler(plugin);
        this.configHandler = new ConfigCommandHandler(plugin, sellHandler);
        this.adminHandler = new AdminCommandHandler(plugin);

        plugin.getCommand("kkfish").setExecutor(this);
        plugin.getCommand("kkfish").setTabCompleter(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help":
                sendHelp(sender);
                break;
            case "reload":
                configHandler.reloadConfig(sender);
                break;
            case "debug":
                configHandler.toggleDebug(sender);
                break;
            case "give":
                handleGive(sender, args);
                break;
            case "gui":
                handleGui(sender, args);
                break;
            case "version":
                adminHandler.checkVersion(sender);
                break;
            case "sellgui":
                handleSellGui(sender, args);
                break;
            case "sell":
                handleSell(sender, args);
                break;
            case "compete":
                String[] competeArgs = new String[args.length - 1];
                System.arraycopy(args, 1, competeArgs, 0, args.length - 1);
                if (!competeHandler.handle(sender, competeArgs)) {
                    sendHelp(sender);
                }
                break;
            case "add":
                handleAdd(sender, args);
                break;
            case "unlock":
                handleUnlock(sender, args);
                break;
            case "lock":
                handleLock(sender, args);
                break;
            case "toggle":
                adminHandler.handleModeCommand(sender, args);
                break;
            default:
                sender.sendMessage(messageManager.getMessage("unknown_command", "§cUnknown command, use /kkfish help to see available commands."));
                break;
        }

        return true;
    }

    // ==================== 子命令处理 ====================

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§dYou do not have permission to execute this command"));
            return;
        }

        if (args.length > 2) {
            int amount = 1;
            if (args.length > 3) {
                try {
                    amount = Integer.parseInt(args[3]);
                    if (amount < 1) {
                        amount = 1;
                    } else if (amount > 64) {
                        amount = 64;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(messageManager.getMessage("invalid_number", "§dInvalid amount, using default value 1"));
                    amount = 1;
                }
            }
            giveHandler.giveItem(sender, args[1], args[2], amount);
        } else {
            sender.sendMessage(messageManager.getMessage("give_usage", "§dUsage: /kkfish give <player> <item_name> [amount]"));
        }
    }

    private void handleGui(CommandSender sender, String[] args) {
        if (args.length >= 3 && sender.hasPermission("kkfish.admin")) {
            String guiType = args[1].toLowerCase();
            String targetPlayerName = args[2];
            Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);

            if (targetPlayer == null) {
                sender.sendMessage(messageManager.getMessage("player_not_found", "§dPlayer not found: %s", targetPlayerName));
                return;
            }

            boolean guiOpened = false;
            switch (guiType) {
                case "main":
                case "menu":
                    plugin.getGUI().openMainMenu(targetPlayer);
                    guiOpened = true;
                    break;
                case "hook":
                case "material":
                case "hookmaterial":
                    plugin.getGUI().openHookMaterial(targetPlayer);
                    guiOpened = true;
                    break;
                case "dex":
                case "fishdex":
                    plugin.getGUI().openFishDex(targetPlayer);
                    guiOpened = true;
                    break;
                case "record":
                case "fishrecord":
                    plugin.getGUI().openFishRecord(targetPlayer);
                    guiOpened = true;
                    break;
                case "help":
                    plugin.getGUI().openHelp(targetPlayer);
                    guiOpened = true;
                    break;
                case "rodshop":
                case "rods":
                    plugin.getGUI().openRodShop(targetPlayer);
                    guiOpened = true;
                    break;
                default:
                    sender.sendMessage(messageManager.getMessage("gui_unknown_type", "§cUnknown GUI type, use main, hook, dex, record, help or rodshop"));
                    break;
            }

            if (guiOpened && !(sender instanceof Player)) {
                sender.sendMessage(messageManager.getMessage("gui_opened_for_player", "§aOpened %s GUI for player %s~", targetPlayer.getName(), guiType));
            }
        } else if (sender instanceof Player) {
            Player player = (Player) sender;

            if (args.length < 2) {
                plugin.getCustomConfig().debugLog(plugin.getMessageManager().getMessageWithoutPrefix("command_gui_permission_check", "Player %s attempted to use GUI command, permission check: kkfish.use=%s", player.getName(), player.hasPermission("kkfish.use")));
                plugin.getGUI().openMainMenu(player);
            } else {
                String guiType = args[1].toLowerCase();
                switch (guiType) {
                    case "main":
                    case "menu":
                        plugin.getGUI().openMainMenu(player);
                        break;
                    case "hook":
                    case "material":
                    case "hookmaterial":
                        plugin.getGUI().openHookMaterial(player);
                        break;
                    case "dex":
                    case "fishdex":
                        plugin.getGUI().openFishDex(player);
                        break;
                    case "record":
                    case "fishrecord":
                        plugin.getGUI().openFishRecord(player);
                        break;
                    case "help":
                        plugin.getGUI().openHelp(player);
                        break;
                    case "rodshop":
                    case "rods":
                        plugin.getGUI().openRodShop(player);
                        break;
                    default:
                        player.sendMessage(messageManager.getMessage("gui_unknown_type", "§cUnknown GUI type, use main, hook, dex, record, help or rodshop"));
                        break;
                }
            }
        } else {
            sender.sendMessage(messageManager.getMessage("gui_console_usage", "§cConsole must specify player name, use: /kf gui <gui_type> <player>"));
        }
    }

    private void handleSellGui(CommandSender sender, String[] args) {
        if (!isPriceAvailable()) {
            sender.sendMessage(messageManager.getMessage("economy_not_enabled", "§cEconomy system is not enabled, selling is unavailable!"));
            return;
        }
        if (sender instanceof Player) {
            Player player = (Player) sender;
            plugin.getGUI().openSellGUI(player);
        } else {
            if (args.length >= 2) {
                String targetPlayerName = args[1];
                Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
                if (targetPlayer != null) {
                    plugin.getGUI().openSellGUI(targetPlayer);
                    sender.sendMessage(messageManager.getMessage("gui_opened_for_player", "Opened sell GUI for player %s", targetPlayer.getName()));
                } else {
                    sender.sendMessage(messageManager.getMessage("player_not_found", "Player not found: %s", targetPlayerName));
                }
            } else {
                sender.sendMessage(messageManager.getMessage("gui_console_usage", "Console must specify player name, use: /kf sellgui <player>"));
            }
        }
    }

    private void handleSell(CommandSender sender, String[] args) {
        if (!isPriceAvailable()) {
            sender.sendMessage(messageManager.getMessage("economy_not_enabled", "§cEconomy system is not enabled, selling is unavailable!"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messageManager.getMessage("sell_usage", "§dUsage: /kf sell <all|hand>"));
            if (sender.hasPermission("kkfish.admin")) {
                sender.sendMessage(plugin.getMessageManager().getMessage("sell_admin_usage", "§dAdmin usage: /kf sell <all|hand> <player>"));
            }
        } else if (args.length == 3 && sender.hasPermission("kkfish.admin")) {
            String option = args[1].toLowerCase();
            String targetPlayerName = args[2];
            Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);

            if (targetPlayer == null) {
                sender.sendMessage(messageManager.getMessage("player_not_found", "§dPlayer not found: %s", targetPlayerName));
                return;
            }

            if ("all".equals(option)) {
                if (sender instanceof Player) {
                    sellHandler.sellAllFishForOther((Player) sender, targetPlayer);
                } else {
                    EconomyService.SellPay consolePay = sellHandler.sellAllFishConsole(sender, targetPlayer);
                    if (consolePay != null) {
                        return;
                    }
                    int totalValue = sellHandler.sellAllFishConsole(targetPlayer);
                    if (totalValue > 0) {
                        sender.sendMessage(plugin.getMessageManager().getMessage(sellHandler.sellRewardKey("sell_help_all_success_op"),
                                sellHandler.isPointRewardActive() ? "§aHelped player %s sell all fish! Received %s points~" : "§aHelped player %s sell all fish! Received %s coins~",
                                targetPlayer.getName(), totalValue));
                        targetPlayer.sendMessage(plugin.getMessageManager().getMessage(sellHandler.sellRewardKey("sell_help_all_success_player"),
                                sellHandler.isPointRewardActive() ? "§aConsole helped you sell all fish! Received %s points~" : "§aConsole helped you sell all fish! Received %s coins~",
                                totalValue));
                    } else {
                        sender.sendMessage(plugin.getMessageManager().getMessage("sell_help_all_empty", "§cPlayer %s has no fish that can be sold~", targetPlayer.getName()));
                    }
                }
            } else if ("hand".equals(option)) {
                if (sender instanceof Player) {
                    sellHandler.sellHandheldFishForOther((Player) sender, targetPlayer);
                } else {
                    EconomyService.SellPay consolePay = sellHandler.sellHandheldFishConsole(sender, targetPlayer);
                    if (consolePay != null) {
                        return;
                    }
                    ItemStack item = targetPlayer.getInventory().getItemInMainHand();
                    if (item == null || item.getType() == Material.AIR) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("sell_help_hand_empty", "§cPlayer %s has no item in their hand~", targetPlayer.getName()));
                        return;
                    }

                    int value = sellHandler.getFishValueFromItem(item);
                    if (value <= 0) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("sell_help_not_fish", "§cThis item cannot be sold~"));
                        return;
                    }

                    if (!sellHandler.addMoneyToPlayer(targetPlayer, value)) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("sell_operation_failed", "§cSale failed, please try again later."));
                        return;
                    }

                    item.setAmount(item.getAmount() - 1);
                    sender.sendMessage(plugin.getMessageManager().getMessage(sellHandler.sellRewardKey("sell_help_hand_success_op"),
                            sellHandler.isPointRewardActive() ? "§aHelped player %s sell hand item! Received %s points~" : "§aHelped player %s sell hand item! Received %s coins~",
                            targetPlayer.getName(), value));
                    targetPlayer.sendMessage(plugin.getMessageManager().getMessage(sellHandler.sellRewardKey("sell_help_hand_success_player"),
                            sellHandler.isPointRewardActive() ? "§aConsole helped you sell hand item! Received %s points~" : "§aConsole helped you sell hand item! Received %s coins~",
                            value));
                }
            } else {
                sender.sendMessage(messageManager.getMessage("sell_invalid_option", "§dInvalid option, use all or hand"));
            }
        } else if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.hasPermission("kkfish.sell") && !player.hasPermission("kkfish.admin")) {
                player.sendMessage(messageManager.getMessage("no_permission", "§dYou do not have permission to execute this command"));
                return;
            }

            String option = args[1].toLowerCase();
            if ("all".equals(option)) {
                sellHandler.sellAllFish(player);
            } else if ("hand".equals(option)) {
                sellHandler.sellHandheldFish(player);
            } else {
                player.sendMessage(messageManager.getMessage("sell_invalid_option", "§dInvalid option, use all or hand"));
            }
        } else {
            sender.sendMessage(plugin.getMessageManager().getMessage("command_console_gui_usage", "§cConsole must specify player name, use: /kf sell <all|hand> <player>"));
        }
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§dYou do not have permission to execute this command"));
            return;
        }
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length < 2) {
                sender.sendMessage(messageManager.getMessage("add_usage", "§dUsage: /kkfish add <fish|rods|baits> [item_name]"));
                return;
            }
            String addType = args[1].toLowerCase();
            configHandler.handleAddCommand(player, addType, args);
        } else {
            sender.sendMessage(messageManager.getMessage("player_only_command", "§cThis command can only be executed by a player in-game!"));
        }
    }

    private void handleUnlock(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§dYou do not have permission to execute this command"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(messageManager.getMessage("unlock_usage", "§dUsage: /kkfish unlock <player> <fish_name|all>"));
            return;
        }

        String targetPlayerName = args[1];
        String fishName = args[2];

        Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
if (targetPlayer == null) {
                sender.sendMessage(messageManager.getMessage("player_not_found", "§dPlayer not found: %s", targetPlayerName));
                return;
            }

        adminHandler.unlockFishForPlayer(sender, targetPlayer, fishName);
    }

    private void handleLock(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§dYou do not have permission to execute this command"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.getMessageManager().getMessage("lock_command_usage", "§dUsage: /kkfish lock <player> <fish_name|all>"));
            return;
        }

        String lockTargetPlayerName = args[1];
        String lockFishName = args[2];

        Player lockTargetPlayer = plugin.getServer().getPlayer(lockTargetPlayerName);
        if (lockTargetPlayer == null) {
            sender.sendMessage(messageManager.getMessage("player_not_found", "§dPlayer not found: %s", lockTargetPlayerName));
            return;
        }

        adminHandler.lockFishForPlayer(sender, lockTargetPlayer, lockFishName);
    }

    // ==================== Tab 补全 ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        boolean isPriceEnabled = isPriceAvailable();

        if (args.length == 1) {
            List<String> availableCommands = new ArrayList<>(subCommands);
            if (!isPriceEnabled) {
                availableCommands.remove("sell");
                availableCommands.remove("sellgui");
            }
            StringUtil.copyPartialMatches(args[0], availableCommands, completions);

        } else if (args.length == 2 && "gui".equals(args[0].toLowerCase())) {
            List<String> guiTypes = Arrays.asList("main", "hook", "dex", "record", "help");
            StringUtil.copyPartialMatches(args[1], guiTypes, completions);
        } else if (args.length == 2 && "give".equals(args[0].toLowerCase())) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (StringUtil.startsWithIgnoreCase(player.getName(), args[1])) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2 && "compete".equals(args[0].toLowerCase())) {
            List<String> competeActions = Arrays.asList("start", "stop", "list");
            StringUtil.copyPartialMatches(args[1], competeActions, completions);
        } else if (args.length == 2 && "add".equals(args[0].toLowerCase())) {
            List<String> addTypes = Arrays.asList("fish", "rods", "baits");
            StringUtil.copyPartialMatches(args[1], addTypes, completions);

        } else if (args.length == 3 && "give".equals(args[0].toLowerCase())) {
            if (args[2].startsWith("fish:")) {
                String prefix = args[2].substring(5);
                Config configManager = plugin.getCustomConfig();
                if (configManager.getFishConfig().contains("fish")) {
                    for (String fishName : configManager.getFishConfig().getConfigurationSection("fish").getKeys(false)) {
                        if (StringUtil.startsWithIgnoreCase(fishName, prefix)) {
                            completions.add("fish:" + fishName);
                        }
                    }
                }
            } else if (args[2].startsWith("rod:")) {
                String prefix = args[2].substring(4);
                Config configManager = plugin.getCustomConfig();
                if (configManager.getRodConfig().contains("rods")) {
                    for (String rodName : configManager.getRodConfig().getConfigurationSection("rods").getKeys(false)) {
                        if (StringUtil.startsWithIgnoreCase(rodName, prefix)) {
                            completions.add("rod:" + rodName);
                        }
                    }
                }
            } else if (args[2].startsWith("baits:")) {
                String prefix = args[2].substring(6);
                Config configManager = plugin.getCustomConfig();
                if (configManager.getBaitConfig().contains("baits")) {
                    for (String baitName : configManager.getAllBaitNames()) {
                        if (StringUtil.startsWithIgnoreCase(baitName, prefix)) {
                            completions.add("baits:" + baitName);
                        }
                    }
                }
            } else {
                List<String> itemTypes = Arrays.asList("fish:", "rod:", "baits:");
                StringUtil.copyPartialMatches(args[2], itemTypes, completions);
            }
        } else if (args.length == 2 && "unlock".equals(args[0].toLowerCase())) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (StringUtil.startsWithIgnoreCase(player.getName(), args[1])) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && "unlock".equals(args[0].toLowerCase())) {
            List<String> options = new ArrayList<>();
            options.add("all");

            Config configManager = plugin.getCustomConfig();
            if (configManager.getFishConfig().contains("fish")) {
                for (String fishName : configManager.getFishConfig().getConfigurationSection("fish").getKeys(false)) {
                    if (StringUtil.startsWithIgnoreCase(fishName, args[2])) {
                        options.add(fishName);
                    }
                }
            }

            StringUtil.copyPartialMatches(args[2], options, completions);
        } else if (args.length == 2 && "lock".equals(args[0].toLowerCase())) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (StringUtil.startsWithIgnoreCase(player.getName(), args[1])) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && "lock".equals(args[0].toLowerCase())) {
            List<String> options = new ArrayList<>();
            options.add("all");

            Config configManager = plugin.getCustomConfig();
            if (configManager.getFishConfig().contains("fish")) {
                for (String fishName : configManager.getFishConfig().getConfigurationSection("fish").getKeys(false)) {
                    if (StringUtil.startsWithIgnoreCase(fishName, args[2])) {
                        options.add(fishName);
                    }
                }
            }

            StringUtil.copyPartialMatches(args[2], options, completions);
        } else if (args.length == 2 && "sell".equals(args[0].toLowerCase()) && isPriceEnabled) {
            List<String> options = Arrays.asList("all", "hand");
            StringUtil.copyPartialMatches(args[1], options, completions);
        } else if (args.length == 3 && "sell".equals(args[0].toLowerCase()) && sender.hasPermission("kkfish.admin") && isPriceEnabled) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (StringUtil.startsWithIgnoreCase(player.getName(), args[2])) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && "compete".equals(args[0].toLowerCase()) && "start".equals(args[1].toLowerCase())) {
            Compete competitionManager = plugin.getCompete();
            if (competitionManager != null) {
                Set<String> configIds = competitionManager.getCompetitionConfigIds();
                StringUtil.copyPartialMatches(args[2], configIds, completions);
            }
        } else if (args.length == 2 && "toggle".equals(args[0].toLowerCase())) {
            List<String> modeOptions = Arrays.asList("plugin", "vanilla");
            StringUtil.copyPartialMatches(args[1], modeOptions, completions);
        }

        return completions;
    }

    // ==================== Help ====================

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(messageManager.getMessage("help_message", "§6===== KKFish Help =====\n§a/kf give <player> <fish_name> [amount] - Give a fish to a player\n§a/kf reload - Reload plugin config\n§a/kf debug - Toggle debug mode\n§a/kf version - Check plugin version\n§a/kf gui [main|hook|dex|record|help] - Open fishing GUI\n§a/kf sell <all|hand> - Sell fish from inventory or hand\n§a/kf sell <all|hand> <player> - [OP] Help another player sell items\n§a/kf compete <start|stop|list> [compete_id] [duration] - [OP] Manage fishing competitions\n§a/kf unlock <player> <fish_name|all> - [OP] Unlock fish dex entries for a player\n§a/kf lock <player> <fish_name|all> - [OP] Lock fish dex entries for a player\n§6===== KKFish Help ====="));
    }

    // ==================== 外部访问 ====================

    private boolean isPriceAvailable() {
        return plugin.getCustomConfig().isPriceEnabled()
                && plugin.getEconomyService() != null
                && plugin.getEconomyService().isEconomyEnabled();
    }

    public List<String> getSubCommands() {
        return new ArrayList<>(subCommands);
    }

    public boolean hasFishingRod(Player player) {
        return adminHandler.hasFishingRod(player);
    }

    public SellCommandHandler getSellHandler() {
        return sellHandler;
    }

    public GiveCommandHandler getGiveHandler() {
        return giveHandler;
    }
}
