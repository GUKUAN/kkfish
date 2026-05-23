package me.kkfish.handlers;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import me.kkfish.kkfish;
import me.kkfish.gui.GUIHolder;
import me.kkfish.managers.GUI;
import me.kkfish.managers.Cmd;
import me.kkfish.managers.Config;

public class GUIAction {
    private final Plugin plugin;
    
    public GUIAction(Plugin plugin) {
        this.plugin = plugin;
    }
    
    public void handleActions(Player player, List<String> actions, InventoryClickEvent event) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        
        for (String action : actions) {
            if (action == null || action.trim().isEmpty()) {
                continue;
            }
            
            handleAction(player, action, event);
        }
    }
    
    private void handleAction(Player player, String action, InventoryClickEvent event) {
        action = action.trim();
        
        if (action.startsWith("[sound]")) {
            handleSoundAction(player, action);
        } else if (action.equals("[close]")) {
            handleCloseAction(event);
        } else if (action.startsWith("[action]")) {
            handlePluginAction(player, action, event);
        } else if (action.startsWith("[console]")) {
            handleConsoleAction(player, action);
        } else if (action.startsWith("[op]")) {
            handleOpAction(player, action);
        } else if (action.startsWith("[player]")) {
            handlePlayerCommandAction(player, action);
        }
    }
    
    private void handleSoundAction(Player player, String action) {
        String[] parts = action.substring(7).trim().split(" ");
        if (parts.length < 2) {
            return;
        }
        
        String soundName = parts[0];
        float volume = 1.0f;
        float pitch = 1.0f;
        
        try {
            if (parts.length > 1) {
                volume = Float.parseFloat(parts[1]);
            }
            if (parts.length > 2) {
                pitch = Float.parseFloat(parts[2]);
            }
        } catch (NumberFormatException e) {
            return;
        }
        
        player.playSound(player.getLocation(), soundName, volume, pitch);
    }
    
    private void handleCloseAction(InventoryClickEvent event) {
        event.getWhoClicked().closeInventory();
    }
    
    private void handlePluginAction(Player player, String action, InventoryClickEvent event) {
        String actionContent = action.substring(8).trim();
        String[] parts = actionContent.split(" ");
        
        if (parts.length == 0) {
            return;
        }
        
        String actionType = parts[0].toLowerCase();
        
        switch (actionType) {
            case "buy":
                handleBuyAction(player, parts, event);
                break;
            case "sell":
                handleSellAction(player);
                break;
            case "open":
            case "open_gui":
                handleOpenAction(player, parts);
                break;
            case "fish_dex_page":
                handleFishDexPageAction(player, parts);
                break;
            case "hook_page":
                handleHookPageAction(player, parts);
                break;
            case "view_competition":
                handleViewCompetitionAction(player, parts);
                break;
        }
    }
    
    private void handleBuyAction(Player player, String[] parts, InventoryClickEvent event) {
        if (parts.length < 3) {
            return;
        }
        
        String itemType = parts[1].toLowerCase();
        String itemId = parts[2];
        boolean hookIdResolved = true;
        
        if (itemId.equals("%hook_id%")) {
            me.kkfish.kkfish kkfishPlugin = (me.kkfish.kkfish) plugin;
            GUI guiManager = kkfishPlugin.getGUI();
            
            int clickedSlot = event.getRawSlot();
            
            int currentPage = guiManager.getCurrentHookMaterialPage(player);
            
            String hookName = guiManager.getHookIdFromSlot(player, clickedSlot, currentPage);
            
            if (hookName != null) {
                itemId = hookName;
            } else {
                if (event.getInventory().getHolder() instanceof GUIHolder) {
                    GUIHolder holder = (GUIHolder) event.getInventory().getHolder();
                    int holderPage = holder.getPage();
                    hookName = guiManager.getHookIdFromSlot(player, clickedSlot, holderPage);
                    if (hookName != null) {
                        itemId = hookName;
                    } else {
                        player.sendMessage(kkfishPlugin.getMessageManager().getMessage(player, "hook_id_not_recognized", "§c无法识别鱼钩ID，请联系管理员。"));
                        hookIdResolved = false;
                    }
                } else {
                    player.sendMessage(kkfishPlugin.getMessageManager().getMessage(player, "hook_id_not_recognized", "§c无法识别鱼钩ID，请联系管理员。"));
                    hookIdResolved = false;
                }
            }
        }
        
        if (!hookIdResolved) {
            return;
        }
        
        me.kkfish.kkfish kkfishPlugin = (me.kkfish.kkfish) plugin;
        GUI guiManager = kkfishPlugin.getGUI();
        Config configManager = kkfishPlugin.getCustomConfig();
        me.kkfish.misc.MessageManager messageManager = kkfishPlugin.getMessageManager();
        
        if (configManager.hasHookMaterialPermission(player, itemId)) {
            guiManager.setPlayerHookMaterial(player, itemId);
            return;
        }
        
        if (!itemType.equals("hook")) {
            return;
        }
        
        if (!configManager.isHookNeedPurchase(itemId)) {
            player.sendMessage(messageManager.getMessage(player, "hook_no_permission", "§c你没有权限使用这个鱼钩！"));
            return;
        }
        
        boolean isLeftClick = event.isLeftClick();
        boolean isRightClick = event.isRightClick();
        
        boolean purchaseSuccess = false;
        
        if (isLeftClick && configManager.canPurchaseWithVault(itemId)) {
            net.milkbowl.vault.economy.Economy economy = kkfishPlugin.getEconomy();
            if (economy == null) {
                player.sendMessage(messageManager.getMessage(player, "hook_purchase_vault_unavailable", "§c经济系统未启用，无法使用金币购买！"));
                return;
            }
            
            double vaultPrice = configManager.getHookVaultPrice(itemId);
            double balance = economy.getBalance(player);
            
            if (balance < vaultPrice) {
                player.sendMessage(messageManager.getMessage(player, "hook_purchase_insufficient_vault", "§c金币不足！还需 %.2f 金币", vaultPrice - balance));
                return;
            }
            
            net.milkbowl.vault.economy.EconomyResponse response = economy.withdrawPlayer(player, vaultPrice);
            if (!response.transactionSuccess()) {
                player.sendMessage(messageManager.getMessage(player, "hook_purchase_deduct_failed", "§c扣除金币失败，请稍后再试。"));
                return;
            }
            
            purchaseSuccess = true;
        } else if (isRightClick && configManager.canPurchaseWithPoints(itemId)) {
            org.black_ixx.playerpoints.PlayerPointsAPI pointsAPI = kkfishPlugin.getPlayerPointsAPI();
            if (pointsAPI == null) {
                player.sendMessage(messageManager.getMessage(player, "hook_purchase_points_unavailable", "§c点卷系统未启用，无法使用点卷购买！"));
                return;
            }
            
            int pointsPrice = configManager.getHookPointsPrice(itemId);
            int currentPoints = pointsAPI.look(player.getUniqueId());
            
            if (currentPoints < pointsPrice) {
                player.sendMessage(messageManager.getMessage(player, "hook_purchase_insufficient_points", "§c点卷不足！还需 %d 点卷", pointsPrice - currentPoints));
                return;
            }
            
            if (!pointsAPI.take(player.getUniqueId(), pointsPrice)) {
                player.sendMessage(messageManager.getMessage(player, "hook_purchase_deduct_failed", "§c扣除点卷失败，请稍后再试。"));
                return;
            }
            
            purchaseSuccess = true;
        } else {
            player.sendMessage(messageManager.getMessage(player, "hook_purchase_invalid_click", "§c请使用左键 (金币) 或右键 (点卷) 购买"));
            return;
        }
        
        if (purchaseSuccess) {
            kkfishPlugin.getDB().markHookAsPurchased(player.getUniqueId().toString(), itemId);
            
            String displayName = configManager.getHookDisplayName(itemId);
            displayName = displayName.replace('§', '&');
            player.sendMessage(messageManager.getMessage(player, "hook_purchase_success", "§a成功购买鱼钩: %s！", displayName));
            
            guiManager.setPlayerHookMaterial(player, itemId);
        }
    }
    
    private void handleSellAction(Player player) {
        me.kkfish.kkfish kkfishPlugin = (me.kkfish.kkfish) plugin;
        if (!kkfishPlugin.getCustomConfig().isEconomyEnabled()) {
            player.sendMessage(kkfishPlugin.getMessageManager().getMessage(player, "economy_not_enabled", "§c经济系统未启用，无法使用卖出功能！"));
            return;
        }
        
        plugin.getLogger().info("Player " + player.getName() + " selling items");
        
        me.kkfish.managers.Cmd commandManager = kkfishPlugin.getCmd();
        
        try {
            java.lang.reflect.Method sellAllFishMethod = commandManager.getClass().getDeclaredMethod("sellAllFish", Player.class);
            sellAllFishMethod.setAccessible(true);
            sellAllFishMethod.invoke(commandManager, player);
        } catch (Exception e) {
            plugin.getLogger().warning(kkfishPlugin.getMessageManager().getMessageWithoutPrefix("log.sell_operation_failed", "执行出售操作失败: ") + e.getMessage());
            player.sendMessage(kkfishPlugin.getMessageManager().getMessage(player, "sell_operation_failed", "§c出售操作失败，请稍后再试。"));
        }
    }
    
    private void handleOpenAction(Player player, String[] parts) {
        if (parts.length < 2) {
            return;
        }
        
        String menuName = parts[1];
        
        if (menuName.equalsIgnoreCase("sell_gui")) {
            me.kkfish.kkfish kkfishPlugin = (me.kkfish.kkfish) plugin;
            if (!kkfishPlugin.getCustomConfig().isEconomyEnabled()) {
                player.sendMessage(kkfishPlugin.getMessageManager().getMessage(player, "economy_not_enabled", "§c经济系统未启用，无法使用卖出功能！"));
                return;
            }
        }
        
        me.kkfish.kkfish kkfishPlugin = (me.kkfish.kkfish) plugin;
        GUI guiManager = kkfishPlugin.getGUI();
        
        switch (menuName.toLowerCase()) {
            case "main_menu":
                guiManager.openGUI(player, GUI.GUIType.MAIN_MENU);
                break;
            case "hook_material":
                guiManager.openGUI(player, GUI.GUIType.HOOK_MATERIAL);
                break;
            case "fish_dex":
                guiManager.openGUI(player, GUI.GUIType.FISH_DEX);
                break;
            case "fish_record":
                guiManager.openGUI(player, GUI.GUIType.FISH_RECORD);
                break;
            case "help_gui":
                guiManager.openGUI(player, GUI.GUIType.HELP_GUI);
                break;
            case "competition_category":
                guiManager.openGUI(player, GUI.GUIType.COMPETITION_CATEGORY);
                break;
            case "reward_preview":
                guiManager.openGUI(player, GUI.GUIType.REWARD_PREVIEW);
                break;
            case "sell_gui":
                guiManager.openGUI(player, GUI.GUIType.SELL_GUI);
                break;
            default:
                plugin.getLogger().warning("未知的菜单名称: " + menuName);
        }
    }
    
    private void handleFishDexPageAction(Player player, String[] parts) {
        if (parts.length < 2) {
            return;
        }
        
        String direction = parts[1].toLowerCase();
        
        me.kkfish.kkfish kkfishPlugin = (me.kkfish.kkfish) plugin;
        GUI guiManager = kkfishPlugin.getGUI();
        
        if (direction.equals("previous")) {
            guiManager.handleFishDexPage(player, false);
        } else if (direction.equals("next")) {
            guiManager.handleFishDexPage(player, true);
        }
    }
    
    private void handleHookPageAction(Player player, String[] parts) {
        if (parts.length < 2) {
            return;
        }
        
        String direction = parts[1].toLowerCase();
        
        me.kkfish.kkfish kkfishPlugin = (me.kkfish.kkfish) plugin;
        GUI guiManager = kkfishPlugin.getGUI();
        
        if (direction.equals("previous")) {
            guiManager.handleHookMaterialPage(player, false);
        } else if (direction.equals("next")) {
            guiManager.handleHookMaterialPage(player, true);
        }
    }
    
    private void handleViewCompetitionAction(Player player, String[] parts) {
        if (parts.length < 2) {
            return;
        }
        
        String competitionId = parts[1];
        
        me.kkfish.kkfish kkfishPlugin = (me.kkfish.kkfish) plugin;
        GUI guiManager = kkfishPlugin.getGUI();
        
        guiManager.openRewardPreview(player, competitionId);
    }
    
    private void handleConsoleAction(Player player, String action) {
        String command = action.substring(9).trim();
        if (!command.isEmpty()) {
            command = command.replace("%player_name%", player.getName());
            command = command.replace("%player%", player.getName());
            command = command.replace("%p", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }
    
    private void handleOpAction(Player player, String action) {
        String command = action.substring(4).trim();
        if (!command.isEmpty()) {
            command = command.replace("%player_name%", player.getName());
            command = command.replace("%player%", player.getName());
            command = command.replace("%p", player.getName());
            boolean wasOp = player.isOp();
            player.setOp(true);
            Bukkit.dispatchCommand(player, command);
            player.setOp(wasOp);
        }
    }
    
    private void handlePlayerCommandAction(Player player, String action) {
        String command = action.substring(8).trim();
        if (!command.isEmpty()) {
            command = command.replace("%player_name%", player.getName());
            command = command.replace("%player%", player.getName());
            command = command.replace("%p", player.getName());
            player.performCommand(command);
        }
    }
}