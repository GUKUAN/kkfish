package me.kkfish.fishing;

import me.kkfish.kkfish;
import me.kkfish.managers.Config;
import me.kkfish.misc.MessageManager;
import me.kkfish.utils.ActionBarUtil;
import me.kkfish.utils.ActionBarUtil.MessageType;
import me.kkfish.utils.SchedulerUtil;
import me.kkfish.utils.XSeriesUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import me.kkfish.scheduler.SchedulerTask;
import org.bukkit.util.Vector;

public class WaterHookMechanic implements HookMechanic {

    private final kkfish plugin;
    private final Config config;
    private final MessageManager messageManager;
    private static final java.util.Map<java.util.UUID, SchedulerTask> floatTasks = new java.util.concurrent.ConcurrentHashMap<>();

    public WaterHookMechanic(kkfish plugin) {
        this.plugin = plugin;
        this.config = plugin.getCustomConfig();
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public WaterType getWaterType() {
        return WaterType.WATER;
    }

    @Override
    public void onHookLand(Player player, ArmorStand hookEntity, Location location, Vector velocity, double chargePercentage, String baitName) {
        createWaterSplashEffect(location);
        ActionBarUtil.sendActionBarPersistent(plugin, player, messageManager.getMessage("hook_in_water", "鱼钩已落入水中，等待鱼儿上钩..."), 40, MessageType.FISHING);
        Vector entryVelocity = velocity.clone();
        handleWaterMovement(player, hookEntity, entryVelocity, chargePercentage, baitName);
    }

    @Override
    public void startFloating(Player player, ArmorStand hookEntity) {
        FloatTask task = new FloatTask(plugin, hookEntity, this);
        task.start(player);
        floatTasks.put(hookEntity.getUniqueId(), task.bukkitTask);
    }

    private static class FloatTask implements Runnable {
        private final kkfish plugin;
        private final ArmorStand hookEntity;
        private final WaterHookMechanic mechanic;
        private SchedulerTask bukkitTask;
        private int floatTicks = 0;
        private final double floatAmplitude = 0.2;
        private Location floatStartLoc;

        FloatTask(kkfish plugin, ArmorStand hookEntity, WaterHookMechanic mechanic) {
            this.plugin = plugin;
            this.hookEntity = hookEntity;
            this.mechanic = mechanic;
            this.floatStartLoc = hookEntity.getLocation().clone();
        }

        void start(Player player) {
            this.bukkitTask = SchedulerUtil.runEntityTaskTimer(plugin, player, this, 0, 1);
        }

        @Override
        public void run() {
            if (!hookEntity.isValid()) {
                if (bukkitTask != null) {
                    bukkitTask.cancel();
                }
                floatTasks.remove(hookEntity.getUniqueId());
                return;
            }
            floatTicks++;
            double yOffset = Math.sin(floatTicks * 0.1) * floatAmplitude;
            Location currentLoc = floatStartLoc.clone();
            currentLoc.setY(currentLoc.getY() + yOffset);
            try {
                java.lang.reflect.Method m = hookEntity.getClass().getMethod("teleportAsync", org.bukkit.Location.class);
                m.invoke(hookEntity, currentLoc);
            } catch (Exception e) {
                try { java.lang.reflect.Method m = hookEntity.getClass().getMethod("teleportAsync", org.bukkit.Location.class); m.invoke(hookEntity, currentLoc); } catch (Exception ex) { hookEntity.teleport(currentLoc); }
            }
        }
    }

    @Override
    public void playAmbientEffect(Location location) {
    }

    @Override
    public void playBiteEffect(Player player, Location location) {
        plugin.getSoundManager().playBiteSound(location);
    }

    @Override
    public void playEscapeEffect(Player player, Location location) {
        plugin.getSoundManager().playFailSound(location);
    }

    @Override
    public void cleanup(Player player, ArmorStand hookEntity) {
        SchedulerTask task = floatTasks.remove(hookEntity.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void createWaterSplashEffect(Location location) {
        if (!config.isHookWaterSplashParticleEnabled()) return;
        Location particleLoc = location.clone().add(0, 1, 0);

        String particleType = config.getHookWaterSplashParticleType();
        int count = config.getHookWaterSplashParticleCount();
        double spread = config.getHookWaterSplashParticleSpreadX();

        XSeriesUtil.spawnParticle(particleLoc, particleType, count, spread, spread, spread, 0.3);
        XSeriesUtil.spawnParticle(particleLoc, "DRIP_WATER", count / 2, spread * 0.8, spread * 0.8, spread * 0.8, 0.1);
        XSeriesUtil.spawnParticle(particleLoc, "BUBBLE_POP", count / 3, spread * 0.7, spread * 0.7, spread * 0.7, 0.05);

        plugin.getSoundManager().playWaterSplashSound(location);
    }

    private void handleWaterMovement(Player player, ArmorStand hookEntity, Vector entryVelocity, double chargePercentage, String baitName) {
        final SchedulerTask[] taskRef = new SchedulerTask[1];

        Runnable runnable = new Runnable() {
            private double distanceMoved = 0;
            private final double targetDistance = 0.5;
            private final double waterResistance = 0.03;
            private int tickCount = 0;
            private final int maxTicks = 20;

            @Override
            public void run() {
                tickCount++;

                entryVelocity.multiply(1 - waterResistance);
                entryVelocity.setY(Math.max(entryVelocity.getY() - 0.01, -0.5));

                Location currentLoc = hookEntity.getLocation();
                currentLoc.add(entryVelocity);

                try {
                java.lang.reflect.Method m = hookEntity.getClass().getMethod("teleportAsync", org.bukkit.Location.class);
                m.invoke(hookEntity, currentLoc);
            } catch (Exception e) {
                try { java.lang.reflect.Method m = hookEntity.getClass().getMethod("teleportAsync", org.bukkit.Location.class); m.invoke(hookEntity, currentLoc); } catch (Exception ex) { hookEntity.teleport(currentLoc); }
            }

                distanceMoved += Math.abs(entryVelocity.getY());

                if (distanceMoved >= targetDistance || currentLoc.getBlockY() <= 0 || tickCount >= maxTicks) {
                    if (taskRef[0] != null) {
                        taskRef[0].cancel();
                    }

                    startFloating(player, hookEntity);
                    plugin.getFish().scheduleBiteCheck(player, chargePercentage, baitName);
                }
            }
        };

        taskRef[0] = SchedulerUtil.runEntityTaskTimer(plugin, player, runnable, 0, 1);
    }
}
