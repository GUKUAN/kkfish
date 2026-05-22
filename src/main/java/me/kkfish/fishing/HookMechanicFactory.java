package me.kkfish.fishing;

import me.kkfish.kkfish;
import me.kkfish.managers.Config;
import me.kkfish.utils.XSeriesUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class HookMechanicFactory {

    private final kkfish plugin;
    private final WaterHookMechanic waterMechanic;
    private final LavaHookMechanic lavaMechanic;
    private final VoidHookMechanic voidMechanic;

    public HookMechanicFactory(kkfish plugin) {
        this.plugin = plugin;
        this.waterMechanic = new WaterHookMechanic(plugin);
        this.lavaMechanic = new LavaHookMechanic(plugin);
        this.voidMechanic = new VoidHookMechanic(plugin);
    }

    public HookMechanic create(WaterType waterType) {
        switch (waterType) {
            case LAVA:
                return lavaMechanic;
            case VOID:
                return voidMechanic;
            case WATER:
            default:
                return waterMechanic;
        }
    }

    public WaterType detectWaterType(Location location, Player player) {
        Config config = plugin.getCustomConfig();
        Location checkLoc = location.clone().add(0, 2, 0);

        if (isLiquidBlock(checkLoc, true)) {
            return WaterType.WATER;
        }

        if (isLiquidBlock(checkLoc, false)) {
            if (canFishInLava(player)) {
                return WaterType.LAVA;
            }
            return null;
        }

        if (isVoidLocation(checkLoc, player)) {
            if (canFishInVoid(player)) {
                return WaterType.VOID;
            }
            return null;
        }

        return null;
    }

    public boolean isEndVoidCandidate(Location hookLocation, Player player) {
        Config config = plugin.getCustomConfig();
        World world = hookLocation.getWorld();
        if (world == null || world.getEnvironment() != World.Environment.THE_END) {
            return false;
        }
        if (!config.isVoidFishingEnabled()) {
            return false;
        }
        if (!config.isEndDetectionEnabled()) {
            return false;
        }
        if (config.isEndRequireBelowPlayer() && hookLocation.getY() > player.getLocation().getY()) {
            return false;
        }
        Location checkLoc = hookLocation.clone().add(0, 2, 0);
        if (isLiquidBlock(checkLoc, true) || isLiquidBlock(checkLoc, false)) {
            return false;
        }
        return true;
    }

    private boolean isLiquidBlock(Location location, boolean checkWater) {
        Block block = location.getBlock();
        Block belowBlock = block.getRelative(org.bukkit.block.BlockFace.DOWN);
        
        if (checkWater) {
            return XSeriesUtil.isWaterBlock(block) || XSeriesUtil.isWaterBlock(belowBlock);
        } else {
            return XSeriesUtil.isLavaBlock(block) || XSeriesUtil.isLavaBlock(belowBlock);
        }
    }

    private boolean isVoidLocation(Location location, Player player) {
        World world = location.getWorld();
        if (world == null) return false;

        if (world.getEnvironment() == World.Environment.THE_END) {
            return false;
        }

        return false;
    }

    private boolean canFishInLava(Player player) {
        Config config = plugin.getCustomConfig();
        if (!config.isLavaFishingEnabled()) return false;

        String triggerMode = config.getLavaTriggerMode();
        switch (triggerMode.toUpperCase()) {
            case "AUTO":
                return true;
            case "EQUIPMENT":
                return hasEquipmentEffect(player, "lava-fishing");
            case "PERMISSION":
                return player.hasPermission("kkfish.fishing.lava");
            case "BOTH":
                return hasEquipmentEffect(player, "lava-fishing")
                        || player.hasPermission("kkfish.fishing.lava");
            default:
                return false;
        }
    }

    private boolean canFishInVoid(Player player) {
        Config config = plugin.getCustomConfig();
        if (!config.isVoidFishingEnabled()) return false;

        String triggerMode = config.getVoidTriggerMode();
        switch (triggerMode.toUpperCase()) {
            case "AUTO":
                return true;
            case "EQUIPMENT":
                return hasEquipmentEffect(player, "void-fishing");
            case "PERMISSION":
                return player.hasPermission("kkfish.fishing.void");
            case "BOTH":
                return hasEquipmentEffect(player, "void-fishing")
                        || player.hasPermission("kkfish.fishing.void");
            default:
                return false;
        }
    }

    private boolean hasEquipmentEffect(Player player, String effectName) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (hasEffectOnItem(mainHand, effectName)) return true;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (hasEffectOnItem(offHand, effectName)) return true;

        ItemStack[] armorContents = player.getInventory().getArmorContents();
        for (ItemStack armor : armorContents) {
            if (hasEffectOnItem(armor, effectName)) return true;
        }

        return false;
    }

    private boolean hasEffectOnItem(ItemStack item, String effectName) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;

        List<String> lore = meta.getLore();
        if (lore == null) return false;

        String searchKey = effectName.replace("-", " ").toLowerCase();
        for (String line : lore) {
            String stripped = org.bukkit.ChatColor.stripColor(line).toLowerCase();
            if (stripped.contains(searchKey)) return true;
        }

        return false;
    }
}
