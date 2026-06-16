package me.kkfish.managers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.kkfish.kkfish;
import me.kkfish.misc.MessageManager;
import me.kkfish.utils.XSeriesUtil;

/**
 * 负责鱼类物品的创建、价值计算、效果展示名解析。
 * 从 Fish.java 拆分而来。
 */
public class FishItemFactory {

    private final kkfish plugin;
    private final Config config;
    private final MessageManager messageManager;
    private final Random random;

    public FishItemFactory(kkfish plugin, Config config, MessageManager messageManager, Random random) {
        this.plugin = plugin;
        this.config = config;
        this.messageManager = messageManager;
        this.random = random;
    }

    public double calculateFishValue(String fishName, double size, int rarity) {
        FileConfiguration fishConfig = config.getFishConfig();
        double baseValue = fishConfig.getDouble("fish." + fishName + ".value", 10.0);

        double sizeMultiplier = size / 30.0;

        double rarityMultiplier;
        switch (rarity) {
            case 1: rarityMultiplier = 1.0; break;
            case 2: rarityMultiplier = 1.2; break;
            case 3: rarityMultiplier = 1.5; break;
            case 4: rarityMultiplier = 2.0; break;
            case 5: rarityMultiplier = 3.0; break;
            default: rarityMultiplier = 1.0; break;
        }

        return baseValue * sizeMultiplier * rarityMultiplier;
    }

    public ItemStack createFishItem(String fishName) {
        return createFishItem(fishName, false, null);
    }

    public ItemStack createFishItem(String fishName, boolean forceRarity) {
        return createFishItem(fishName, forceRarity, null);
    }

    public ItemStack createFishItem(String fishName, boolean forceRarity, Player player) {
        FileConfiguration fishConfig = config.getFishConfig();

        double minSize = fishConfig.getDouble("fish." + fishName + ".min-size", 20.0);
        double maxSize = fishConfig.getDouble("fish." + fishName + ".max-size", 60.0);
        double randomSize = minSize + Math.random() * (maxSize - minSize);

        String fishLevel = config.getRandomFishLevel(fishName);

        return createFishItem(fishName, forceRarity, player, randomSize, fishLevel);
    }

    public ItemStack createFishItem(String fishName, boolean forceRarity, Player player, double fishSize, String fishLevel) {
        return createFishItem(fishName, forceRarity, player, fishSize, fishLevel, null);
    }

    public ItemStack createFishItem(String fishName, boolean forceRarity, Player player, double fishSize, String fishLevel, Double preCalculatedValue) {
        FileConfiguration fishConfig = config.getFishConfig();

        String displayName = fishConfig.getString("fish." + fishName + ".display-name", fishName);
        String description = fishConfig.getString("fish." + fishName + ".description", plugin.getMessageManager().getMessageWithoutPrefix("fish_default_description", "一条普通的鱼"));
        String materialStr = fishConfig.getString("fish." + fishName + ".material", "COD");
        Material material;
        try {
            material = XSeriesUtil.parseMaterial(materialStr);
        } catch (Exception e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.invalid_fish_material", "无效的鱼材质: %s, 使用默认材质COD", materialStr));
            material = XSeriesUtil.getMaterial("COD");
        }

        ItemStack fishItem = new ItemStack(material);
        ItemMeta meta = fishItem.getItemMeta();

        boolean hasCustomNBT = fishConfig.getBoolean("fish." + fishName + ".has-custom-nbt", false);
        if (hasCustomNBT && config.isCustomNBTSupportEnabled()) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.fishing_create_fish_item", "Creating fish item with custom NBT: %s").replace("%s", fishName));
        }

        if (meta != null) {
            displayName = displayName.replace('&', '§');
            meta.setDisplayName(displayName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "=" + ChatColor.WHITE + plugin.getMessageManager().getMessageWithoutPrefix("fish_info_label", "【 鱼的信息 】") + ChatColor.GRAY + "=");
            if (description.contains("\n")) {
                String[] lines = description.split("\\n");
                for (String line : lines) {
                    lore.add(ChatColor.WHITE + line);
                }
            } else {
                lore.add(ChatColor.WHITE + description);
            }

            double minSize = fishConfig.getDouble("fish." + fishName + ".min-size", 20.0);
            double maxSize = fishConfig.getDouble("fish." + fishName + ".max-size", 60.0);

            int rarity = config.getFishRarity(fishName);

            double sizeBonus = 1.0;
            double valueBonus = 1.0;
            String hookMaterial = "wood";

            if (player != null) {
                hookMaterial = plugin.getDB().getPlayerHookMaterial(player.getUniqueId().toString());

                switch (hookMaterial != null ? hookMaterial.toLowerCase() : "wood") {
                    case "wood":
                        sizeBonus = 1.05;
                        break;
                    case "stone":
                        sizeBonus = 1.1;
                        break;
                    case "iron":
                        sizeBonus = 1.15;
                        valueBonus = 1.1;
                        break;
                    case "gold":
                        sizeBonus = 1.2;
                        valueBonus = 1.2;
                        break;
                    case "diamond":
                        sizeBonus = 1.3;
                        valueBonus = 1.3;
                        break;
                    default:
                        sizeBonus = 1.05;
                        break;
                }

                fishSize = Math.min(fishSize * sizeBonus, maxSize);
            }

            if (player != null && hookMaterial != null && !hookMaterial.equalsIgnoreCase("wood")) {
                String materialColor = ChatColor.GRAY.toString();
                switch (hookMaterial.toLowerCase()) {
                    case "stone":
                        materialColor = ChatColor.DARK_GRAY.toString();
                        break;
                    case "iron":
                        materialColor = ChatColor.WHITE.toString();
                        break;
                    case "gold":
                        materialColor = ChatColor.GOLD.toString();
                        break;
                    case "diamond":
                        materialColor = ChatColor.AQUA.toString();
                        break;
                }
            }

            String rarityName = config.getRarityNameByLevel(fishLevel);
            String rarityDisplayName = config.getRarityDisplayName(rarityName);

            int value;
            if (preCalculatedValue != null) {
                value = (int) Math.round(preCalculatedValue);
            } else {
                double baseValue = fishConfig.getDouble("fish." + fishName + ".value", 10.0);
                double rarityMultiplier = config.getRarityValueMultiplier(rarityName);

                double finalValue = baseValue * fishSize / maxSize * rarityMultiplier * valueBonus;

                if (config.isDebugMode()) {
                    kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_calculation", "[Debug] 计算鱼的价值: 基础价值=%s, 鱼大小=%s, 最大大小=%s, 稀有度倍率=%s, 鱼钩材质加成=%s, 最终价值=%s", baseValue, fishSize, maxSize, rarityMultiplier, valueBonus, finalValue));
                }

                if (plugin.isRealisticSeasonsEnabled() && config.isSeasonalPriceFluctuationEnabled()) {
                    String currentSeason = plugin.getCurrentSeason();
                    if (currentSeason != null) {
                        double seasonalMultiplier = config.getSeasonalPriceMultiplier(currentSeason);
                        finalValue *= seasonalMultiplier;

                        double baseFluctuation = config.getBasePriceFluctuation();
                        double randomFluctuation = 1.0 + (random.nextDouble() - 0.5) * 2 * baseFluctuation;
                        finalValue *= randomFluctuation;

                        if (config.isDebugMode()) {
                            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_seasonal_price_fluctuation", "[Debug] 应用季节性价格浮动: 当前季节=%s, 季节倍率=%s, 随机浮动=%s, 浮动后价值=%s", currentSeason, seasonalMultiplier, randomFluctuation, finalValue));
                        }
                    }
                }

                value = Math.max(1, (int) Math.round(finalValue));
            }

            String sizeQuality;
            if (fishSize < minSize + (maxSize - minSize) * 0.3) {
                sizeQuality = messageManager.getMessageWithoutPrefix("fish_size_small", "Small Fry");
            } else if (fishSize < minSize + (maxSize - minSize) * 0.7) {
                sizeQuality = messageManager.getMessageWithoutPrefix("fish_size_medium", "Medium");
            } else {
                sizeQuality = messageManager.getMessageWithoutPrefix("fish_size_large", "Large");
            }

            lore.clear();

            String templateName = config.getFishTemplateName(fishName);
            String template = config.getFishTemplate(templateName);

            String displayLevel = fishLevel != null ? fishLevel.split(":")[0] : "1";

            Map<String, String> replacements = new HashMap<>();
            replacements.put("%name%", displayName);
            replacements.put("%description%", ChatColor.WHITE + description);
            replacements.put("%size%", ChatColor.GREEN + String.format("%.0f", fishSize));
            replacements.put("%size_quality%", ChatColor.GREEN + sizeQuality);
            replacements.put("%value%", ChatColor.RED + String.valueOf(value));
            replacements.put("%rarity%", ChatColor.translateAlternateColorCodes('&', rarityDisplayName));
            replacements.put("%separator%", ChatColor.GRAY + messageManager.getMessageWithoutPrefix("separator", "-------------------"));

            StringBuilder effectsInfo = new StringBuilder();
            List<String> effects = fishConfig.getStringList("fish." + fishName + ".effects");
            if (!effects.isEmpty()) {
                for (String effect : effects) {
                    String displayEffect = getEffectDisplayName(effect);
                    effectsInfo.append(ChatColor.WHITE + "  • " + displayEffect + "\n");
                }
                if (effectsInfo.length() > 0) {
                    effectsInfo.setLength(effectsInfo.length() - 1);
                }
            }
            replacements.put("%effects%", effectsInfo.toString());

            List<String> tips = messageManager.getMessageList(player, "fish_tips", new ArrayList<>());
            if (tips.isEmpty()) {
                tips = Arrays.asList("你可以在市场上出售它换取金币!", "今天你钓了多少条鱼了?", "它放在水族馆里会很好看!", "鱼肉看起来很美味!", "也许你可以用它来制作特殊药水?");
            }
            String randomTipContent = tips.get(random.nextInt(tips.size()));
            String randomTip = ChatColor.GRAY + "「 " + ChatColor.WHITE + randomTipContent + ChatColor.GRAY + " 」";
            replacements.put("%tip%", randomTip);

            String formattedLore = template;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                formattedLore = formattedLore.replace(entry.getKey(), entry.getValue());
            }

            formattedLore = ChatColor.translateAlternateColorCodes('&', formattedLore);

            String[] lines = formattedLore.split("\\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    lore.add(line);
                }
            }

            meta.setLore(lore);

            boolean enchantGlow = fishConfig.getBoolean("fish." + fishName + ".enchant-glow", false);
            if (enchantGlow) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            int customModelData = fishConfig.getInt("fish." + fishName + ".custom-model-data", -1);
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }

            UUID fishUUID = UUID.randomUUID();
            String uuidStr = fishUUID.toString();

            if (config.isDebugMode()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_uuid_generated", "[Debug] 生成鱼的UUID: %s", uuidStr));
            }

            lore.add(ChatColor.BLACK + "ID: " + uuidStr.substring(0, 8));
            meta.setLore(lore);

            if (config.isDebugMode()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_value_stored", "[Debug] 存储鱼的价值到数据库: UUID=%s, 价值=%s", uuidStr, value));
            }

            plugin.getDB().storeFishUUIDValue(uuidStr, value);

            List<String> fishEffects = fishConfig.getStringList("fish." + fishName + ".effects");

            if (config.isDebugMode()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_fish_effects_stored", "[Debug] 存储鱼的特殊效果到数据库: UUID=%s, 效果数量=%s", uuidStr, fishEffects.size()));
            }

            plugin.getDB().storeFishEffects(uuidStr, fishEffects);

            fishItem.setItemMeta(meta);

            boolean uuidStored = me.kkfish.utils.NBTUtil.setNBTData(fishItem, "fish_uuid", uuidStr);

            if (config.isDebugMode()) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("debug_uuid_stored_to_nbt", "[Debug] 存储UUID到NBT: %s", uuidStored ? "成功" : "失败"));
            }

            if (!uuidStored) {
                kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.fishing_uuid_nbt_failed", "Unable to store UUID in item NBT, but will continue creating item"));
            }
        }

        return fishItem;
    }

    public String getEffectDisplayName(String effect) {
        try {
            String[] parts = effect.split(" ");
            if (parts.length < 2) return effect;

            String effectType = parts[0];
            String[] levelDuration = parts[1].split(":");

            if (levelDuration.length < 2) return effect;

            if (config.getFishConfig().contains("effects-map")) {
                String mappedName = config.getFishConfig().getString("effects-map." + effectType);
                if (mappedName != null && !mappedName.isEmpty()) {
                    return mappedName + levelDuration[0] + " " + levelDuration[1] + "s";
                }
            }
        } catch (Exception e) {
            kkfish.log("§e" + plugin.getMessageManager().getMessageWithoutPrefix("log.fishing_effect_parse_failed", "Error parsing effect name: ") + e.getMessage());
        }
        return effect;
    }

    public ItemStack createOceanBackgroundItem() {
        Material glassMaterial = XSeriesUtil.getMaterial("LIGHT_BLUE_STAINED_GLASS_PANE");
        if (glassMaterial == null) {
            glassMaterial = Material.GLASS_PANE;
        }
        ItemStack glass = new ItemStack(glassMaterial);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f ");
            glass.setItemMeta(meta);
        }
        return glass;
    }
}
