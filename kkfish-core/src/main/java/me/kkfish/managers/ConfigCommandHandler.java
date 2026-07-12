package me.kkfish.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;

/**
 * 配置命令处理器：负责 /kkfish reload、/kkfish debug、/kkfish add 的逻辑。
 * 从 Cmd 抽取，职责单一。
 */
public class ConfigCommandHandler {

    private final kkfish plugin;
    private final MessageManager messageManager;
    private final SellCommandHandler sellHandler;

    public ConfigCommandHandler(kkfish plugin, SellCommandHandler sellHandler) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
        this.sellHandler = sellHandler;
    }

    /**
     * 重载所有配置。
     */
    public void reloadConfig(CommandSender sender) {
        if (!sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§dYou do not have permission to execute this command"));
            return;
        }
        plugin.getCustomConfig().reloadConfigs();
        sellHandler.clearValueCache();
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

        sender.sendMessage(messageManager.getMessage("config_reloaded", "§aConfig reloaded successfully!"));
    }

    /**
     * 切换调试模式。
     */
    public void toggleDebug(CommandSender sender) {
        if (!sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§dYou do not have permission to execute this command"));
            return;
        }
        Config configManager = plugin.getCustomConfig();
        boolean newState = !configManager.isDebugMode();
        configManager.setDebugMode(newState);
        sender.sendMessage(messageManager.getMessage("debug_toggled", "§dDebug mode %s", newState ? "enabled" : "disabled"));
    }

    /**
     * 处理 add 命令：将手持物品添加到配置文件。
     */
    public void handleAddCommand(Player player, String addType, String[] args) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(messageManager.getMessage("empty_hand", "§cPlease hold the item you want to add"));
            return;
        }

        String itemName = "";
        String displayName = "";

        if (args.length > 2) {
            displayName = args[2];
            itemName = displayName;
            if (itemName.startsWith("-")) {
                itemName = "_" + itemName.substring(1);
            }
            if (itemName.isEmpty()) {
                itemName = "custom_" + System.currentTimeMillis();
            }
        } else {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                displayName = ChatColor.stripColor(meta.getDisplayName());
                itemName = displayName;
                if (itemName.startsWith("-")) {
                    itemName = "_" + itemName.substring(1);
                }
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

            switch (addType) {
                case "fish":
                    addFishToConfig(configManager, item, itemName);
                    player.sendMessage(messageManager.getMessage("add_fish_success", "§aSuccessfully added fish '" + itemName + "' to config file"));
                    break;
                case "rods":
                    addRodToConfig(configManager, item, itemName, displayName);
                    player.sendMessage(messageManager.getMessage("add_rod_success", "§aSuccessfully added rod '" + displayName + "' to config file"));
                    break;
                case "baits":
                    addBaitToConfig(configManager, item, itemName);
                    player.sendMessage(messageManager.getMessage("add_bait_success", "§aSuccessfully added bait '" + itemName + "' to config file"));
                    break;
                default:
                    player.sendMessage(messageManager.getMessage("add_invalid_type", "§cUnknown add type, use: fish, rods or baits"));
                    break;
            }

            configManager.saveConfigs();
            configManager.reloadConfigs();

            player.sendMessage(messageManager.getMessage("add_config_edit_hint", "§ePlease edit the config file to customize item properties"));
        } catch (Exception e) {
            player.sendMessage(messageManager.getMessage("add_error", "§cError adding item: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void addFishToConfig(Config configManager, ItemStack item, String fishName) {
        FileConfiguration fishConfig = configManager.getFishConfig();

        if (fishConfig.contains("fish." + fishName)) {
            int count = 1;
            while (fishConfig.contains("fish." + fishName + "_" + count)) {
                count++;
            }
            fishName = fishName + "_" + count;
        }

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

        try {
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().isEmpty() == false) {
                fishConfig.set("fish." + fishName + ".has-custom-nbt", true);
            }
        } catch (Exception e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.command_save_nbt_failed", "Error saving NBT data: ") + e.getMessage());
        }

        List<Map<String, Object>> levels = new ArrayList<>();
        Map<String, Object> commonLevel = new LinkedHashMap<>();
        commonLevel.put("common", 80);
        Map<String, Object> rareLevel = new LinkedHashMap<>();
        rareLevel.put("rare", 15);
        Map<String, Object> epicLevel = new LinkedHashMap<>();
        epicLevel.put("epic", 4);
        Map<String, Object> legendaryLevel = new LinkedHashMap<>();
        legendaryLevel.put("legendary", 1);
        levels.add(commonLevel);
        levels.add(rareLevel);
        levels.add(epicLevel);
        levels.add(legendaryLevel);
        fishConfig.set("fish." + fishName + ".level", levels);
    }

    private void addRodToConfig(Config configManager, ItemStack item, String rodName, String displayName) {
        FileConfiguration rodConfig = configManager.getRodConfig();

        if (rodConfig.contains("rods." + rodName)) {
            int count = 1;
            while (rodConfig.contains("rods." + rodName + "_" + count)) {
                count++;
            }
            rodName = rodName + "_" + count;
        }

        rodConfig.set("rods." + rodName + ".display-name", displayName);
        rodConfig.set("rods." + rodName + ".material", item.getType().name());
        rodConfig.set("rods." + rodName + ".difficulty", 1.0);
        rodConfig.set("rods." + rodName + ".float-area-size", 20);
        rodConfig.set("rods." + rodName + ".durability", 100);
        rodConfig.set("rods." + rodName + ".charge-speed", 1.0);
        rodConfig.set("rods." + rodName + ".bite-rate-bonus", 0.0);
        rodConfig.set("rods." + rodName + ".rare-fish-chance", 0.0);
        rodConfig.set("rods." + rodName + ".custom-model-data", item.getItemMeta().hasCustomModelData() ? item.getItemMeta().getCustomModelData() : 0);

        try {
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                PersistentDataContainer container = meta.getPersistentDataContainer();

                if (container.isEmpty() == false) {
                    rodConfig.set("rods." + rodName + ".has-custom-nbt", true);

                    ConfigurationSection nbtSection = rodConfig.createSection("rods." + rodName + ".nbt");

                    for (NamespacedKey key : container.getKeys()) {
                        try {
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
                                nbtSection.set(key.getKey() + ".type", "byte_array");
                            } else {
                                nbtSection.set(key.getKey() + ".type", "unhandled");
                            }
                        } catch (Exception ex) {
                            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.command_save_nbt_key_failed", "Error saving NBT key %s: ").replace("%s", key.getKey()) + ex.getMessage());
                        }
                    }

                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.command_save_nbt_success", "Saved %s NBT data items to rod configuration").replace("%s", String.valueOf(container.getKeys().size())));
                }
            }
        } catch (Exception e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.command_save_nbt_failed", "Error saving NBT data: ") + e.getMessage());
        }

        List<String> effects = new ArrayList<>();
        rodConfig.set("rods." + rodName + ".effects", effects);
    }

    private void addBaitToConfig(Config configManager, ItemStack item, String baitName) {
        FileConfiguration baitConfig = configManager.getBaitConfig();

        if (baitConfig.contains("baits." + baitName)) {
            int count = 1;
            while (baitConfig.contains("baits." + baitName + "_" + count)) {
                count++;
            }
            baitName = baitName + "_" + count;
        }

        baitConfig.set("baits." + baitName + ".display-name", ChatColor.stripColor(item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : "&a" + baitName));
        baitConfig.set("baits." + baitName + ".material", item.getType().name());

        try {
            if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().isEmpty() == false) {
                baitConfig.set("baits." + baitName + ".has-custom-nbt", true);
            }
        } catch (Exception e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.command_save_nbt_failed", "Error saving NBT data: ") + e.getMessage());
        }

        List<String> effects = new ArrayList<>();
        effects.add("rare");
        baitConfig.set("baits." + baitName + ".effects", effects);

        Map<String, Object> effectValues = new LinkedHashMap<>();
        effectValues.put("rare", 0.05);
        baitConfig.set("baits." + baitName + ".effect-values", effectValues);

        List<String> permissions = new ArrayList<>();
        permissions.add("kkfish.baits.use." + baitName);
        baitConfig.set("baits." + baitName + ".permissions", permissions);
    }
}
