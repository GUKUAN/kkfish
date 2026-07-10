package me.kkfish.listeners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import me.kkfish.kkfish;
import me.kkfish.integrations.CustomItemHook;
import me.kkfish.managers.Config;
import me.kkfish.misc.MessageManager;

public class ItemCraft implements Listener {

    private final kkfish plugin;
    private final Config config;

    public ItemCraft(kkfish plugin) {
        this.plugin = plugin;
        this.config = plugin.getCustomConfig();
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() != Material.FISHING_ROD) {
            return;
        }

        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey tagsKey = new NamespacedKey(plugin, "Tags");
            if (container.has(tagsKey, PersistentDataType.STRING)) {
                String tagsString = container.get(tagsKey, PersistentDataType.STRING);
                if (tagsString != null && tagsString.contains("自定义鱼竿")) {
                    return;
                }
            }
        }

        String defaultRodName = "default";
        ConfigurationSection rodConfig = config.getRodConfig();
        if (!rodConfig.contains("rods.default")) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("item_craft_no_default_rod", "未找到默认鱼竿配置，请确保在rods.yml中正确配置了rods.default"));
            return;
        }

        ItemStack defaultRod = createDefaultRod();
        if (defaultRod != null) {
            event.setCurrentItem(defaultRod);
            
            Player player = (Player) event.getWhoClicked();
            MessageManager messageManager = plugin.getMessageManager();
            player.sendMessage(messageManager.getMessage("default_rod_crafted", "§a你的钓鱼竿已升级为默认钓鱼竿！"));
        }
    }

    private ItemStack createDefaultRod() {
        String rodName = "default";
        
        String materialStr = config.getRodConfig().getString("rods.default.material", "FISHING_ROD");
        // 优先尝试 IA 自定义物品
        ItemStack rod;
        if (CustomItemHook.isCustomItemStr(materialStr)) {
            rod = CustomItemHook.createItemStack(materialStr, 1);
        } else {
            Material material = Material.matchMaterial(materialStr);
            if (material == null) {
                material = Material.FISHING_ROD;
            }
            rod = new ItemStack(material, 1);
        }
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) {
            return rod;
        }
        
        ConfigurationSection nbtSection = config.getRodConfig().getConfigurationSection("rods.default.nbt");
        try {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            
            NamespacedKey tagsKey = new NamespacedKey(plugin, "Tags");
            List<String> tagsList = new ArrayList<>();
            tagsList.add("自定义鱼竿");
            
            container.set(tagsKey, PersistentDataType.STRING, String.join(",", tagsList));
            
            if (nbtSection != null && !nbtSection.getKeys(false).isEmpty() && config.isCustomNBTSupportEnabled()) {
                for (String nbtKey : nbtSection.getKeys(false)) {
                    if (nbtKey.equalsIgnoreCase("CustomModelData")) {
                        continue;
                    }
                    
                    Object value = nbtSection.get(nbtKey);
                    
                    if (value instanceof String) {
                        container.set(new NamespacedKey(plugin, nbtKey), PersistentDataType.STRING, (String) value);
                    } else if (value instanceof Integer) {
                        container.set(new NamespacedKey(plugin, nbtKey), PersistentDataType.INTEGER, (Integer) value);
                    } else if (value instanceof Double) {
                        container.set(new NamespacedKey(plugin, nbtKey), PersistentDataType.DOUBLE, (Double) value);
                    } else if (value instanceof Boolean) {
                        container.set(new NamespacedKey(plugin, nbtKey), PersistentDataType.INTEGER, ((Boolean) value) ? 1 : 0);
                    }
                }
            }
        } catch (Exception e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("item_craft_apply_nbt_error", "应用默认鱼竿NBT数据时出错: %s", e.getMessage()));
        }
        
        String displayName = ChatColor.translateAlternateColorCodes('&', 
                config.getRodConfig().getString("rods.default.display-name", "&f普通钓鱼竿"));
        displayName = CustomItemHook.replaceFontImages(displayName);
        meta.setDisplayName(displayName);
        
        int customModelData = config.getRodCustomModelData(rodName);
        if (customModelData != -1) {
            meta.setCustomModelData(customModelData);
        }
        
        List<String> lore = new ArrayList<>();
        
        String templateName = config.getRodTemplateName(rodName);
        String template = config.getRodTemplate(templateName);
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
        variables.put("%difficulty%", String.valueOf(config.getRodDifficulty(rodName)));
        variables.put("%float_area%", String.valueOf(config.getRodFloatAreaSize(rodName)));
        
        int durability = config.getRodDurability(rodName);
        MessageManager messageManager = plugin.getMessageManager();
        if (durability > 0) {
            String unit = messageManager.getMessageWithoutPrefix("rod_durability_unit", "点");
            int progressBarLength = 16;
            double progressPercentage = 1.0;
            int filledLength = progressBarLength;
            ChatColor durabilityColor = ChatColor.GREEN;
            
            StringBuilder barBuilder = new StringBuilder();
            barBuilder.append(" ");
            barBuilder.append(ChatColor.GRAY).append("[");
            barBuilder.append(durabilityColor);
            for (int i = 0; i < filledLength; i++) {
                barBuilder.append('|');
            }
            barBuilder.append(ChatColor.GRAY);
            barBuilder.append(" ]");
            
            variables.put("%durability%", durability + unit + barBuilder.toString());
        } else {
            variables.put("%durability%", messageManager.getMessageWithoutPrefix("rod_durability_infinite", "无限耐久"));
        }
        
        double chargeSpeed = config.getRodChargeSpeed(rodName);
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
        
        double biteRateBonus = config.getRodBiteRateBonus(rodName);
        variables.put("%bite_rate_bonus%", biteRateBonus > 0 ? 
                String.format("+%.1f%%", biteRateBonus * 100) : 
                messageManager.getMessageWithoutPrefix("rod_bite_rate_bonus_none", "无"));
        
        List<String> effects = config.getRodEffects(rodName);
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
                lore.add(ChatColor.translateAlternateColorCodes('&', CustomItemHook.replaceFontImages(line)));
            } else {
                lore.add("");
            }
        }
        
        meta.setLore(lore);
        
        rod.setItemMeta(meta);
        
        return rod;
    }
}