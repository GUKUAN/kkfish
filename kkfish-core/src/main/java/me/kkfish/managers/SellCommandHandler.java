package me.kkfish.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.milkbowl.vault.economy.Economy;

import me.kkfish.economy.EconomyService;
import me.kkfish.economy.SellValue;
import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;

/**
 * 出售命令处理器：负责 /kkfish sell 和 sellgui 的所有逻辑。
 * 从 Cmd 抽取，职责单一。
 *
 * <p>包含：
 * <ul>
 *   <li>手中鱼出售（自己/管理员代售）</li>
 *   <li>背包全出售（自己/管理员代售/控制台）</li>
 *   <li>鱼价值读取（带 UUID 缓存）</li>
 *   <li>鱼 UUID 读写工具</li>
 *   <li>物品奖励处理</li>
 *   <li>金币发放</li>
 * </ul></p>
 */
public class SellCommandHandler {

    private final kkfish plugin;
    private final MessageManager messageManager;
    private final Map<String, SellValue> fishValueCache = new ConcurrentHashMap<>();

    public SellCommandHandler(kkfish plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    private static class SellEntry {
        private final int slot;
        private final ItemStack item;
        private final int amount;
        private final String uuidStr;
        private final boolean itemRewards;

        private SellEntry(int slot, ItemStack item, String uuidStr, boolean itemRewards) {
            this.slot = slot;
            this.item = item;
            this.amount = item.getAmount();
            this.uuidStr = uuidStr;
            this.itemRewards = itemRewards;
        }
    }

    public static class SellBatch {
        private int soldCount;
        private boolean itemRewards;
        private EconomyService.SellPay pay = new EconomyService.SellPay(0, 0);
        private final List<SellEntry> entries = new ArrayList<>();

        public int getSoldCount() {
            return soldCount;
        }

        public EconomyService.SellPay getPay() {
            return pay;
        }

        public boolean hasItemRewards() {
            return itemRewards;
        }

        public boolean hasAny() {
            return soldCount > 0;
        }
    }

    // ==================== 出售入口 ====================

    /**
     * 出售玩家自己手中的鱼。
     */
    public void sellHandheldFish(Player player) {
        if (player != null) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) {
                player.sendMessage(messageManager.getMessage("sell_hand_empty", "§cYou have no item in your hand~"));
                return;
            }

            String fishUUIDStr = getFishUUIDString(item);
            SellValue sellValue = getFishSellValueFromItem(item);
            EconomyService.SellPay pay = getPayForSellValue(sellValue);
            boolean hasRewards = hasItemRewards(item);

            if (!sellValue.hasAnyValue() && !hasRewards) {
                player.sendMessage(messageManager.getMessage("sell_not_fish", "§cThis is not a fish that can be sold~"));
                return;
            }

            if (sellValue.hasAnyValue() && !pay.hasAny() && !hasRewards) {
                player.sendMessage(messageManager.getMessage("economy_not_enabled", "§cEconomy system is not enabled, unable to receive rewards!"));
                return;
            }

            if (pay.hasAny() && !addSellPayToPlayer(player, pay)) {
                player.sendMessage(messageManager.getMessage("sell_operation_failed", "§cSale failed, please try again later."));
                return;
            }

            handleItemRewards(player, item);
            item.setAmount(item.getAmount() - 1);

            if (pay.hasAny()) {
                player.sendMessage(sellRewardMessage("sell_hand_success", pay,
                        "§aSuccessfully sold! Received %s coins~",
                        "§aSuccessfully sold! Received %s points~",
                        "§aSuccessfully sold! Received %s coins and %s points~"));
            } else {
                player.sendMessage(messageManager.getMessage("sell_hand_success_items", "§aSuccessfully sold! Received item rewards~"));
            }

            if (fishUUIDStr != null) {
                plugin.getDB().removeFishUUIDValue(fishUUIDStr);
            }
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(messageManager.getMessage("sell_hand_empty", "§cYou have no item in your hand~"));
            return;
        }

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_sell_hand_item", "[Debug] Attempting to sell hand item: %s", item.getType().name()));
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName()) {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_display_name", "[Debug] Item display name: %s", meta.getDisplayName()));
                }
                if (meta.hasLore()) {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_has_lore", "[Debug] Item has lore: %s lines", meta.getLore().size()));
                }
            }
        }

        String fishUUIDStr = getFishUUIDString(item);

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_uuid_obtained", "[Debug] Obtained fish UUID: %s", (fishUUIDStr != null ? fishUUIDStr : "null")));
        }

        int value = getFishValueFromItem(item);
        boolean hasRewards = hasItemRewards(item);
        boolean canMoneyReward = canGiveSellReward();

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_reward", "[Debug] Fish value: %s, has item rewards: %s", value, hasRewards));
        }

        if (value <= 0 && !hasRewards) {
            player.sendMessage(messageManager.getMessage("sell_not_fish", "§cThis is not a fish that can be sold~"));
            return;
        }

        if (value > 0 && !canMoneyReward && !hasRewards) {
            player.sendMessage(messageManager.getMessage("economy_not_enabled", "§cEconomy system is not enabled, unable to receive rewards!"));
            return;
        }

        handleItemRewards(player, item);

        item.setAmount(item.getAmount() - 1);

        if (value > 0 && canMoneyReward) {
            if (addMoneyToPlayer(player, value)) {
                player.sendMessage(messageManager.getMessage(sellRewardKey("sell_hand_success"),
                        sellRewardDefault("§aSuccessfully sold! Received %s coins~", "§aSuccessfully sold! Received %s points~"), value));
            } else {
                player.sendMessage(messageManager.getMessage("sell_operation_failed", "§cSale failed, please try again later."));
            }
        } else {
            player.sendMessage(messageManager.getMessage("sell_hand_success", "§aSuccessfully sold! Received item rewards~"));
        }

        if (fishUUIDStr != null) {
            plugin.getDB().removeFishUUIDValue(fishUUIDStr);
        }
    }

    /**
     * 管理员代为出售目标玩家手中的鱼。
     */
    public void sellHandheldFishForOther(Player opPlayer, Player targetPlayer) {
        if (opPlayer != null && targetPlayer != null) {
            ItemStack item = targetPlayer.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) {
                opPlayer.sendMessage(messageManager.getMessage("sell_other_hand_empty", "§cPlayer %s has no item in their hand~", targetPlayer.getName()));
                return;
            }

            String fishUUIDStr = getFishUUIDString(item);
            SellValue sellValue = getFishSellValueFromItem(item);
            EconomyService.SellPay pay = getPayForSellValue(sellValue);
            boolean hasRewards = hasItemRewards(item);

            if (!sellValue.hasAnyValue() && !hasRewards) {
                opPlayer.sendMessage(messageManager.getMessage("sell_not_fish", "§cThis is not a fish that can be sold~"));
                return;
            }

            if (sellValue.hasAnyValue() && !pay.hasAny() && !hasRewards) {
                opPlayer.sendMessage(messageManager.getMessage("economy_not_enabled", "§cEconomy system is not enabled, unable to receive rewards!"));
                return;
            }

            if (pay.hasAny() && !addSellPayToPlayer(targetPlayer, pay)) {
                opPlayer.sendMessage(messageManager.getMessage("sell_operation_failed", "§cSale failed, please try again later."));
                return;
            }

            handleItemRewards(targetPlayer, item);
            item.setAmount(item.getAmount() - 1);

            if (pay.hasAny()) {
                opPlayer.sendMessage(sellRewardMessage("sell_other_hand_success_op", pay,
                        "§aHelped player %s sell item! Received %s coins~",
                        "§aHelped player %s sell item! Received %s points~",
                        "§aHelped player %s sell item! Received %s coins and %s points~",
                        targetPlayer.getName()));
                targetPlayer.sendMessage(sellRewardMessage("sell_other_hand_success_player", pay,
                        "§aAn admin helped you sell your item! Received %s coins~",
                        "§aAn admin helped you sell your item! Received %s points~",
                        "§aAn admin helped you sell your item! Received %s coins and %s points~"));
            } else {
                opPlayer.sendMessage(messageManager.getMessage("sell_other_hand_success_op_items", "§aHelped player %s sell item! Received item rewards~", targetPlayer.getName()));
                targetPlayer.sendMessage(messageManager.getMessage("sell_other_hand_success_player_items", "§aAn admin helped you sell your item! Received item rewards~"));
            }

            if (fishUUIDStr != null) {
                plugin.getDB().removeFishUUIDValue(fishUUIDStr);
            }
            return;
        }
        ItemStack item = targetPlayer.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            opPlayer.sendMessage(messageManager.getMessage("sell_other_hand_empty", "§cPlayer %s has no item in their hand~", targetPlayer.getName()));
            return;
        }

        String fishUUIDStr = getFishUUIDString(item);

        int value = getFishValueFromItem(item);
        boolean hasRewards = hasItemRewards(item);
        boolean canMoneyReward = canGiveSellReward();
        if (value <= 0 && !hasRewards) {
            opPlayer.sendMessage(messageManager.getMessage("sell_not_fish", "§cThis is not a fish that can be sold~"));
            return;
        }

        if (value > 0 && !canMoneyReward && !hasRewards) {
            opPlayer.sendMessage(messageManager.getMessage("economy_not_enabled", "§cEconomy system is not enabled, unable to receive rewards!"));
            return;
        }

        handleItemRewards(targetPlayer, item);

        item.setAmount(item.getAmount() - 1);

        if (value > 0 && canMoneyReward) {
            if (addMoneyToPlayer(targetPlayer, value)) {
                opPlayer.sendMessage(messageManager.getMessage(sellRewardKey("sell_other_hand_success_op"),
                        sellRewardDefault("§aHelped player %s sell item! Received %s coins~", "§aHelped player %s sell item! Received %s points~"), targetPlayer.getName(), value));
                targetPlayer.sendMessage(messageManager.getMessage(sellRewardKey("sell_other_hand_success_player"),
                        sellRewardDefault("§aAn admin helped you sell your item! Received %s coins~", "§aAn admin helped you sell your item! Received %s points~"), value));
            } else {
                opPlayer.sendMessage(messageManager.getMessage("sell_operation_failed", "§cSale failed, please try again later."));
            }
        } else {
            opPlayer.sendMessage(messageManager.getMessage("sell_other_hand_success_op", "§aHelped player %s sell item! Received item rewards~", targetPlayer.getName()));
            targetPlayer.sendMessage(messageManager.getMessage("sell_other_hand_success_player", "§aAn admin helped you sell your item! Received item rewards~"));
        }

        if (fishUUIDStr != null) {
            plugin.getDB().removeFishUUIDValue(fishUUIDStr);
        }
    }

    /**
     * 出售玩家自己背包中的所有鱼。
     */
    public void sellAllFish(Player player) {
        if (player != null) {
            SellBatch batch = collectSellItems(player);
            if (!batch.hasAny()) {
                player.sendMessage(messageManager.getMessage("sell_all_empty", "§cYou have no fish in your inventory that can be sold~"));
                return;
            }

            if (batch.getPay().hasAny() && !addSellPayToPlayer(player, batch.getPay())) {
                player.sendMessage(messageManager.getMessage("sell_operation_failed", "§cSale failed, please try again later."));
                return;
            }

            applySellBatch(player, batch);

            if (batch.getPay().hasAny()) {
                player.sendMessage(sellRewardMessage("sell_all_success", batch.getPay(),
                        "§aSuccessfully sold %s fish! Received %s coins~",
                        "§aSuccessfully sold %s fish! Received %s points~",
                        "§aSuccessfully sold %s fish! Received %s coins and %s points~",
                        batch.getSoldCount()));
            } else if (batch.hasItemRewards()) {
                player.sendMessage(messageManager.getMessage("sell_all_success_items", "§aSuccessfully sold %s fish! Received item rewards~", batch.getSoldCount()));
            }
            return;
        }
        int totalValue = 0;
        int soldCount = 0;
        List<String> uuidStrsToRemove = new ArrayList<>();
        boolean hasItemRewards = false;
        boolean canMoneyReward = canGiveSellReward();

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_start_checking_inventory", "[Debug] Started checking inventory for fish..."));
        }

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            if (plugin.getCustomConfig().isDebugMode()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_check_item_slot", "[Debug] Checking item slot %s: %s", i, item.getType().name()));
                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta.hasDisplayName()) {
                        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_display_name", "[Debug] Item display name: %s", meta.getDisplayName()));
                    }
                }
            }

            String fishUUIDStr = getFishUUIDString(item);
            if (fishUUIDStr != null) {
                uuidStrsToRemove.add(fishUUIDStr);
            }

            if (plugin.getCustomConfig().isDebugMode()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_uuid_obtained", "[Debug] Obtained fish UUID: %s", (fishUUIDStr != null ? fishUUIDStr : "null")));
            }

            int value = getFishValueFromItem(item);
            boolean itemHasRewards = hasItemRewards(item);

            if (plugin.getCustomConfig().isDebugMode()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_reward", "[Debug] Fish value: %s, has item rewards: %s", value, itemHasRewards));
            }

            if ((value > 0 && canMoneyReward) || itemHasRewards) {
                if (itemHasRewards) {
                    handleItemRewards(player, item);
                    hasItemRewards = true;
                }

                if (value > 0 && canMoneyReward) {
                    totalValue += value * item.getAmount();
                }

                soldCount += item.getAmount();
                player.getInventory().setItem(i, null);

                if (plugin.getCustomConfig().isDebugMode()) {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_sold", "[Debug] Sold item, amount: %s", item.getAmount()));
                }
            }
        }

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_sell_complete", "[Debug] Sale complete, total value: %s, total count: %s", totalValue, soldCount));
        }

        if (soldCount > 0) {
            if (totalValue > 0 && canMoneyReward) {
                if (addMoneyToPlayer(player, totalValue)) {
                    player.sendMessage(messageManager.getMessage(sellRewardKey("sell_all_success"),
                            sellRewardDefault("§aSuccessfully sold %s fish! Received %s coins~", "§aSuccessfully sold %s fish! Received %s points~"), soldCount, totalValue));
                } else {
                    player.sendMessage(messageManager.getMessage("sell_operation_failed", "§cSale failed, please try again later."));
                }
            } else if (hasItemRewards) {
                player.sendMessage(messageManager.getMessage("sell_all_success", "§aSuccessfully sold %s fish! Received item rewards~", soldCount));
            }

            for (String uuidStr : uuidStrsToRemove) {
                plugin.getDB().removeFishUUIDValue(uuidStr);
            }
        } else {
            player.sendMessage(messageManager.getMessage("sell_all_empty", "§cYou have no fish in your inventory that can be sold~"));
        }
    }

    /**
     * 管理员代为出售目标玩家背包中的所有鱼。
     */
    public void sellAllFishForOther(Player opPlayer, Player targetPlayer) {
        if (opPlayer != null && targetPlayer != null) {
            SellBatch batch = collectSellItems(targetPlayer);
            if (!batch.hasAny()) {
                opPlayer.sendMessage(messageManager.getMessage("sell_other_all_empty", "§cPlayer %s has no fish in their inventory that can be sold~", targetPlayer.getName()));
                return;
            }

            if (batch.getPay().hasAny() && !addSellPayToPlayer(targetPlayer, batch.getPay())) {
                opPlayer.sendMessage(messageManager.getMessage("sell_operation_failed", "§cSale failed, please try again later."));
                return;
            }

            applySellBatch(targetPlayer, batch);

            if (batch.getPay().hasAny()) {
                opPlayer.sendMessage(sellRewardMessage("sell_other_all_success_op", batch.getPay(),
                        "§aHelped player %s sell %s fish! Received %s coins~",
                        "§aHelped player %s sell %s fish! Received %s points~",
                        "§aHelped player %s sell %s fish! Received %s coins and %s points~",
                        targetPlayer.getName(), batch.getSoldCount()));
                targetPlayer.sendMessage(sellRewardMessage("sell_other_all_success_player", batch.getPay(),
                        "§aAn admin helped you sell %s fish! Received %s coins~",
                        "§aAn admin helped you sell %s fish! Received %s points~",
                        "§aAn admin helped you sell %s fish! Received %s coins and %s points~",
                        batch.getSoldCount()));
            } else if (batch.hasItemRewards()) {
                opPlayer.sendMessage(messageManager.getMessage("sell_other_all_success_op_items", "§aHelped player %s sell %s fish! Received item rewards~", targetPlayer.getName(), batch.getSoldCount()));
                targetPlayer.sendMessage(messageManager.getMessage("sell_other_all_success_player_items", "§aAn admin helped you sell %s fish! Received item rewards~", batch.getSoldCount()));
            }
            return;
        }
        int totalValue = 0;
        int soldCount = 0;
        List<String> uuidStrsToRemove = new ArrayList<>();
        boolean hasItemRewards = false;
        boolean canMoneyReward = canGiveSellReward();

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

            if ((value > 0 && canMoneyReward) || itemHasRewards) {
                if (itemHasRewards) {
                    handleItemRewards(targetPlayer, item);
                    hasItemRewards = true;
                }

                if (value > 0 && canMoneyReward) {
                    totalValue += value * item.getAmount();
                }

                soldCount += item.getAmount();
                targetPlayer.getInventory().setItem(i, null);
            }
        }

        if (soldCount > 0) {
            if (totalValue > 0 && canMoneyReward) {
                if (addMoneyToPlayer(targetPlayer, totalValue)) {
                    opPlayer.sendMessage(messageManager.getMessage(sellRewardKey("sell_other_all_success_op"),
                            sellRewardDefault("§aHelped player %s sell %s fish! Received %s coins~", "§aHelped player %s sell %s fish! Received %s points~"), targetPlayer.getName(), soldCount, totalValue));
                    targetPlayer.sendMessage(messageManager.getMessage(sellRewardKey("sell_other_all_success_player"),
                            sellRewardDefault("§aAn admin helped you sell %s fish! Received %s coins~", "§aAn admin helped you sell %s fish! Received %s points~"), soldCount, totalValue));
                } else {
                    opPlayer.sendMessage(messageManager.getMessage("sell_operation_failed", "§cSale failed, please try again later."));
                }
            } else if (hasItemRewards) {
                opPlayer.sendMessage(messageManager.getMessage("sell_other_all_success_op", "§aHelped player %s sell %s fish! Received item rewards~", targetPlayer.getName(), soldCount));
                targetPlayer.sendMessage(messageManager.getMessage("sell_other_all_success_player", "§aAn admin helped you sell %s fish! Received item rewards~", soldCount));
            }

            for (String uuidStr : uuidStrsToRemove) {
                plugin.getDB().removeFishUUIDValue(uuidStr);
            }
        } else {
            opPlayer.sendMessage(messageManager.getMessage("sell_other_all_empty", "§cPlayer %s has no fish in their inventory that can be sold~", targetPlayer.getName()));
        }
    }

    /**
     * 控制台代为出售目标玩家背包中的所有鱼，返回总价值。
     */
    public EconomyService.SellPay sellAllFishConsole(CommandSender sender, Player targetPlayer) {
        SellBatch batch = collectSellItems(targetPlayer);
        if (!batch.hasAny()) {
            if (sender != null) {
                sender.sendMessage(messageManager.getMessage("sell_help_all_empty", "§cPlayer %s has no fish that can be sold~", targetPlayer.getName()));
            }
            return new EconomyService.SellPay(0, 0);
        }

        if (batch.getPay().hasAny() && !addSellPayToPlayer(targetPlayer, batch.getPay())) {
            if (sender != null) {
                sender.sendMessage(messageManager.getMessage("sell_operation_failed", "§cSale failed, please try again later."));
            }
            return new EconomyService.SellPay(0, 0);
        }

        applySellBatch(targetPlayer, batch);

        if (sender != null) {
            if (batch.getPay().hasAny()) {
                sender.sendMessage(sellRewardMessage("sell_help_all_success_op", batch.getPay(),
                        "§aHelped player %s sell all fish! Received %s coins~",
                        "§aHelped player %s sell all fish! Received %s points~",
                        "§aHelped player %s sell all fish! Received %s coins and %s points~",
                        targetPlayer.getName()));
                targetPlayer.sendMessage(sellRewardMessage("sell_help_all_success_player", batch.getPay(),
                        "§aConsole helped you sell all fish! Received %s coins~",
                        "§aConsole helped you sell all fish! Received %s points~",
                        "§aConsole helped you sell all fish! Received %s coins and %s points~"));
            } else if (batch.hasItemRewards()) {
                sender.sendMessage(messageManager.getMessage("sell_help_all_success_op_items", "§aHelped player %s sell all fish! Received item rewards~", targetPlayer.getName()));
                targetPlayer.sendMessage(messageManager.getMessage("sell_help_all_success_player_items", "§aConsole helped you sell all fish! Received item rewards~"));
            }
        }

        return batch.getPay();
    }

    public int sellAllFishConsole(Player targetPlayer) {
        if (targetPlayer != null) {
            return sellAllFishConsole(null, targetPlayer).getTotalAmount();
        }
        int totalValue = 0;
        int soldCount = 0;
        List<String> uuidStrsToRemove = new ArrayList<>();
        boolean hasItemRewards = false;
        boolean canMoneyReward = canGiveSellReward();

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

            if ((value > 0 && canMoneyReward) || itemHasRewards) {
                if (itemHasRewards) {
                    handleItemRewards(targetPlayer, item);
                    hasItemRewards = true;
                }

                if (value > 0 && canMoneyReward) {
                    totalValue += value * item.getAmount();
                }

                soldCount += item.getAmount();
                targetPlayer.getInventory().setItem(i, null);
            }
        }

        if (soldCount > 0) {
            if (totalValue > 0 && canMoneyReward) {
                addMoneyToPlayer(targetPlayer, totalValue);
            }

            for (String uuidStr : uuidStrsToRemove) {
                plugin.getDB().removeFishUUIDValue(uuidStr);
            }
        }

        return totalValue;
    }

    public EconomyService.SellPay sellHandheldFishConsole(CommandSender sender, Player targetPlayer) {
        ItemStack item = targetPlayer.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            sender.sendMessage(messageManager.getMessage("sell_help_hand_empty", "§cPlayer %s has no item in their hand~", targetPlayer.getName()));
            return new EconomyService.SellPay(0, 0);
        }

        String fishUUIDStr = getFishUUIDString(item);
        SellValue sellValue = getFishSellValueFromItem(item);
        EconomyService.SellPay pay = getPayForSellValue(sellValue);
        boolean hasRewards = hasItemRewards(item);

        if (!sellValue.hasAnyValue() && !hasRewards) {
            sender.sendMessage(messageManager.getMessage("sell_help_not_fish", "§cThis is not a fish that can be sold~"));
            return new EconomyService.SellPay(0, 0);
        }

        if (sellValue.hasAnyValue() && !pay.hasAny() && !hasRewards) {
            sender.sendMessage(messageManager.getMessage("economy_not_enabled", "§cEconomy system is not enabled, unable to receive rewards!"));
            return new EconomyService.SellPay(0, 0);
        }

        if (pay.hasAny() && !addSellPayToPlayer(targetPlayer, pay)) {
            sender.sendMessage(messageManager.getMessage("sell_operation_failed", "§cSale failed, please try again later."));
            return new EconomyService.SellPay(0, 0);
        }

        handleItemRewards(targetPlayer, item);
        item.setAmount(item.getAmount() - 1);

        if (pay.hasAny()) {
            sender.sendMessage(sellRewardMessage("sell_help_hand_success_op", pay,
                    "§aHelped player %s sell hand item! Received %s coins~",
                    "§aHelped player %s sell hand item! Received %s points~",
                    "§aHelped player %s sell hand item! Received %s coins and %s points~",
                    targetPlayer.getName()));
            targetPlayer.sendMessage(sellRewardMessage("sell_help_hand_success_player", pay,
                    "§aConsole helped you sell hand item! Received %s coins~",
                    "§aConsole helped you sell hand item! Received %s points~",
                    "§aConsole helped you sell hand item! Received %s coins and %s points~"));
        } else {
            sender.sendMessage(messageManager.getMessage("sell_help_hand_success_op_items", "§aHelped player %s sell hand item! Received item rewards~", targetPlayer.getName()));
            targetPlayer.sendMessage(messageManager.getMessage("sell_help_hand_success_player_items", "§aConsole helped you sell hand item! Received item rewards~"));
        }

        if (fishUUIDStr != null) {
            plugin.getDB().removeFishUUIDValue(fishUUIDStr);
        }

        return pay;
    }

    // ==================== 鱼价值读取 ====================

    /**
     * 从物品读取鱼价值（带 UUID 缓存）。
     */
    public int getFishValueFromItem(ItemStack item) {
        return getFishSellValueFromItem(item).getDisplayValue();
    }

    public SellValue getFishSellValueFromItem(ItemStack item) {
        String uuidStr = getFishUUIDString(item);
        if (uuidStr != null) {
            SellValue cachedValue = fishValueCache.get(uuidStr);
            if (cachedValue != null) {
                return cachedValue;
            }
            try {
                SellValue value = plugin.getDB().getFishSellValueByUUID(uuidStr);

                if (plugin.getCustomConfig().isDebugMode()) {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_get_fish_value_db", "[Debug] Fetched fish value from database: %s", value));
                }

                if (value.hasAnyValue()) {
                    fishValueCache.put(uuidStr, value);
                    return value;
                }
            } catch (IllegalArgumentException e) {
                if (plugin.getCustomConfig().isDebugMode()) {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_uuid_format_error", "[Debug] UUID format error: %s", e.getMessage()));
                }
            }
        }

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_not_found", "[Debug] Fish value not found, returning 0"));
        }

        return SellValue.oldValue(0);
    }

    /**
     * 清空鱼价值缓存（reload 时调用）。
     */
    public void clearValueCache() {
        fishValueCache.clear();
    }

    // ==================== 鱼UUID 工具 ====================

    private NamespacedKey getFishUUIDKey() {
        return new NamespacedKey(plugin, "fish_uuid");
    }

    public String getFishUUIDString(ItemStack item) {
        Object uuidObj = me.kkfish.utils.NBTUtil.getNBTData(item, "fish_uuid");
        if (uuidObj != null) {
            if (plugin.getCustomConfig().isDebugMode()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_uuid_found_nbtutil", "[Debug] Got UUID from NBTUtil: %s", uuidObj.toString()));
            }
            return uuidObj.toString();
        }

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_uuid_not_found", "[Debug] UUID not found, returning null"));
        }

        return null;
    }

    public void setFishUUIDString(ItemStack item, ItemMeta meta, String uuidStr) {
        NamespacedKey key = getFishUUIDKey();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, uuidStr);
        item.setItemMeta(meta);
    }

    public UUID getFishUUID(ItemStack item) {
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

    // ==================== 物品奖励 ====================

    public boolean hasItemRewards(ItemStack item) {
        String fishName = getItemNameFromItem(item);
        boolean hasRewards = fishName != null && plugin.getCustomConfig().getItemValue() != null && plugin.getCustomConfig().getItemValue().hasItemRewards(fishName);

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_check_item_rewards", "[Debug] Checking if item has item rewards: %s", hasRewards));
            if (fishName != null) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_name", "[Debug] Fish name: %s", fishName));
            } else {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_cannot_get_fish_name", "[Debug] Unable to get fish name"));
            }
        }

        return hasRewards;
    }

    public void handleItemRewards(Player player, ItemStack item) {
        String fishName = getItemNameFromItem(item);
        if (fishName != null && plugin.getCustomConfig().getItemValue() != null) {
            List<ItemStack> itemRewards = plugin.getCustomConfig().getItemValue().getItemRewards(fishName);
            for (ItemStack reward : itemRewards) {
                player.getInventory().addItem(reward);
            }
        }
    }

    public String getItemNameFromItem(ItemStack item) {
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

    public SellBatch collectSellItems(Player player) {
        return collectSellItems(player.getInventory(), null);
    }

    public SellBatch collectSellItems(Inventory inventory, Set<Integer> ignoredSlots) {
        SellBatch batch = new SellBatch();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (ignoredSlots != null && ignoredSlots.contains(i)) {
                continue;
            }

            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            SellValue sellValue = getFishSellValueFromItem(item);
            EconomyService.SellPay itemPay = getPayForSellValue(sellValue).multiply(item.getAmount());
            boolean itemHasRewards = hasItemRewards(item);

            if ((sellValue.hasAnyValue() && itemPay.hasAny()) || itemHasRewards) {
                String fishUUIDStr = getFishUUIDString(item);
                batch.entries.add(new SellEntry(i, item, fishUUIDStr, itemHasRewards));
                batch.soldCount += item.getAmount();
                batch.pay = batch.pay.add(itemPay);
                if (itemHasRewards) {
                    batch.itemRewards = true;
                }
            }
        }
        return batch;
    }

    public void applySellBatch(Player player, SellBatch batch) {
        applySellBatch(player, player.getInventory(), batch);
    }

    public void applySellBatch(Player player, Inventory inventory, SellBatch batch) {
        if (batch == null || !batch.hasAny()) return;
        for (SellEntry entry : batch.entries) {
            if (entry.itemRewards) {
                handleItemRewards(player, entry.item);
            }
            inventory.setItem(entry.slot, null);
            if (entry.uuidStr != null) {
                plugin.getDB().removeFishUUIDValue(entry.uuidStr);
            }
        }
    }

    // ==================== 金币发放 ====================

    public boolean canGiveSellReward() {
        EconomyService economyService = plugin.getEconomyService();
        return economyService != null && economyService.isEconomyEnabled();
    }

    public EconomyService.SellPay getPayForSellValue(SellValue value) {
        EconomyService economyService = plugin.getEconomyService();
        if (economyService == null) return new EconomyService.SellPay(0, 0);
        return economyService.resolveSellPay(value);
    }

    public boolean isPointRewardActive() {
        EconomyService economyService = plugin.getEconomyService();
        return economyService != null && economyService.getRewardType() == EconomyService.RewardType.PLAYER_POINTS;
    }

    public String sellRewardKey(String baseKey) {
        return isPointRewardActive() ? baseKey + "_points" : baseKey;
    }

    public String sellRewardKey(String baseKey, EconomyService.SellPay pay) {
        if (pay != null && pay.hasBoth()) return baseKey + "_both";
        if (pay != null && pay.getPointsAmount() > 0 && pay.getVaultAmount() <= 0) return baseKey + "_points";
        return baseKey;
    }

    private String sellRewardDefault(String vaultText, String pointsText) {
        return isPointRewardActive() ? pointsText : vaultText;
    }

    private String sellRewardDefault(String vaultText, String pointsText, String bothText, EconomyService.SellPay pay) {
        if (pay != null && pay.hasBoth()) return bothText;
        if (pay != null && pay.getPointsAmount() > 0 && pay.getVaultAmount() <= 0) return pointsText;
        return vaultText;
    }

    public String sellRewardMessage(String baseKey, EconomyService.SellPay pay,
                                    String vaultText, String pointsText, String bothText, Object... prefixArgs) {
        return messageManager.getMessage(sellRewardKey(baseKey, pay),
                sellRewardDefault(vaultText, pointsText, bothText, pay), buildRewardArgs(pay, prefixArgs));
    }

    private Object[] buildRewardArgs(EconomyService.SellPay pay, Object... prefixArgs) {
        int payArgCount = pay != null && pay.hasBoth() ? 2 : 1;
        Object[] args = new Object[prefixArgs.length + payArgCount];
        for (int i = 0; i < prefixArgs.length; i++) {
            args[i] = prefixArgs[i];
        }

        if (pay != null && pay.hasBoth()) {
            args[prefixArgs.length] = pay.getVaultAmount();
            args[prefixArgs.length + 1] = pay.getPointsAmount();
        } else if (pay != null && pay.getPointsAmount() > 0) {
            args[prefixArgs.length] = pay.getPointsAmount();
        } else {
            args[prefixArgs.length] = pay != null ? pay.getVaultAmount() : 0;
        }
        return args;
    }

    public boolean addSellPayToPlayer(Player player, EconomyService.SellPay pay) {
        EconomyService economyService = plugin.getEconomyService();
        if (economyService == null || !economyService.depositSellPay(player, pay)) {
            player.sendMessage(messageManager.getMessage("economy_disabled", "§cEconomy system is not enabled, unable to receive rewards~"));
            return false;
        }

        return true;
    }

    public boolean addMoneyToPlayer(Player player, int amount) {
        if (amount > 0) {
            return addSellPayToPlayer(player, getPayForSellValue(SellValue.oldValue(amount)));
        }
        EconomyService economyService = plugin.getEconomyService();
        if (economyService == null || !economyService.deposit(player, amount)) {
            player.sendMessage(messageManager.getMessage("economy_disabled", "§cEconomy system is not enabled, unable to receive coins~"));
            return false;
        }

        return true;
    }
}
