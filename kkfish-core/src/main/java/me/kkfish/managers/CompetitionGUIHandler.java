package me.kkfish.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.kkfish.competition.CompetitionConfig;
import me.kkfish.gui.GUIMenuLoader;
import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;
import me.kkfish.utils.XSeriesUtil;

/**
 * 比赛 GUI 处理器：负责比赛分类物品、奖励预览物品的生成及比赛配置查询。
 *
 * <p>材质解析通过 {@link GUICommons#parseMaterial(String)} 共享调用。
 */
public class CompetitionGUIHandler {
    private final kkfish plugin;
    private final Config config;
    private final MessageManager messageManager;

    public CompetitionGUIHandler(kkfish plugin, Config config, MessageManager messageManager) {
        this.plugin = plugin;
        this.config = config;
        this.messageManager = messageManager;
    }

    /**
     * 处理比赛分类物品
     */
    public void handleCompetitionItems(Inventory gui, GUIMenuLoader.MenuConfig.MenuItem item, Player player, int page) {
        Compete compete = plugin.getCompete();
        Collection<CompetitionConfig> competitionConfigs = compete.getCompetitionConfigs();

        List<Integer> slots = item.getSlots();
        int competitionIndex = 0;

        for (int slot : slots) {
            if (competitionIndex < competitionConfigs.size() && slot >= 0 && slot < gui.getSize()) {
                CompetitionConfig config = (CompetitionConfig) competitionConfigs.toArray()[competitionIndex];

                ItemStack competitionItem = createCompetitionItemFromConfig(item, player, config);
                if (competitionItem != null) {
                    gui.setItem(slot, competitionItem);
                }

                competitionIndex++;
            }
        }
    }

    /**
     * 处理奖励物品
     */
    public void handleRewardItems(Inventory gui, GUIMenuLoader.MenuConfig.MenuItem item, Player player, int page) {
        Compete compete = plugin.getCompete();
        Collection<CompetitionConfig> competitionConfigs = compete.getCompetitionConfigs();

        CompetitionConfig competitionConfig = getCompetitionByIndex(competitionConfigs, page);
        if (competitionConfig == null) {
            return;
        }

        Map<Integer, List<String>> rewards = competitionConfig.getRewards();
        Map<Integer, List<String>> rewardDisplayInfo = competitionConfig.getRewardDisplayInfo();

        List<Integer> slots = item.getSlots();
        int rewardIndex = 0;

        for (Map.Entry<Integer, List<String>> entry : rewards.entrySet()) {
            if (rewardIndex < slots.size()) {
                int slot = slots.get(rewardIndex);
                if (slot >= 0 && slot < gui.getSize()) {
                    int rank = entry.getKey();
                    List<String> commands = entry.getValue();
                    List<String> displayInfo = rewardDisplayInfo.get(rank);

                    ItemStack rewardItem = createRewardItemFromConfig(item, rank, commands, displayInfo);
                    if (rewardItem != null) {
                        gui.setItem(slot, rewardItem);
                    }
                }
                rewardIndex++;
            }
        }
    }

    /**
     * 从配置创建奖励物品
     */
    public ItemStack createRewardItemFromConfig(GUIMenuLoader.MenuConfig.MenuItem itemConfig, int rank, List<String> commands, List<String> displayInfo) {
        // 解析奖励信息
        String rewardType = "Unknown";
        String rewardAmount = "Unknown";
        String rewardCondition = messageManager.getMessageWithoutPrefix("gui_reward_condition", "第" + rank + "名");
        Material material = XSeriesUtil.getMaterial("CHEST");

        // 优先使用配置的显示信息
        if (displayInfo != null && !displayInfo.isEmpty()) {
            rewardType = displayInfo.get(0);
            if (displayInfo.size() > 1) {
                rewardAmount = displayInfo.get(1);
            }
        } else {
            // 如果没有配置显示信息，才通过解析命令来生成显示信息
            for (String command : commands) {
                // 去掉命令开头的斜杠（如果有）
                String normalizedCommand = command.startsWith("/") ? command.substring(1) : command;

                if (normalizedCommand.startsWith("eco give")) {
                    // 经济奖励
                    rewardType = messageManager.getMessageWithoutPrefix("gui_reward_type_eco", "Eco Reward");
                    String[] parts = normalizedCommand.split(" ");
                    if (parts.length >= 3) {
                        rewardAmount = parts[2] + " " + messageManager.getMessageWithoutPrefix("gui_currency", "Coins");
                    }
                    material = XSeriesUtil.getMaterial("GOLD_INGOT");
                } else if (normalizedCommand.startsWith("give")) {
                    // 物品奖励
                    rewardType = messageManager.getMessageWithoutPrefix("gui_reward_type_item", "Item Reward");
                    String[] parts = normalizedCommand.split(" ");
                    if (parts.length >= 2) {
                        // 格式: give player item amount
                        if (parts.length >= 4) {
                            rewardAmount = parts[2] + " × " + parts[3];
                            material = GUICommons.parseMaterial(parts[2]);
                        } else if (parts.length >= 3) {
                            rewardAmount = parts[2] + " × 1";
                            material = GUICommons.parseMaterial(parts[2]);
                        }
                    }
                    if (material == null) {
                        material = XSeriesUtil.getMaterial("ITEM_FRAME");
                    }
                }
            }
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // 设置显示名称
        String displayName = itemConfig.getDisplayName();
        // 检查是否是国际化键值（以i18n:开头）
        if (displayName.startsWith("i18n:")) {
            String key = displayName.substring(5);
            displayName = messageManager.getMessageWithoutPrefix(key, displayName);
        }
        displayName = displayName.replace("%reward_name%", messageManager.getMessageWithoutPrefix("gui_reward_name", "第" + rank + "名奖励"));
        displayName = ChatColor.translateAlternateColorCodes('&', displayName);
        meta.setDisplayName(displayName);

        // 设置lore
        List<String> lore = new ArrayList<>();
        for (String line : itemConfig.getLore()) {
            // 检查是否是国际化键值（以i18n:开头）
            if (line.startsWith("i18n:")) {
                String key = line.substring(5);
                line = messageManager.getMessageWithoutPrefix(key, line);
            }
            String replacedLine = line;

            // 处理%reward_display%占位符
            if (replacedLine.contains("%reward_display%")) {
                if (displayInfo != null && !displayInfo.isEmpty()) {
                    // 显示配置的奖励简介
                    for (String infoLine : displayInfo) {
                        String displayLine = "&7| &f" + infoLine;
                        displayLine = ChatColor.translateAlternateColorCodes('&', displayLine);
                        lore.add(displayLine);
                    }
                    continue; // 跳过默认处理
                }
            }

            // 处理其他占位符
            replacedLine = replacedLine.replace("%reward_type%", rewardType);
            replacedLine = replacedLine.replace("%reward_amount%", rewardAmount);
            replacedLine = replacedLine.replace("%reward_condition%", rewardCondition);
            replacedLine = ChatColor.translateAlternateColorCodes('&', replacedLine);
            lore.add(replacedLine);
        }

        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

        // 设置Custom Model Data
        if (itemConfig.hasCustomModelData()) {
            meta.setCustomModelData(itemConfig.getCustomModelData());
        }

        item.setItemMeta(meta);

        return item;
    }

    /**
     * 从配置创建比赛物品
     */
    public ItemStack createCompetitionItemFromConfig(GUIMenuLoader.MenuConfig.MenuItem itemConfig, Player player, CompetitionConfig competitionConfig) {
        try {
            // 解析材质
            String materialStr = itemConfig.getMaterial().replace("%competition_item%", getCompetitionMaterial(competitionConfig.getType()));
            Material material = GUICommons.parseMaterial(materialStr);
            if (material == null) {
                material = XSeriesUtil.getMaterial("FISHING_ROD");
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            // 设置显示名称
            String displayName = itemConfig.getDisplayName();
            // 检查是否是国际化键值（以i18n:开头）
            if (displayName.startsWith("i18n:")) {
                String key = displayName.substring(5);
                displayName = messageManager.getMessageWithoutPrefix(player, key, displayName);
            }
            displayName = displayName.replace("%competition_name%", competitionConfig.getName());
            displayName = displayName.replace("%player_name%", player.getName());
            displayName = displayName.replace("%player%", player.getName());
            displayName = displayName.replace("%p", player.getName());
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

            // 设置 lore
            List<String> lore = new ArrayList<>();
            for (String line : itemConfig.getLore()) {
                // 检查是否是国际化键值（以i18n:开头）
                if (line.startsWith("i18n:")) {
                    String key = line.substring(5);
                    line = messageManager.getMessageWithoutPrefix(player, key, line);
                }
                line = line.replace("%competition_name%", competitionConfig.getName());
                line = line.replace("%competition_type%", getCompetitionTypeDisplayName(competitionConfig.getType()));
                line = line.replace("%competition_time%", competitionConfig.getSchedule());
                line = line.replace("%competition_fee%", "0");
                line = line.replace("%competition_reward%", getCompetitionRewardString(competitionConfig));
                line = line.replace("%competition_id%", competitionConfig.getId());
                line = line.replace("%player_name%", player.getName());
                line = line.replace("%player%", player.getName());
                line = line.replace("%p", player.getName());
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);

            // 设置 Custom Model Data
            if (itemConfig.hasCustomModelData()) {
                meta.setCustomModelData(itemConfig.getCustomModelData());
            }

            // 设置不可破坏
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            kkfish.log("§e" + "创建比赛物品失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取比赛材质
     */
    public String getCompetitionMaterial(String type) {
        switch (type) {
            case "AMOUNT":
                return "cooked_cod";
            case "TOTAL_VALUE":
                return "gold_ingot";
            case "SINGLE_VALUE":
                return "nether_star";
            default:
                return "fishing_rod";
        }
    }

    /**
     * 获取比赛奖励字符串
     */
    public String getCompetitionRewardString(CompetitionConfig config) {
        Map<Integer, List<String>> rewards = config.getRewards();
        Map<Integer, List<String>> rewardDisplayInfo = config.getRewardDisplayInfo();
        StringBuilder rewardStr = new StringBuilder();

        // 只显示第一名的奖励
        if (rewardDisplayInfo.containsKey(1)) {
            List<String> displayInfo = rewardDisplayInfo.get(1);
            if (!displayInfo.isEmpty()) {
                // 使用配置的奖励显示信息
                for (String infoLine : displayInfo) {
                    rewardStr.append(infoLine);
                    break; // 只取第一行
                }
            }
        } else if (rewards.containsKey(1)) {
            // 如果没有配置显示信息，使用命令解析
            List<String> commands = rewards.get(1);
            rewardStr.append("第1名:");
            for (String command : commands) {
                if (command.startsWith("/eco give")) {
                    String[] parts = command.split(" ");
                    if (parts.length >= 4) {
                        rewardStr.append(" " + parts[3]).append("金币");
                    }
                } else if (command.startsWith("/give")) {
                    String[] parts = command.split(" ");
                    if (parts.length >= 3) {
                        rewardStr.append(" " + parts[2]);
                    }
                }
            }
        }

        return rewardStr.toString();
    }

    /**
     * 获取比赛类型的显示名称
     */
    public String getCompetitionTypeDisplayName(String type) {
        switch (type) {
            case "AMOUNT":
                return messageManager.getMessageWithoutPrefix("gui_competition_type_amount", "AMOUNT");
            case "TOTAL_VALUE":
                return messageManager.getMessageWithoutPrefix("gui_competition_type_total_value", "TOTAL_VALUE");
            case "SINGLE_VALUE":
                return messageManager.getMessageWithoutPrefix("gui_competition_type_single_value", "SINGLE_VALUE");
            case "POINTS_ONLY":
                return messageManager.getMessageWithoutPrefix("gui_competition_type_points_only", "POINTS_ONLY");
            default:
                // 直接返回类型名称
                return type;
        }
    }

    /**
     * 根据索引获取比赛配置
     */
    public CompetitionConfig getCompetitionByIndex(Collection<CompetitionConfig> competitionConfigs, int index) {
        // 如果索引为-1，返回第一个比赛配置
        if (index == -1 && !competitionConfigs.isEmpty()) {
            return competitionConfigs.iterator().next();
        }

        int currentIndex = 0;
        for (CompetitionConfig config : competitionConfigs) {
            if (currentIndex == index) {
                return config;
            }
            currentIndex++;
        }
        return null;
    }

    /**
     * 获取比赛配置的索引
     */
    public int getCompetitionIndex(String competitionId) {
        Compete compete = plugin.getCompete();
        Collection<CompetitionConfig> competitionConfigs = compete.getCompetitionConfigs();

        int index = 0;
        for (CompetitionConfig config : competitionConfigs) {
            if (config.getId().equals(competitionId)) {
                return index;
            }
            index++;
        }
        return -1;
    }
}
