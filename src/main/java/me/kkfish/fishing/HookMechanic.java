package me.kkfish.fishing;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public interface HookMechanic {
    WaterType getWaterType();

    void onHookLand(Player player, ArmorStand hookEntity, Location location, Vector velocity, double chargePercentage, String baitName);

    void startFloating(Player player, ArmorStand hookEntity);

    void playAmbientEffect(Location location);

    void playBiteEffect(Player player, Location location);

    void playEscapeEffect(Player player, Location location);

    void cleanup(Player player, ArmorStand hookEntity);
}
