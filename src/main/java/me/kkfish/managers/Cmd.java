package me.kkfish.managers;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Logger;

import me.kkfish.kkfish;
import me.kkfish.managers.GUI;
import me.kkfish.managers.Compete;
import me.kkfish.utils.XSeriesUtil;
import me.kkfish.managers.Config;
import me.kkfish.managers.DB;
import me.kkfish.misc.MessageManager;
import me.kkfish.misc.UpdateChecker;

public class Cmd implements CommandExecutor, TabCompleter {

    private final kkfish plugin;
    private final Logger logger;
    private final MessageManager messageManager;
    private final Map<String, Double> fishValueCache = new ConcurrentHashMap<>();
    private final List<String> subCommands = Arrays.asList("help", "reload", "debug", "give", "gui", "version", "sell", "compete", "add", "unlock", "lock", "sellgui", "toggle");
    


    public Cmd(kkfish plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.messageManager = kkfish.getInstance().getMessageManager();
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
                reloadConfig(sender);
                break;

            case "debug":
                toggleDebug(sender);
                break;
            case "give":
                if (!sender.hasPermission("kkfish.admin")) {
                    sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
                    return true;
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
                            sender.sendMessage(messageManager.getMessage("invalid_number", "§d无效的数量，将使用默认值1"));
                            amount = 1;
                        }
                    }
                    giveItem(sender, args[1], args[2], amount);
                } else {
                    sender.sendMessage(messageManager.getMessage("give_usage", "§d用法: /kkfish give <玩家名> <物品名> [数量]"));
                }
                break;
            case "gui":
                if (args.length >= 3 && sender.hasPermission("kkfish.admin")) {
                    String guiType = args[1].toLowerCase();
                    String targetPlayerName = args[2];
                    Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
                    
                    if (targetPlayer == null) {
                        sender.sendMessage(messageManager.getMessage("player_not_found", "§d找不到玩家: %s", targetPlayerName));
                        return true;
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
                        default:
                            sender.sendMessage(messageManager.getMessage("gui_unknown_type", "§c未知的GUI类型，请使用 main, hook, dex, record 或 help"));
                            break;
                    }
                    
                    if (guiOpened && !(sender instanceof Player)) {
                        sender.sendMessage(messageManager.getMessage("gui_opened_for_player", "§a已为玩家%s打开了%s界面～", targetPlayer.getName(), guiType));
                    }
                } else if (sender instanceof Player) {
                    Player player = (Player)sender;
                    if (logger.isLoggable(java.util.logging.Level.FINE)) {
                        logger.info(plugin.getMessageManager().getMessageWithoutPrefix("command_gui_permission_check", "玩家 %s 尝试使用GUI命令，权限检查结果: kkfish.use=%s", player.getName(), player.hasPermission("kkfish.use")));
                    }
                    
                    if (args.length < 2) {
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
                            default:
                            player.sendMessage(messageManager.getMessage("gui_unknown_type", "§c未知的GUI类型，请使用 main, hook, dex, record 或 help"));
                            break;
                        }
                    }
                } else {
                    sender.sendMessage(messageManager.getMessage("gui_console_usage", "§c控制台必须指定玩家名，请使用: /kf gui <gui类型> <玩家名>"));
                }
                break;
            case "version":
                checkVersion(sender);
                break;
            case "sellgui":
                if (!plugin.getCustomConfig().isPriceEnabled()) {
                    sender.sendMessage(messageManager.getMessage("economy_not_enabled", "§c经济系统未启用，无法使用卖出功能！"));
                    break;
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
                            sender.sendMessage(messageManager.getMessage("gui_opened_for_player", "已为玩家%s打开了卖出界面", targetPlayer.getName()));
                        } else {
                            sender.sendMessage(messageManager.getMessage("player_not_found", "找不到玩家: %s", targetPlayerName));
                        }
                    } else {
                        sender.sendMessage(messageManager.getMessage("gui_console_usage", "控制台必须指定玩家名，请使用: /kf sellgui <玩家名>"));
                    }
                }
                break;
            case "sell":
                if (!plugin.getCustomConfig().isPriceEnabled()) {
                    sender.sendMessage(messageManager.getMessage("economy_not_enabled", "§c经济系统未启用，无法使用出售功能！"));
                    break;
                }
                if (args.length < 2) {
                    sender.sendMessage(messageManager.getMessage("sell_usage", "§d用法: /kf sell <all|hand>"));
                    if (sender.hasPermission("kkfish.admin")) {
                        sender.sendMessage(plugin.getMessageManager().getMessage("sell_admin_usage", "§d管理员用法: /kf sell <all|hand> <玩家名>"));
                    }
                } else if (args.length == 3 && sender.hasPermission("kkfish.admin")) {
                    String option = args[1].toLowerCase();
                    String targetPlayerName = args[2];
                    Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
                    
                    if (targetPlayer == null) {
                        sender.sendMessage(messageManager.getMessage("player_not_found", "§d找不到玩家: %s", targetPlayerName));
                        return true;
                    }
                    
                    if ("all".equals(option)) {
                        if (sender instanceof Player) {
                            sellAllFishForOther((Player)sender, targetPlayer);
                        } else {
                            int totalValue = sellAllFishConsole(targetPlayer);
                            if (totalValue > 0) {
                                sender.sendMessage(plugin.getMessageManager().getMessage("sell_help_all_success_op", "§a已帮助玩家 %s 出售所有鱼类！获得了 %s 金币～", targetPlayer.getName(), totalValue));
                                targetPlayer.sendMessage(plugin.getMessageManager().getMessage("sell_help_all_success_player", "§a控制台已帮助你出售所有鱼类！获得了 %s 金币～", totalValue));
                            } else {
                                sender.sendMessage(plugin.getMessageManager().getMessage("sell_help_all_empty", "§c玩家 %s 的背包中没有可出售的鱼～", targetPlayer.getName()));
                            }
                        }
                    } else if ("hand".equals(option)) {
                        if (sender instanceof Player) {
                            sellHandheldFishForOther((Player)sender, targetPlayer);
                        } else {
                            ItemStack item = targetPlayer.getInventory().getItemInMainHand();
                            if (item == null || item.getType() == Material.AIR) {
                                sender.sendMessage(plugin.getMessageManager().getMessage("sell_help_hand_empty", "§c玩家 %s 手中没有物品哦～", targetPlayer.getName()));
                                return true;
                            }
                            
                            int value = getFishValueFromItem(item);
                            if (value <= 0) {
                                sender.sendMessage(plugin.getMessageManager().getMessage("sell_help_not_fish", "§c这不是可以出售的鱼～"));
                                return true;
                            }
                            
                            item.setAmount(item.getAmount() - 1);
                            addMoneyToPlayer(targetPlayer, value);
                            sender.sendMessage(plugin.getMessageManager().getMessage("sell_help_hand_success_op", "§a已帮助玩家 %s 出售手中物品！获得了 %s 金币～", targetPlayer.getName(), value));
                            targetPlayer.sendMessage(plugin.getMessageManager().getMessage("sell_help_hand_success_player", "§a控制台已帮助你出售手中物品！获得了 %s 金币～", value));
                        }
                    } else {
                        sender.sendMessage(messageManager.getMessage("sell_invalid_option", "§d无效的选项，请使用all或hand"));
                    }
                } else if (sender instanceof Player) {
                    Player player = (Player)sender;
                    if (!player.hasPermission("kkfish.sell") && !player.hasPermission("kkfish.admin")) {
                        player.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
                        return true;
                    }
                    
                    String option = args[1].toLowerCase();
                    if ("all".equals(option)) {
                        sellAllFish(player);
                    } else if ("hand".equals(option)) {
                        sellHandheldFish(player);
                    } else {
                        player.sendMessage(messageManager.getMessage("sell_invalid_option", "§d无效的选项，请使用all或hand"));
                    }
                } else {
                    sender.sendMessage(plugin.getMessageManager().getMessage("command_console_gui_usage", "§c控制台必须指定玩家名，请使用: /kf sell <all|hand> <玩家名>"));
                }
                break;

            case "compete":
                String[] competeArgs = new String[args.length - 1];
                System.arraycopy(args, 1, competeArgs, 0, args.length - 1);
                handleCompeteCommand(sender, competeArgs);
                break;
            case "add":
                if (!sender.hasPermission("kkfish.admin")) {
                    sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
                    return true;
                }
                if (sender instanceof Player) {
                    Player player = (Player)sender;
                    if (args.length < 2) {
                        sender.sendMessage(messageManager.getMessage("add_usage", "§d用法: /kkfish add <fish|rods|baits> [物品名]"));
                        return true;
                    }
                    String addType = args[1].toLowerCase();
                    handleAddCommand(player, addType, args);
                } else {
                    sender.sendMessage(messageManager.getMessage("player_only_command", "§c此命令只能由玩家在游戏内执行！"));
                }
                break;
            case "unlock":
                if (!sender.hasPermission("kkfish.admin")) {
                    sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
                    return true;
                }
                
                if (args.length < 3) {
                    sender.sendMessage(messageManager.getMessage("unlock_usage", "§d用法: /kkfish unlock <玩家名> <fish_name|all>"));
                    return true;
                }
                
                String targetPlayerName = args[1];
                String fishName = args[2];
                
                Player targetPlayer = plugin.getServer().getPlayer(targetPlayerName);
                if (targetPlayer == null) {
                    sender.sendMessage(messageManager.getMessage("player_not_found", "§d找不到玩家: %s", targetPlayerName));
                    return true;
                }
                
                unlockFishForPlayer(sender, targetPlayer, fishName);
                break;
                
            case "lock":
                if (!sender.hasPermission("kkfish.admin")) {
                    sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
                    return true;
                }
                
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("lock_command_usage", "§d用法: /kkfish lock <玩家名> <fish_name|all>"));
                    return true;
                }
                
                String lockTargetPlayerName = args[1];
                String lockFishName = args[2];
                
                Player lockTargetPlayer = plugin.getServer().getPlayer(lockTargetPlayerName);
                if (lockTargetPlayer == null) {
                    sender.sendMessage(messageManager.getMessage("player_not_found", "§d找不到玩家: %s", lockTargetPlayerName));
                    return true;
                }
                
                lockFishForPlayer(sender, lockTargetPlayer, lockFishName);
                break;
            case "toggle":
                handleModeCommand(sender, args);
                break;
            default:
                sender.sendMessage(messageManager.getMessage("unknown_command", "§c未知命令，请使用 /kkfish help 查看可用命令。"));
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        boolean isPriceEnabled = plugin.getCustomConfig().isPriceEnabled();

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

    private void sendHelp(CommandSender sender) {
        MessageManager messageManager = kkfish.getInstance().getMessageManager();
        sender.sendMessage(messageManager.getMessage("help_message", "§6===== 钓鱼插件帮助 =====\n§a/kf give <玩家名> <鱼名> [数量] - 给予指定玩家一条鱼\n§a/kf reload - 重载插件配置\n§a/kf debug - 切换调试模式\n§a/kf version - 检查插件版本\n§a/kf gui [main|hook|dex|record|help] - 打开钓鱼系统界面\n§a/kf sell <all|hand> - 出售背包中的鱼或手中的鱼\n§a/kf sell <all|hand> <玩家名> - [OP]帮助其他玩家出售物品\n§a/kf compete <start|stop|list> [比赛ID] [持续时间] - [OP]管理钓鱼比赛\n§a/kf unlock <玩家名> <fish_name|all> - [OP]解锁指定玩家的鱼类图鉴\n§a/kf lock <玩家名> <fish_name|all> - [OP]锁定指定玩家的鱼类图鉴\n§6===== 钓鱼插件帮助 ====="));
    }
    

    private void unlockFishForPlayer(CommandSender sender, Player targetPlayer, String fishName) {
        Config configManager = plugin.getCustomConfig();
        DB dbManager = plugin.getDB();
        
        if ("all".equalsIgnoreCase(fishName)) {
            // 解锁所有鱼类
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
            // 解锁指定鱼类
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
    

    private void lockFishForPlayer(CommandSender sender, Player targetPlayer, String fishName) {
        Config configManager = plugin.getCustomConfig();
        DB dbManager = plugin.getDB();
        
        if ("all".equalsIgnoreCase(fishName)) {
            // 锁定所有鱼类
            dbManager.lockFishForPlayer(targetPlayer.getUniqueId().toString(), fishName);
            sender.sendMessage(plugin.getMessageManager().getMessage("lock_all_success_op", "§a已成功为玩家%s锁定了所有鱼类图鉴！", targetPlayer.getName()));
            targetPlayer.sendMessage(plugin.getMessageManager().getMessage("lock_all_success_player", "§a管理员已为你锁定了所有鱼类图鉴！"));
        } else {
            // 锁定指定鱼类
            if (!configManager.getFishConfig().contains("fish." + fishName)) {
                sender.sendMessage(plugin.getMessageManager().getMessage("fish_not_found", "§c未找到鱼类: %s", fishName));
                return;
            }
            
            dbManager.lockFishForPlayer(targetPlayer.getUniqueId().toString(), fishName);
            
            sender.sendMessage(plugin.getMessageManager().getMessage("lock_success_op", "§a已成功为玩家%s锁定了鱼类图鉴: %s", targetPlayer.getName(), fishName));
            targetPlayer.sendMessage(plugin.getMessageManager().getMessage("lock_success_player", "§a管理员已为你锁定了鱼类图鉴: %s", fishName));
        }
    }

    private void sellHandheldFish(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(messageManager.getMessage("sell_hand_empty", "§c你手中没有物品哦～"));
            return;
        }
        
        if (plugin.getCustomConfig().isDebugMode()) {
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_sell_hand_item", "[Debug] 尝试出售手中物品: %s", item.getType().name()));
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName()) {
                    plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_display_name", "[Debug] 物品显示名称: %s", meta.getDisplayName()));
                }
                if (meta.hasLore()) {
                    plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_has_lore", "[Debug] 物品有Lore: %s行", meta.getLore().size()));
                }
            }
        }
        
        String fishUUIDStr = getFishUUIDString(item);
        
        if (plugin.getCustomConfig().isDebugMode()) {
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_uuid_obtained", "[Debug] 获取到的鱼UUID: %s", (fishUUIDStr != null ? fishUUIDStr : "null")));
        }
        
        int value = getFishValueFromItem(item);
        boolean hasRewards = hasItemRewards(item);
        
        if (plugin.getCustomConfig().isDebugMode()) {
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_reward", "[Debug] 鱼的价值: %s, 是否有物品奖励: %s", value, hasRewards));
        }
        
        if (value <= 0 && !hasRewards) {
            player.sendMessage(messageManager.getMessage("sell_not_fish", "§c这不是可以出售的鱼～"));
            return;
        }
        
        handleItemRewards(player, item);
        
        item.setAmount(item.getAmount() - 1);
        
        if (value > 0 && plugin.getCustomConfig().isEconomySystemEnabled()) {
            addMoneyToPlayer(player, value);
            player.sendMessage(messageManager.getMessage("sell_hand_success", "§a成功出售！获得了 %s 金币～", value));
        } else {
            player.sendMessage(messageManager.getMessage("sell_hand_success", "§a成功出售！获得了物品奖励～"));
        }
        
        if (fishUUIDStr != null) {
            plugin.getDB().removeFishUUIDValue(fishUUIDStr);
        }
    }
    
    private boolean hasItemRewards(ItemStack item) {
        String fishName = getItemNameFromItem(item);
        boolean hasRewards = fishName != null && plugin.getCustomConfig().getItemValue() != null && plugin.getCustomConfig().getItemValue().hasItemRewards(fishName);
        
        if (plugin.getCustomConfig().isDebugMode()) {
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_check_item_rewards", "[Debug] 检查物品是否有物品奖励: %s", hasRewards));
            if (fishName != null) {
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_name", "[Debug] 鱼的名称: %s", fishName));
            } else {
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_cannot_get_fish_name", "[Debug] 无法获取鱼的名称"));
            }
        }
        
        return hasRewards;
    }
    
    private void handleItemRewards(Player player, ItemStack item) {
        String fishName = getItemNameFromItem(item);
        if (fishName != null && plugin.getCustomConfig().getItemValue() != null) {
            List<ItemStack> itemRewards = plugin.getCustomConfig().getItemValue().getItemRewards(fishName);
            for (ItemStack reward : itemRewards) {
                player.getInventory().addItem(reward);
            }
        }
    }
    
    private String getItemNameFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasDisplayName()) {
            return null;
        }
        
        String displayName = ChatColor.stripColor(meta.getDisplayName());
        return displayName;
    }
    
    private void sellHandheldFishForOther(Player opPlayer, Player targetPlayer) {
        ItemStack item = targetPlayer.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            opPlayer.sendMessage(messageManager.getMessage("sell_other_hand_empty", "§c玩家%s手中没有物品哦～", targetPlayer.getName()));
            return;
        }
        
        String fishUUIDStr = getFishUUIDString(item);
        
        int value = getFishValueFromItem(item);
        if (value <= 0 && !hasItemRewards(item)) {
            opPlayer.sendMessage(messageManager.getMessage("sell_not_fish", "§c这不是可以出售的鱼～"));
            return;
        }
        
        handleItemRewards(targetPlayer, item);
        
        item.setAmount(item.getAmount() - 1);
        
        if (value > 0 && plugin.getCustomConfig().isEconomySystemEnabled()) {
            addMoneyToPlayer(targetPlayer, value);
            opPlayer.sendMessage(messageManager.getMessage("sell_other_hand_success_op", "§a已帮助玩家%s出售物品！获得了 %s 金币～", targetPlayer.getName(), value));
            targetPlayer.sendMessage(messageManager.getMessage("sell_other_hand_success_player", "§a管理员已帮助你出售物品！获得了 %s 金币～", value));
        } else {
            opPlayer.sendMessage(messageManager.getMessage("sell_other_hand_success_op", "§a已帮助玩家%s出售物品！获得了物品奖励～", targetPlayer.getName()));
            targetPlayer.sendMessage(messageManager.getMessage("sell_other_hand_success_player", "§a管理员已帮助你出售物品！获得了物品奖励～"));
        }
        
        if (fishUUIDStr != null) {
            plugin.getDB().removeFishUUIDValue(fishUUIDStr);
        }
    }

    public void sellAllFish(Player player) {
        int totalValue = 0;
        int soldCount = 0;
        List<String> uuidStrsToRemove = new ArrayList<>();
        boolean hasItemRewards = false;
        
        if (plugin.getCustomConfig().isDebugMode()) {
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_start_checking_inventory", "[Debug] 开始检查背包中的鱼..."));
        }
        
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            
            if (plugin.getCustomConfig().isDebugMode()) {
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_check_item_slot", "[Debug] 检查物品槽 %s: %s", i, item.getType().name()));
                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta.hasDisplayName()) {
                        plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_display_name", "[Debug] 物品显示名称: %s", meta.getDisplayName()));
                    }
                }
            }
            
            String fishUUIDStr = getFishUUIDString(item);
            if (fishUUIDStr != null) {
                uuidStrsToRemove.add(fishUUIDStr);
            }
            
            if (plugin.getCustomConfig().isDebugMode()) {
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_uuid_obtained", "[Debug] 获取到的鱼UUID: %s", (fishUUIDStr != null ? fishUUIDStr : "null")));
            }
            
            int value = getFishValueFromItem(item);
            boolean itemHasRewards = hasItemRewards(item);
            
            if (plugin.getCustomConfig().isDebugMode()) {
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_reward", "[Debug] 鱼的价值: %s, 是否有物品奖励: %s", value, itemHasRewards));
            }
            
            if (value > 0 || itemHasRewards) {
                if (itemHasRewards) {
                    handleItemRewards(player, item);
                    hasItemRewards = true;
                }
                
                if (value > 0) {
                    totalValue += value * item.getAmount();
                }
                
                soldCount += item.getAmount();
                player.getInventory().setItem(i, null);
                
                if (plugin.getCustomConfig().isDebugMode()) {
                    plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_sold", "[Debug] 已出售物品，数量: %s", item.getAmount()));
                }
            }
        }
        
        if (plugin.getCustomConfig().isDebugMode()) {
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_sell_complete", "[Debug] 出售完成，总价值: %s, 总数量: %s", totalValue, soldCount));
        }
        
        if (soldCount > 0) {
            if (totalValue > 0 && plugin.getCustomConfig().isEconomySystemEnabled()) {
                addMoneyToPlayer(player, totalValue);
                player.sendMessage(messageManager.getMessage("sell_all_success", "§a成功出售了 %s 条鱼！获得了 %s 金币～", soldCount, totalValue));
            } else if (hasItemRewards) {
                player.sendMessage(messageManager.getMessage("sell_all_success", "§a成功出售了 %s 条鱼！获得了物品奖励～", soldCount));
            }
            
            for (String uuidStr : uuidStrsToRemove) {
                plugin.getDB().removeFishUUIDValue(uuidStr);
            }
        } else {
            player.sendMessage(messageManager.getMessage("sell_all_empty", "§c你的背包里没有可以出售的鱼～"));
        }
    }
    
    private void sellAllFishForOther(Player opPlayer, Player targetPlayer) {
        int totalValue = 0;
        int soldCount = 0;
        List<String> uuidStrsToRemove = new ArrayList<>();
        boolean hasItemRewards = false;
        
        for (int i = 0; i < targetPlayer.getInventory().getSize(); i++) {
            ItemStack item = targetPlayer.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            
            String fishUUIDStr = getFishUUIDString(item);
            if (fishUUIDStr != null) {
                uuidStrsToRemove.add(fishUUIDStr);
            }
            
            int value = getFishValueFromItem(item);
            boolean itemHasRewards = hasItemRewards(item);
            
            if (value > 0 || itemHasRewards) {
                if (itemHasRewards) {
                    handleItemRewards(targetPlayer, item);
                    hasItemRewards = true;
                }
                
                if (value > 0) {
                    totalValue += value * item.getAmount();
                }
                
                soldCount += item.getAmount();
                targetPlayer.getInventory().setItem(i, null);
            }
        }
        
        if (soldCount > 0) {
            if (totalValue > 0 && plugin.getCustomConfig().isEconomySystemEnabled()) {
                addMoneyToPlayer(targetPlayer, totalValue);
                opPlayer.sendMessage(messageManager.getMessage("sell_other_all_success_op", "§a已帮助玩家%s出售了 %s 条鱼！获得了 %s 金币～", targetPlayer.getName(), soldCount, totalValue));
                targetPlayer.sendMessage(messageManager.getMessage("sell_other_all_success_player", "§a管理员已帮助你出售了 %s 条鱼！获得了 %s 金币～", soldCount, totalValue));
            } else if (hasItemRewards) {
                opPlayer.sendMessage(messageManager.getMessage("sell_other_all_success_op", "§a已帮助玩家%s出售了 %s 条鱼！获得了物品奖励～", targetPlayer.getName(), soldCount));
                targetPlayer.sendMessage(messageManager.getMessage("sell_other_all_success_player", "§a管理员已帮助你出售了 %s 条鱼！获得了物品奖励～", soldCount));
            }
            
            for (String uuidStr : uuidStrsToRemove) {
                plugin.getDB().removeFishUUIDValue(uuidStr);
            }
        } else {
            opPlayer.sendMessage(messageManager.getMessage("sell_other_all_empty", "§c玩家%s的背包里没有可以出售的鱼～", targetPlayer.getName()));
        }
    }

    private int getFishValueFromItem(ItemStack item) {
        String uuidStr = getFishUUIDString(item);
        if (uuidStr != null) {
            Double cachedValue = fishValueCache.get(uuidStr);
            if (cachedValue != null) {
                return cachedValue.intValue();
            }
            try {
                int value = plugin.getDB().getFishValueByUUID(uuidStr);
                
                if (plugin.getCustomConfig().isDebugMode()) {
                    plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_get_fish_value_db", "[Debug] 从数据库获取鱼价值: %s", value));
                }
                
                if (value > 0) {
                    fishValueCache.put(uuidStr, (double) value);
                    return value;
                }
            } catch (IllegalArgumentException e) {
                if (plugin.getCustomConfig().isDebugMode()) {
                    plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_uuid_format_error", "[Debug] UUID格式错误: %s", e.getMessage()));
                }
            }
        }
        
        if (plugin.getCustomConfig().isDebugMode()) {
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_not_found", "[Debug] 未找到鱼价值，返回0"));
        }
        
        return 0;
    }
    
    private NamespacedKey getFishUUIDKey() {
        return new NamespacedKey(plugin, "fish_uuid");
    }
    
    private String getFishUUIDString(ItemStack item) {
        Object uuidObj = me.kkfish.utils.NBTUtil.getNBTData(item, "fish_uuid");
        if (uuidObj != null) {
            if (plugin.getCustomConfig().isDebugMode()) {
                plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_uuid_found_nbtutil", "[Debug] 从NBTUtil获取到UUID: %s", uuidObj.toString()));
            }
            return uuidObj.toString();
        }
        
        if (plugin.getCustomConfig().isDebugMode()) {
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("debug_uuid_not_found", "[Debug] 未找到UUID，返回null"));
        }
        
        return null;
    }
    
    private void setFishUUIDString(ItemStack item, ItemMeta meta, String uuidStr) {
        NamespacedKey key = getFishUUIDKey();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, uuidStr);
        item.setItemMeta(meta);
    }
    
    private UUID getFishUUID(ItemStack item) {
        String uuidStr = getFishUUIDString(item);
        if (uuidStr != null) {
            try {
                return UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private void addMoneyToPlayer(Player player, int amount) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(messageManager.getMessage("economy_disabled", "§c经济系统未启用，无法获得金币～"));
            return;
        }
        
        economy.depositPlayer(player, amount);
    }

    private void reloadConfig(CommandSender sender) {
        MessageManager messageManager = kkfish.getInstance().getMessageManager();
        if (!sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
            return;
        }
        plugin.getCustomConfig().reloadConfigs();
        fishValueCache.clear();
        plugin.getMessageManager().loadMessages();
        plugin.getMessageManager().completeAllLanguageFiles();
        
        if (plugin.getCompete() != null) {
            plugin.getCompete().loadConfigs();
            plugin.getCompete().setupScheduledCompetitions();
        }
        
        if (plugin.getGUI() != null) {
            plugin.getGUI().reloadMenuConfigs();
        }
        
        plugin.getCustomConfig().checkAndAddMissingConfigs();
        
        plugin.initMetrics();
        
        sender.sendMessage(messageManager.getMessage("config_reloaded", "§a配置已成功重载！"));
    }

    private void toggleDebug(CommandSender sender) {
        MessageManager messageManager = kkfish.getInstance().getMessageManager();
        if (!sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
            return;
        }
        Config configManager = plugin.getCustomConfig();
        boolean newState = !configManager.isDebugMode();
        configManager.setDebugMode(newState);
        sender.sendMessage(messageManager.getMessage("debug_toggled", "§d调试模式已%s", newState ? "开启" : "关闭"));
    }
    
    private int sellAllFishConsole(Player targetPlayer) {
        int totalValue = 0;
        int soldCount = 0;
        List<String> uuidStrsToRemove = new ArrayList<>();
        boolean hasItemRewards = false;
        
        for (int i = 0; i < targetPlayer.getInventory().getSize(); i++) {
            ItemStack item = targetPlayer.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            
            String fishUUIDStr = getFishUUIDString(item);
            if (fishUUIDStr != null) {
                uuidStrsToRemove.add(fishUUIDStr);
            }
            
            int value = getFishValueFromItem(item);
            boolean itemHasRewards = hasItemRewards(item);
            
            if (value > 0 || itemHasRewards) {
                if (itemHasRewards) {
                    handleItemRewards(targetPlayer, item);
                    hasItemRewards = true;
                }
                
                if (value > 0) {
                    totalValue += value * item.getAmount();
                }
                
                soldCount += item.getAmount();
                targetPlayer.getInventory().setItem(i, null);
            }
        }
        
        if (soldCount > 0) {
            if (totalValue > 0 && plugin.getCustomConfig().isEconomySystemEnabled()) {
                addMoneyToPlayer(targetPlayer, totalValue);
            }
            
            for (String uuidStr : uuidStrsToRemove) {
                plugin.getDB().removeFishUUIDValue(uuidStr);
            }
        }
        
        return totalValue;
    }
    
    private void giveItem(CommandSender sender, String targetName, String itemSpec, int amount) {
        MessageManager messageManager = kkfish.getInstance().getMessageManager();
        
        if (!sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
            return;
        }
    
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(messageManager.getMessage("player_not_found", "§d找不到玩家: %s", targetName));
            return;
        }
    
        String[] parts = itemSpec.split(":");
        if (parts.length != 2) {
            sender.sendMessage(messageManager.getMessage("item_format_error", "§d物品格式错误，请使用: fish:鱼名 或 rod:鱼竿名 或 baits:鱼饵名"));
            return;
        }

        String itemType = parts[0].toLowerCase();
        String itemName = parts[1];

        try {
            ItemStack item = null;
            
            if ("fish".equals(itemType)) {
                item = plugin.getFish().createFishItem(itemName, false, target);
            } else if ("rod".equals(itemType)) {
                item = createRodItem(itemName);
            } else if ("baits".equals(itemType)) {
                item = createBaitItem(itemName);
            } else {
                sender.sendMessage(messageManager.getMessage("unknown_item_type", "§d未知物品类型，请使用: fish 或 rod 或 baits"));
                return;
            }
    
            if (item != null) {
                item.setAmount(amount);
                
                target.getInventory().addItem(item);
                sender.sendMessage(messageManager.getMessage("give_success", "§d已给予玩家 %s 物品: %s x%d", targetName, itemName, amount));
                target.sendMessage(messageManager.getMessage("receive_item", "§d你收到了物品: %s x%d", itemName, amount));
                
                if ("baits".equals(itemType) && plugin.getCustomConfig().isAutoEquipBaitEnabled()) {
                    ItemStack offhandItem = target.getInventory().getItemInOffHand();
                    if (offhandItem == null || offhandItem.getType() == Material.AIR) {
                        ItemStack baitToEquip = item.clone();
                        baitToEquip.setAmount(1);
                        target.getInventory().setItemInOffHand(baitToEquip);
                        
                        if (item.getAmount() > 1) {
                            item.setAmount(item.getAmount() - 1);
                        } else {
                            target.getInventory().remove(item);
                        }
                        
                        target.sendMessage(plugin.getMessageManager().getMessage("bait_auto_equipped", "§a已自动装备一个鱼饵到副手！"));
                    }
                }
            } else {
                sender.sendMessage(messageManager.getMessage("item_not_found", "§d找不到物品: %s", itemName));
            }
        } catch (Exception e) {
            sender.sendMessage(messageManager.getMessage("give_error", "§d给予物品时发生错误"));
            e.printStackTrace();
        }
    }
    
    private ItemStack createRodItem(String rodName) {
        Config configManager = plugin.getCustomConfig();
        if (!configManager.rodExists(rodName)) {
            return null;
        }
        
        String materialStr = configManager.getRodConfig().getString("rods." + rodName + ".material", "FISHING_ROD");
        Material material = XSeriesUtil.parseMaterial(materialStr);
        if (material == null) {
            material = XSeriesUtil.parseMaterial("FISHING_ROD");
        }
        
        ItemStack rod = new ItemStack(material, 1);
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) {
            return rod;
        }
        
        ConfigurationSection nbtSection = configManager.getRodConfig().getConfigurationSection("rods." + rodName + ".nbt");
        
        List<String> tagsList = new ArrayList<>();
        tagsList.add("自定义鱼竿");
        boolean tagsAdded = me.kkfish.utils.NBTUtil.addTags(rod, tagsList);
        
        if (tagsAdded) {
            configManager.debugLog("已为鱼竿物品添加Tags:['自定义鱼竿']标记: " + rodName);
        } else {
            configManager.debugLog("无法为鱼竿物品添加Tags标记，但将继续创建物品: " + rodName);
        }
        
        if (nbtSection != null && !nbtSection.getKeys(false).isEmpty() && configManager.isCustomNBTSupportEnabled()) {
            for (String nbtKey : nbtSection.getKeys(false)) {
                if (nbtKey.equalsIgnoreCase("CustomModelData")) {
                    continue;
                }
                
                Object value = nbtSection.get(nbtKey);
                if (value != null) {
                    boolean nbtSet = me.kkfish.utils.NBTUtil.setNBTData(rod, nbtKey, value);
                    if (!nbtSet) {
                        configManager.debugLog("无法为鱼竿物品设置NBT数据: " + nbtKey + " = " + value);
                    }
                }
            }
        }
        
        String displayName = ChatColor.translateAlternateColorCodes('&', 
                configManager.getRodConfig().getString("rods." + rodName + ".display-name", "&f" + rodName));
        meta.setDisplayName(displayName);
        
        int customModelData = configManager.getRodCustomModelData(rodName);
        if (customModelData > 0) {
            try {
                java.lang.reflect.Method setCustomModelDataMethod = meta.getClass().getMethod("setCustomModelData", Integer.class);
                if (setCustomModelDataMethod != null) {
                    setCustomModelDataMethod.invoke(meta, customModelData);
                }
            } catch (Exception e) {
            }
        }
        
        List<String> lore = new ArrayList<>();
        
        String templateName = configManager.getRodTemplateName(rodName);
        String template = configManager.getRodTemplate(templateName);
        if (template == null || template.isEmpty()) {
            template = "&6[===== 鱼竿属性 =====]\n" +
                      "&b│ 难度系数: %difficulty%\n" +
                      "&a│ 浮标区域: %float_area%\n" +
                      "&c│ 耐久度: %durability%\n" +
                      "&d│ 充能速度: %charge_speed%\n" +
                      "&d│ 咬钩几率加成: %bite_rate_bonus%\n" +
                      "&6[====================]\n" +
                      " \n" +
                      "&e✨ 特殊效果:\n" +
                      "%effects%\n" +
                      " \n" +
                      "&7钓鱼快乐~";
        }
        
        Map<String, String> variables = new HashMap<>();
        variables.put("%name%", displayName);
        variables.put("%difficulty%", String.valueOf(configManager.getRodDifficulty(rodName)));
        variables.put("%float_area%", String.valueOf(configManager.getRodFloatAreaSize(rodName)));
        
        int durability = configManager.getRodDurability(rodName);
        if (durability > 0) {
            String unit = messageManager.getMessageWithoutPrefix("rod_durability_unit", "点");
            String full = messageManager.getMessageWithoutPrefix("rod_durability_full", "(满)");
            variables.put("%durability%", durability + unit + " " + full);
        } else {
            variables.put("%durability%", messageManager.getMessageWithoutPrefix("rod_durability_infinite", "无限耐久"));
        }
        
        double chargeSpeed = configManager.getRodChargeSpeed(rodName);
        String speedText;
        if (chargeSpeed != 1.0) {
            String speedType = chargeSpeed > 1.0 ? 
                    messageManager.getMessageWithoutPrefix("rod_charge_speed_fast", "加速") : 
                    messageManager.getMessageWithoutPrefix("rod_charge_speed_slow", "减速");
            speedText = String.format("%.1f倍 (" + speedType + ")", chargeSpeed);
        } else {
            speedText = messageManager.getMessageWithoutPrefix("rod_charge_speed_normal", "正常");
        }
        variables.put("%charge_speed%", speedText);
        
        double biteRateBonus = configManager.getRodBiteRateBonus(rodName);
        variables.put("%bite_rate_bonus%", biteRateBonus > 0 ? 
                String.format("+%.1f%%", biteRateBonus * 100) : 
                messageManager.getMessageWithoutPrefix("rod_bite_rate_bonus_none", "无"));
        
        List<String> effects = configManager.getRodEffects(rodName);
        StringBuilder effectsBuilder = new StringBuilder();
        if (!effects.isEmpty()) {
            for (String effect : effects) {
                effectsBuilder.append("&7  └─ &r").append(ChatColor.translateAlternateColorCodes('&', effect)).append("\n");
            }
        } else {
            effectsBuilder.append("&7  └─ " + messageManager.getMessageWithoutPrefix("rod_effects_none", "无特殊效果") + "\n");
        }
        variables.put("%effects%", effectsBuilder.toString());
        
        String formattedTemplate = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            formattedTemplate = formattedTemplate.replace(entry.getKey(), entry.getValue());
        }
        
        String[] lines = formattedTemplate.split("\\n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            } else {
                lore.add("");
            }
        }
        
        meta.setLore(lore);
        
        rod.setItemMeta(meta);
        return rod;
    }
    
    private ItemStack createBaitItem(String baitName) {
        Config configManager = plugin.getCustomConfig();
        if (!configManager.baitExists(baitName)) {
            return null;
        }
        
        String materialStr = configManager.getBaitConfig().getString("baits." + baitName + ".material", "MAGMA_CREAM");
        Material material = XSeriesUtil.parseMaterial(materialStr);
        if (material == null) {
            material = XSeriesUtil.parseMaterial("MAGMA_CREAM");
        }
        
        ItemStack bait = new ItemStack(material, 64);
        ItemMeta meta = bait.getItemMeta();
        if (meta == null) {
            return bait;
        }
        
        boolean hasCustomNBT = configManager.getBaitConfig().getBoolean("baits." + baitName + ".has-custom-nbt", false);
        if (hasCustomNBT && configManager.isCustomNBTSupportEnabled()) {
            plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("log.command_create_item", "Creating fish item with custom NBT: %s").replace("%s", baitName));
        }
        
        String displayName = ChatColor.translateAlternateColorCodes('&', 
                configManager.getBaitConfig().getString("baits." + baitName + ".display-name", "&f" + baitName));
        meta.setDisplayName(displayName);
        
        int customModelData = configManager.getBaitCustomModelData(baitName);
        if (customModelData > 0 || customModelData == -1) {
            try {
                java.lang.reflect.Method setCustomModelDataMethod = meta.getClass().getMethod("setCustomModelData", Integer.class);
                if (setCustomModelDataMethod != null) {
                    setCustomModelDataMethod.invoke(meta, customModelData);
                }
            } catch (Exception e) {
            }
        }
        
        String templateName = configManager.getBaitTemplateName(baitName);
        String template = configManager.getBaitTemplate(templateName);
        
        StringBuilder effectsBuilder = new StringBuilder();
        List<String> effects = configManager.getBaitEffects(baitName);
        
        if (effects.size() > 0) {
            if (configManager.getBaitConfig().contains("baits." + baitName + ".effects")) {
                boolean isFirst = true;
                for (String effectType : effects) {
                    double value = configManager.getBaitEffectValueByName(baitName, effectType);
                    String effectDesc = "";
                    
                    if (effectType.equals("rare")) {
                            effectDesc = ChatColor.translateAlternateColorCodes('&', 
                                messageManager.getMessageWithoutPrefix("bait_effect_rare", "稀有鱼几率 +") + 
                                String.format("%.1f%%", value * 100));
                        } else if (effectType.equals("size")) {
                            effectDesc = ChatColor.translateAlternateColorCodes('&', 
                                messageManager.getMessageWithoutPrefix("bait_effect_size", "鱼的大小 +") + 
                                String.format("%.1f%%", value * 100));
                        } else if (effectType.equals("bite")) {
                            effectDesc = ChatColor.translateAlternateColorCodes('&', 
                                messageManager.getMessageWithoutPrefix("bait_effect_bite", "咬钩几率 +") + 
                                String.format("%.1f%%", value * 100));
                        }
                    
                    if (!effectDesc.isEmpty()) {
                        if (!isFirst) {
                            effectsBuilder.append("\n");
                        }
                        effectsBuilder.append(effectDesc);
                        isFirst = false;
                    }
                }
            } else {
                String effect = configManager.getBaitEffect(baitName);
                double value = configManager.getBaitEffectValue(baitName);
                
                if (!effect.equals("none")) {
                    if (effect.equals("rare")) {
                        effectsBuilder.append(ChatColor.translateAlternateColorCodes('&', 
                            messageManager.getMessageWithoutPrefix("bait_effect_rare", "稀有鱼几率 +") + 
                            String.format("%.1f%%", value * 100)));
                    } else if (effect.equals("size")) {
                        effectsBuilder.append(ChatColor.translateAlternateColorCodes('&', 
                            messageManager.getMessageWithoutPrefix("bait_effect_size", "鱼的大小 +") + 
                            String.format("%.1f%%", value * 100)));
                    } else if (effect.equals("bite")) {
                        effectsBuilder.append(ChatColor.translateAlternateColorCodes('&', 
                            messageManager.getMessageWithoutPrefix("bait_effect_bite", "咬钩几率 +") + 
                            String.format("%.1f%%", value * 100)));
                    }
                }
            }
        } else {
            effectsBuilder.append(ChatColor.translateAlternateColorCodes('&', messageManager.getMessageWithoutPrefix("bait_no_effects", "无特殊效果")));
        }
        
        String description = configManager.getBaitConfig().getString("baits." + baitName + ".description", "");
        if (description.isEmpty()) {
            description = ChatColor.translateAlternateColorCodes('&', messageManager.getMessageWithoutPrefix("bait_default_description", "一种特殊的鱼饵"));
        } else {
            description = ChatColor.translateAlternateColorCodes('&', description);
        }
        
        String loreContent = template
                .replace("%name%", displayName)
                .replace("%description%", description)
                .replace("%effects%", effectsBuilder.toString());
        
        List<String> lore = new ArrayList<>();
        
        for (String line : loreContent.split("\\n")) {
            lore.add(line);
        }
        
        String permission = "kkfish.baits.use." + baitName;
        lore.add("");
        lore.add(ChatColor.translateAlternateColorCodes('&', messageManager.getMessageWithoutPrefix("bait_permission_text", "权限: ") + ChatColor.WHITE + permission));
        
        lore.add(ChatColor.translateAlternateColorCodes('&', messageManager.getMessageWithoutPrefix("bait_usage_text", "放于副手，蓄力抛出时消耗")));
        
        meta.setLore(lore);
        
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        
        bait.setItemMeta(meta);
        return bait;
    }

    public List<String> getSubCommands() {
        return new ArrayList<>(subCommands);
    }
    
    private boolean handleCompeteCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }
        
        Compete competitionManager = plugin.getCompete();
        if (competitionManager == null) {
            sender.sendMessage(messageManager.getMessage("competition_not_initialized", "§c比赛功能未初始化!"));
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "start":
                if (sender instanceof Player && !sender.hasPermission("kkfish.admin")) {
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
                
                boolean started = competitionManager.startCompetitionManually(configId, duration);
                if (started) {
                    sender.sendMessage(messageManager.getMessage("competition_started_success", "§a成功启动比赛: %id%", configId));
                } else {
                    sender.sendMessage(messageManager.getMessage("competition_started_failed", "§c启动比赛失败: 配置不存在或比赛已在进行中"));
                }
                break;
                
            case "stop":
                if (sender instanceof Player && !sender.hasPermission("kkfish.admin")) {
                    sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage(messageManager.getMessage("competition_specify_competition_id", "§c请指定要停止的比赛ID!"));
                    return true;
                }
                
                String competitionId = args[1];
                boolean stopped = competitionManager.stopCompetitionManually(competitionId);
                if (stopped) {
                    sender.sendMessage(messageManager.getMessage("competition_stopped_success", "§a成功停止比赛: %id%", competitionId));
                } else {
                    sender.sendMessage(messageManager.getMessage("competition_stopped_failed", "§c停止比赛失败: 比赛不存在或未在进行中"));
                }
                break;
                
            case "forcestop":
                if (sender instanceof Player && !sender.hasPermission("kkfish.admin")) {
                    sender.sendMessage(messageManager.getMessage("no_permission", "§d你没有权限执行此命令"));
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage(messageManager.getMessage("competition_specify_competition_id", "§c请指定要强制停止的比赛ID!"));
                    return true;
                }
                
                String forceCompetitionId = args[1];
                boolean forceStopped = competitionManager.forceStopCompetitionManually(forceCompetitionId);
                if (forceStopped) {
                    sender.sendMessage(messageManager.getMessage("competition_force_stopped_success", "§a成功强制停止并结算比赛: %id%", forceCompetitionId));
                } else {
                    sender.sendMessage(messageManager.getMessage("competition_force_stopped_failed", "§c强制停止比赛失败: 比赛不存在或未在进行中"));
                }
                break;
                
            case "list":
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
                break;
                
            default:
                sender.sendMessage(messageManager.getMessage("competition_unknown_subcommand", "§c未知的子命令: %command%", subCommand));
                sendHelp(sender);
                break;
        }
        
        return true;
    }
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
    
    private void checkVersion(CommandSender sender) {
        final MessageManager messageManager = kkfish.getInstance().getMessageManager();
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
    
    private boolean isNewerVersion(String newVersion, String oldVersion) {
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
            plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("log.command_version_parse_failed", "Error parsing version number, using simple comparison method: ") + e.getMessage());
            return !newVersion.equals(oldVersion);
        }
    }
    
    private String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        
        if (hours > 0) {
            return hours + messageManager.getMessageWithoutPrefix("time.hour", "h") + 
                   minutes + messageManager.getMessageWithoutPrefix("time.minute", "m") + 
                   secs + messageManager.getMessageWithoutPrefix("time.second", "s");
        } else if (minutes > 0) {
            return minutes + messageManager.getMessageWithoutPrefix("time.minute", "m") + 
                   secs + messageManager.getMessageWithoutPrefix("time.second", "s");
        } else {
            return secs + messageManager.getMessageWithoutPrefix("time.second", "s");
        }
    }

    private void handleModeCommand(CommandSender sender, String[] args) {
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
    
    // 处理add命令，添加手持物品到配置
    private void handleAddCommand(Player player, String addType, String[] args) {
        // 获取手持物品
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(messageManager.getMessage("empty_hand", "§c请手持要添加的物品"));
            return;
        }
        
        // 解析物品名称
        String itemName = "";
        String displayName = "";
        
        if (args.length > 2) {
            // 保留原始输入的显示名称
            displayName = args[2];
            // 配置键可以使用所有符号，但需要确保有效
            // 只替换YAML中可能导致解析问题的字符，其他符号保持不变
            itemName = displayName;
            // 处理特殊情况：如果名称以减号开头，在YAML中需要特别处理
            if (itemName.startsWith("-")) {
                itemName = "_" + itemName.substring(1);
            }
            // 确保不会生成空的配置键
            if (itemName.isEmpty()) {
                itemName = "custom_" + System.currentTimeMillis();
            }
        } else {
            // 从物品显示名或类型获取名称
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                displayName = ChatColor.stripColor(meta.getDisplayName());
                // 配置键可以使用所有符号，但需要确保有效
                // 只替换YAML中可能导致解析问题的字符，其他符号保持不变
                itemName = displayName;
                // 处理特殊情况：如果名称以减号开头，在YAML中需要特别处理
                if (itemName.startsWith("-")) {
                    itemName = "_" + itemName.substring(1);
                }
                // 确保不会生成空的配置键
                if (itemName.isEmpty()) {
                    itemName = "custom_" + System.currentTimeMillis();
                }
            } else {
                itemName = item.getType().name().toLowerCase();
                displayName = itemName;
            }
        }
        
        try {
            Config configManager = plugin.getCustomConfig();
            
            // 根据类型添加到不同的配置文件
            switch (addType) {
                case "fish":
                    addFishToConfig(configManager, item, itemName);
                    player.sendMessage(messageManager.getMessage("add_fish_success", "§a成功将鱼类 '" + itemName + "' 添加到配置文件"));
                    break;
                case "rods":
                    addRodToConfig(configManager, item, itemName, displayName);
                    player.sendMessage(messageManager.getMessage("add_rod_success", "§a成功将鱼竿 '" + displayName + "' 添加到配置文件"));
                    break;
                case "baits":
                    addBaitToConfig(configManager, item, itemName);
                    player.sendMessage(messageManager.getMessage("add_bait_success", "§a成功将鱼饵 '" + itemName + "' 添加到配置文件"));
                    break;
                default:
                    player.sendMessage(messageManager.getMessage("add_invalid_type", "§c未知的添加类型，请使用: fish, rods 或 baits"));
                    break;
            }
            
            // 保存配置
            configManager.saveConfigs();
            // 重载配置
            configManager.reloadConfigs();
            
            player.sendMessage(messageManager.getMessage("add_config_edit_hint", "§e请在配置文件中进行详细修改以完善物品属性"));
        } catch (Exception e) {
            player.sendMessage(messageManager.getMessage("add_error", "§c添加物品时发生错误: " + e.getMessage()));
            e.printStackTrace();
        }
    }
    
    // 添加鱼到配置
    private void addFishToConfig(Config configManager, ItemStack item, String fishName) {
        FileConfiguration fishConfig = configManager.getFishConfig();
        
        // 检查是否已存在
        if (fishConfig.contains("fish." + fishName)) {
            int count = 1;
            while (fishConfig.contains("fish." + fishName + "_" + count)) {
                count++;
            }
            fishName = fishName + "_" + count;
        }
        
        // 设置基本配置
        fishConfig.set("fish." + fishName + ".display-name", ChatColor.stripColor(item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : "&f" + fishName));
        fishConfig.set("fish." + fishName + ".material", item.getType().name());
        fishConfig.set("fish." + fishName + ".rarity", 1);
        fishConfig.set("fish." + fishName + ".value", 10.0);
        fishConfig.set("fish." + fishName + ".exp", 5);
        fishConfig.set("fish." + fishName + ".saturation", 2);
        fishConfig.set("fish." + fishName + ".activity", 1.0);
        fishConfig.set("fish." + fishName + ".bite-rate-multiplier", 1.0);
        fishConfig.set("fish." + fishName + ".biomes", Arrays.asList("ALL"));
        fishConfig.set("fish." + fishName + ".weather", Arrays.asList("ALL"));
        fishConfig.set("fish." + fishName + ".time", Arrays.asList("DAY", "NIGHT"));
        
        // 保存NBT数据（如果有）
        try {
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().isEmpty() == false) {
                fishConfig.set("fish." + fishName + ".has-custom-nbt", true);
            }
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("log.command_save_nbt_failed", "Error saving NBT data: ") + e.getMessage());
        }
        
        // 设置等级配置
        List<Map<String, Object>> levels = new ArrayList<>();
        Map<String, Object> commonLevel = new java.util.LinkedHashMap<>();
        commonLevel.put("common", 80);
        Map<String, Object> rareLevel = new java.util.LinkedHashMap<>();
        rareLevel.put("rare", 15);
        Map<String, Object> epicLevel = new java.util.LinkedHashMap<>();
        epicLevel.put("epic", 4);
        Map<String, Object> legendaryLevel = new java.util.LinkedHashMap<>();
        legendaryLevel.put("legendary", 1);
        levels.add(commonLevel);
        levels.add(rareLevel);
        levels.add(epicLevel);
        levels.add(legendaryLevel);
        fishConfig.set("fish." + fishName + ".level", levels);
    }
    
    // 添加鱼竿到配置
    private void addRodToConfig(Config configManager, ItemStack item, String rodName, String displayName) {
        FileConfiguration rodConfig = configManager.getRodConfig();
        
        // 检查是否已存在
        if (rodConfig.contains("rods." + rodName)) {
            int count = 1;
            while (rodConfig.contains("rods." + rodName + "_" + count)) {
                count++;
            }
            rodName = rodName + "_" + count;
        }
        
        // 设置基本配置
        // 使用传入的displayName作为显示名称，确保中文能正确保存
        rodConfig.set("rods." + rodName + ".display-name", displayName);
        rodConfig.set("rods." + rodName + ".material", item.getType().name());
        rodConfig.set("rods." + rodName + ".difficulty", 1.0);
        rodConfig.set("rods." + rodName + ".float-area-size", 20);
        rodConfig.set("rods." + rodName + ".durability", 100);
        rodConfig.set("rods." + rodName + ".charge-speed", 1.0);
        rodConfig.set("rods." + rodName + ".bite-rate-bonus", 0.0);
        rodConfig.set("rods." + rodName + ".rare-fish-chance", 0.0);
        rodConfig.set("rods." + rodName + ".custom-model-data", item.getItemMeta().hasCustomModelData() ? item.getItemMeta().getCustomModelData() : 0);
        
        // 保存NBT数据（如果有）
        try {
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                PersistentDataContainer container = meta.getPersistentDataContainer();
                
                if (container.isEmpty() == false) {
                    // 设置标志表示有自定义NBT
                    rodConfig.set("rods." + rodName + ".has-custom-nbt", true);
                    
                    // 创建一个配置节来保存所有NBT数据
                    ConfigurationSection nbtSection = rodConfig.createSection("rods." + rodName + ".nbt");
                    
                    // 遍历所有NBT键
                    for (NamespacedKey key : container.getKeys()) {
                        try {
                            // 尝试获取不同类型的数据
                            if (container.has(key, PersistentDataType.STRING)) {
                                String value = container.get(key, PersistentDataType.STRING);
                                nbtSection.set(key.getKey() + ".string", value);
                            } else if (container.has(key, PersistentDataType.INTEGER)) {
                                int value = container.get(key, PersistentDataType.INTEGER);
                                nbtSection.set(key.getKey() + ".int", value);
                            } else if (container.has(key, PersistentDataType.DOUBLE)) {
                                double value = container.get(key, PersistentDataType.DOUBLE);
                                nbtSection.set(key.getKey() + ".double", value);
                            } else if (container.has(key, PersistentDataType.BYTE)) {
                                byte value = container.get(key, PersistentDataType.BYTE);
                                nbtSection.set(key.getKey() + ".byte", value);
                            } else if (container.has(key, PersistentDataType.SHORT)) {
                                short value = container.get(key, PersistentDataType.SHORT);
                                nbtSection.set(key.getKey() + ".short", value);
                            } else if (container.has(key, PersistentDataType.LONG)) {
                                long value = container.get(key, PersistentDataType.LONG);
                                nbtSection.set(key.getKey() + ".long", value);
                            } else if (container.has(key, PersistentDataType.FLOAT)) {
                                float value = container.get(key, PersistentDataType.FLOAT);
                                nbtSection.set(key.getKey() + ".float", value);
                            } else if (container.has(key, PersistentDataType.BYTE_ARRAY)) {
                                byte[] value = container.get(key, PersistentDataType.BYTE_ARRAY);
                                // 字节数组需要特殊处理，这里简单记录其存在
                                nbtSection.set(key.getKey() + ".type", "byte_array");
                            } else {
                                // 对于不支持的类型，标记为未处理类型
                                nbtSection.set(key.getKey() + ".type", "unhandled");
                            }
                        } catch (Exception ex) {
                            plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("log.command_save_nbt_key_failed", "Error saving NBT key %s: ").replace("%s", key.getKey()) + ex.getMessage());
                        }
                    }
                    
                    plugin.getLogger().info(plugin.getMessageManager().getMessageWithoutPrefix("log.command_save_nbt_success", "Saved %s NBT data items to rod configuration").replace("%s", String.valueOf(container.getKeys().size())));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("log.command_save_nbt_failed", "Error saving NBT data: ") + e.getMessage());
        }
        
        // 设置效果列表
        List<String> effects = new ArrayList<>();
        rodConfig.set("rods." + rodName + ".effects", effects);
    }
    
    // 添加鱼饵到配置
    private void addBaitToConfig(Config configManager, ItemStack item, String baitName) {
        FileConfiguration baitConfig = configManager.getBaitConfig();
        
        // 检查是否已存在
        if (baitConfig.contains("baits." + baitName)) {
            int count = 1;
            while (baitConfig.contains("baits." + baitName + "_" + count)) {
                count++;
            }
            baitName = baitName + "_" + count;
        }
        
        // 设置基本配置
        baitConfig.set("baits." + baitName + ".display-name", ChatColor.stripColor(item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : "&a" + baitName));
        baitConfig.set("baits." + baitName + ".material", item.getType().name());
        
        // 保存NBT数据（如果有）
        try {
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().isEmpty() == false) {
                baitConfig.set("baits." + baitName + ".has-custom-nbt", true);
            }
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.getMessageManager().getMessageWithoutPrefix("log.command_save_nbt_failed", "Error saving NBT data: ") + e.getMessage());
        }
        
        // 设置多效果配置
        List<String> effects = new ArrayList<>();
        effects.add("rare");
        baitConfig.set("baits." + baitName + ".effects", effects);
        
        // 设置效果值
        Map<String, Object> effectValues = new java.util.LinkedHashMap<>();
        effectValues.put("rare", 0.05); // 5% 稀有鱼几率加成
        baitConfig.set("baits." + baitName + ".effect-values", effectValues);
        
        // 设置权限
        List<String> permissions = new ArrayList<>();
        permissions.add("kkfish.baits.use." + baitName);
        baitConfig.set("baits." + baitName + ".permissions", permissions);
    }
}