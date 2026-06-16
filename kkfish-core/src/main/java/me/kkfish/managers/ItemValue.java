package me.kkfish.managers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.FileConfiguration;
import me.kkfish.kkfish;
import me.kkfish.managers.Config;
import me.kkfish.utils.XSeriesUtil;

import java.util.ArrayList;
import java.util.List;

public class ItemValue {

    private final Config config;

    public ItemValue(Config config) {
        this.config = config;
    }

    public List<ItemStack> getItemRewards(String fishName) {
        List<ItemStack> rewards = new ArrayList<>();
        FileConfiguration fishConfig = config.getFishConfig();

        if (fishConfig.contains("fish." + fishName + ".item-value")) {
            List<String> itemValueList = fishConfig.getStringList("fish." + fishName + ".item-value");
            for (String itemValue : itemValueList) {
                String[] parts = itemValue.split(":");
                if (parts.length == 2) {
                    String materialName = parts[0].trim();
                    int amount;
                    try {
                        amount = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    Material material = XSeriesUtil.parseMaterial(materialName);
                    if (material == null) {
                        kkfish.log(kkfish.getInstance().getMessageManager().getMessageWithoutPrefix("log.item_value_invalid_material", "§eInvalid material in item-value: " + materialName, materialName));
                        continue;
                    }
                    if (material.isItem()) {
                        ItemStack itemStack = new ItemStack(material, amount);
                        rewards.add(itemStack);
                    }
                }
            }
        }

        return rewards;
    }

    public boolean hasItemRewards(String fishName) {
        FileConfiguration fishConfig = config.getFishConfig();
        return fishConfig.contains("fish." + fishName + ".item-value");
    }
}
