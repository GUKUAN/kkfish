package me.kkfish.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.milkbowl.vault.economy.Economy;

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
    private final Map<String, Double> fishValueCache = new ConcurrentHashMap<>();

    public SellCommandHandler(kkfish plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    // ==================== 出售入口 ====================

    /**
     * 出售玩家自己手中的鱼。
     */
    public void sellHandheldFish(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(messageManager.getMessage("sell_hand_empty", "§c你手中没有物品哦～"));
            return;
        }

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_sell_hand_item", "[Debug] 尝试出售手中物品: %s", item.getType().name()));
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName()) {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_display_name", "[Debug] 物品显示名称: %s", meta.getDisplayName()));
                }
                if (meta.hasLore()) {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_has_lore", "[Debug] 物品有Lore: %s行", meta.getLore().size()));
                }
            }
        }

        String fishUUIDStr = getFishUUIDString(item);

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_uuid_obtained", "[Debug] 获取到的鱼UUID: %s", (fishUUIDStr != null ? fishUUIDStr : "null")));
        }

        int value = getFishValueFromItem(item);
        boolean hasRewards = hasItemRewards(item);

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_reward", "[Debug] 鱼的价值: %s, 是否有物品奖励: %s", value, hasRewards));
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

    /**
     * 管理员代为出售目标玩家手中的鱼。
     */
    public void sellHandheldFishForOther(Player opPlayer, Player targetPlayer) {
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

    /**
     * 出售玩家自己背包中的所有鱼。
     */
    public void sellAllFish(Player player) {
        int totalValue = 0;
        int soldCount = 0;
        List<String> uuidStrsToRemove = new ArrayList<>();
        boolean hasItemRewards = false;

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_start_checking_inventory", "[Debug] 开始检查背包中的鱼..."));
        }

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            if (plugin.getCustomConfig().isDebugMode()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_check_item_slot", "[Debug] 检查物品槽 %s: %s", i, item.getType().name()));
                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta.hasDisplayName()) {
                        kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_display_name", "[Debug] 物品显示名称: %s", meta.getDisplayName()));
                    }
                }
            }

            String fishUUIDStr = getFishUUIDString(item);
            if (fishUUIDStr != null) {
                uuidStrsToRemove.add(fishUUIDStr);
            }

            if (plugin.getCustomConfig().isDebugMode()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_uuid_obtained", "[Debug] 获取到的鱼UUID: %s", (fishUUIDStr != null ? fishUUIDStr : "null")));
            }

            int value = getFishValueFromItem(item);
            boolean itemHasRewards = hasItemRewards(item);

            if (plugin.getCustomConfig().isDebugMode()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_reward", "[Debug] 鱼的价值: %s, 是否有物品奖励: %s", value, itemHasRewards));
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
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_item_sold", "[Debug] 已出售物品，数量: %s", item.getAmount()));
                }
            }
        }

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_sell_complete", "[Debug] 出售完成，总价值: %s, 总数量: %s", totalValue, soldCount));
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

    /**
     * 管理员代为出售目标玩家背包中的所有鱼。
     */
    public void sellAllFishForOther(Player opPlayer, Player targetPlayer) {
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

    /**
     * 控制台代为出售目标玩家背包中的所有鱼，返回总价值。
     */
    public int sellAllFishConsole(Player targetPlayer) {
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

    // ==================== 鱼价值读取 ====================

    /**
     * 从物品读取鱼价值（带 UUID 缓存）。
     */
    public int getFishValueFromItem(ItemStack item) {
        String uuidStr = getFishUUIDString(item);
        if (uuidStr != null) {
            Double cachedValue = fishValueCache.get(uuidStr);
            if (cachedValue != null) {
                return cachedValue.intValue();
            }
            try {
                int value = plugin.getDB().getFishValueByUUID(uuidStr);

                if (plugin.getCustomConfig().isDebugMode()) {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_get_fish_value_db", "[Debug] 从数据库获取鱼价值: %s", value));
                }

                if (value > 0) {
                    fishValueCache.put(uuidStr, (double) value);
                    return value;
                }
            } catch (IllegalArgumentException e) {
                if (plugin.getCustomConfig().isDebugMode()) {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_uuid_format_error", "[Debug] UUID格式错误: %s", e.getMessage()));
                }
            }
        }

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_not_found", "[Debug] 未找到鱼价值，返回0"));
        }

        return 0;
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
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_uuid_found_nbtutil", "[Debug] 从NBTUtil获取到UUID: %s", uuidObj.toString()));
            }
            return uuidObj.toString();
        }

        if (plugin.getCustomConfig().isDebugMode()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_uuid_not_found", "[Debug] 未找到UUID，返回null"));
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
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_check_item_rewards", "[Debug] 检查物品是否有物品奖励: %s", hasRewards));
            if (fishName != null) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_name", "[Debug] 鱼的名称: %s", fishName));
            } else {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_cannot_get_fish_name", "[Debug] 无法获取鱼的名称"));
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

    // ==================== 金币发放 ====================

    public void addMoneyToPlayer(Player player, int amount) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage(messageManager.getMessage("economy_disabled", "§c经济系统未启用，无法获得金币～"));
            return;
        }

        economy.depositPlayer(player, amount);
    }
}
