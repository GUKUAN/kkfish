package me.kkfish.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.kkfish.kkfish;
import me.kkfish.integrations.CustomItemHook;
import me.kkfish.misc.MessageManager;
import me.kkfish.utils.XSeriesUtil;

/**
 * 给予命令处理器：负责 /kkfish give 的物品给予逻辑，以及鱼竿/鱼饵物品的构造。
 * 从 Cmd 抽取，职责单一。
 */
public class GiveCommandHandler {

    private final kkfish plugin;
    private final MessageManager messageManager;

    public GiveCommandHandler(kkfish plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    /**
     * 给予目标玩家指定物品。
     *
     * @param sender   执行者
     * @param targetName 目标玩家名
     * @param itemSpec 物品规格（fish:名 / rod:名 / baits:名）
     * @param amount   数量
     */
    public void giveItem(CommandSender sender, String targetName, String itemSpec, int amount) {
        if (!sender.hasPermission("kkfish.admin")) {
            sender.sendMessage(messageManager.getMessage("no_permission", "§dYou do not have permission to execute this command"));
            return;
        }

        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(messageManager.getMessage("player_not_found", "§dPlayer not found: %s", targetName));
            return;
        }

        String[] parts = itemSpec.split(":");
        if (parts.length != 2) {
            sender.sendMessage(messageManager.getMessage("item_format_error", "§dItem format error, use: fish:<name> or rod:<name> or baits:<name>"));
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
                sender.sendMessage(messageManager.getMessage("unknown_item_type", "§dUnknown item type, use: fish, rod or baits"));
                return;
            }

            if (item != null) {
                item.setAmount(amount);

                target.getInventory().addItem(item);
                sender.sendMessage(messageManager.getMessage("give_success", "§dGave player %s item: %s x%d", targetName, itemName, amount));
                target.sendMessage(messageManager.getMessage("receive_item", "§dReceived item: %s x%d", itemName, amount));

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

                        target.sendMessage(plugin.getMessageManager().getMessage("bait_auto_equipped", "§aAuto-equipped a bait to offhand!"));
                    }
                }
            } else {
                sender.sendMessage(messageManager.getMessage("item_not_found", "§dItem not found: %s", itemName));
            }
        } catch (Exception e) {
            sender.sendMessage(messageManager.getMessage("give_error", "§dError giving item"));
            e.printStackTrace();
        }
    }

    /**
     * 根据配置构造鱼竿物品。
     */
    public ItemStack createRodItem(String rodName) {
        Config configManager = plugin.getCustomConfig();
        if (!configManager.rodExists(rodName)) {
            return null;
        }

        String materialStr = configManager.getRodConfig().getString("rods." + rodName + ".material", "FISHING_ROD");
        // 优先尝试 IA 自定义物品
        ItemStack rod;
        if (CustomItemHook.isCustomItemStr(materialStr)) {
            rod = CustomItemHook.createItemStack(materialStr, 1);
        } else {
            Material material = XSeriesUtil.parseMaterial(materialStr);
            if (material == null) {
                material = XSeriesUtil.parseMaterial("FISHING_ROD");
            }
            rod = new ItemStack(material, 1);
        }
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
        displayName = CustomItemHook.replaceFontImages(displayName);
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
            String unit = messageManager.getMessageWithoutPrefix("rod_durability_unit", "points");
            String full = messageManager.getMessageWithoutPrefix("rod_durability_full", "(full)");
            variables.put("%durability%", durability + unit + " " + full);
        } else {
            variables.put("%durability%", messageManager.getMessageWithoutPrefix("rod_durability_infinite", "Unlimited durability"));
        }

        double chargeSpeed = configManager.getRodChargeSpeed(rodName);
        String speedText;
        if (chargeSpeed != 1.0) {
            String speedType = chargeSpeed > 1.0 ?
                    messageManager.getMessageWithoutPrefix("rod_charge_speed_fast", "faster") :
                    messageManager.getMessageWithoutPrefix("rod_charge_speed_slow", "slower");
            speedText = String.format("%.1f倍 (" + speedType + ")", chargeSpeed);
        } else {
            speedText = messageManager.getMessageWithoutPrefix("rod_charge_speed_normal", "normal");
        }
        variables.put("%charge_speed%", speedText);

        double biteRateBonus = configManager.getRodBiteRateBonus(rodName);
        variables.put("%bite_rate_bonus%", biteRateBonus > 0 ?
                String.format("+%.1f%%", biteRateBonus * 100) :
                messageManager.getMessageWithoutPrefix("rod_bite_rate_bonus_none", "none"));

        List<String> effects = configManager.getRodEffects(rodName);
        StringBuilder effectsBuilder = new StringBuilder();
        if (!effects.isEmpty()) {
            for (String effect : effects) {
                effectsBuilder.append("&7  └─ &r").append(ChatColor.translateAlternateColorCodes('&', effect)).append("\n");
            }
        } else {
            effectsBuilder.append("&7  └─ " + messageManager.getMessageWithoutPrefix("rod_effects_none", "No special effects") + "\n");
        }
        variables.put("%effects%", effectsBuilder.toString());

        String formattedTemplate = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            formattedTemplate = formattedTemplate.replace(entry.getKey(), entry.getValue());
        }

        String[] lines = formattedTemplate.split("\\n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                lore.add(ChatColor.translateAlternateColorCodes('&', CustomItemHook.replaceFontImages(line)));
            } else {
                lore.add("");
            }
        }

        meta.setLore(lore);

        rod.setItemMeta(meta);
        return rod;
    }

    /**
     * 根据配置构造鱼饵物品。
     */
    public ItemStack createBaitItem(String baitName) {
        Config configManager = plugin.getCustomConfig();
        if (!configManager.baitExists(baitName)) {
            return null;
        }

        String materialStr = configManager.getBaitConfig().getString("baits." + baitName + ".material", "MAGMA_CREAM");
        // 优先尝试 IA 自定义物品
        ItemStack bait;
        if (CustomItemHook.isCustomItemStr(materialStr)) {
            bait = CustomItemHook.createItemStack(materialStr, 64);
        } else {
            Material material = XSeriesUtil.parseMaterial(materialStr);
            if (material == null) {
                material = XSeriesUtil.parseMaterial("MAGMA_CREAM");
            }
            bait = new ItemStack(material, 64);
        }
        ItemMeta meta = bait.getItemMeta();
        if (meta == null) {
            return bait;
        }

        boolean hasCustomNBT = configManager.getBaitConfig().getBoolean("baits." + baitName + ".has-custom-nbt", false);
        if (hasCustomNBT && configManager.isCustomNBTSupportEnabled()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.command_create_item", "Creating fish item with custom NBT: %s").replace("%s", baitName));
        }

        String displayName = ChatColor.translateAlternateColorCodes('&',
                configManager.getBaitConfig().getString("baits." + baitName + ".display-name", "&f" + baitName));
        displayName = CustomItemHook.replaceFontImages(displayName);
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
                                messageManager.getMessageWithoutPrefix("bait_effect_rare", "Rare fish chance +") +
                                String.format("%.1f%%", value * 100));
                        } else if (effectType.equals("size")) {
                            effectDesc = ChatColor.translateAlternateColorCodes('&',
                                messageManager.getMessageWithoutPrefix("bait_effect_size", "Fish size +") +
                                String.format("%.1f%%", value * 100));
                        } else if (effectType.equals("bite")) {
                            effectDesc = ChatColor.translateAlternateColorCodes('&',
                                messageManager.getMessageWithoutPrefix("bait_effect_bite", "Bite chance +") +
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
                            messageManager.getMessageWithoutPrefix("bait_effect_rare", "Rare fish chance +") +
                            String.format("%.1f%%", value * 100)));
                    } else if (effect.equals("size")) {
                        effectsBuilder.append(ChatColor.translateAlternateColorCodes('&',
                            messageManager.getMessageWithoutPrefix("bait_effect_size", "Fish size +") +
                            String.format("%.1f%%", value * 100)));
                    } else if (effect.equals("bite")) {
                        effectsBuilder.append(ChatColor.translateAlternateColorCodes('&',
                            messageManager.getMessageWithoutPrefix("bait_effect_bite", "Bite chance +") +
                            String.format("%.1f%%", value * 100)));
                    }
                }
            }
        } else {
            effectsBuilder.append(ChatColor.translateAlternateColorCodes('&', messageManager.getMessageWithoutPrefix("bait_no_effects", "No special effects")));
        }

        String description = configManager.getBaitConfig().getString("baits." + baitName + ".description", "");
        if (description.isEmpty()) {
            description = ChatColor.translateAlternateColorCodes('&', messageManager.getMessageWithoutPrefix("bait_default_description", "A special bait"));
        } else {
            description = ChatColor.translateAlternateColorCodes('&', description);
        }

        String loreContent = template
                .replace("%name%", displayName)
                .replace("%description%", description)
                .replace("%effects%", effectsBuilder.toString());

        List<String> lore = new ArrayList<>();

        for (String line : loreContent.split("\\n")) {
            lore.add(CustomItemHook.replaceFontImages(line));
        }

        String permission = "kkfish.baits.use." + baitName;
        lore.add("");
        lore.add(ChatColor.translateAlternateColorCodes('&', messageManager.getMessageWithoutPrefix("bait_permission_text", "Permission: ") + ChatColor.WHITE + permission));

        lore.add(ChatColor.translateAlternateColorCodes('&', messageManager.getMessageWithoutPrefix("bait_usage_text", "Hold in offhand, consumed when casting")));

        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        bait.setItemMeta(meta);
        return bait;
    }
}
