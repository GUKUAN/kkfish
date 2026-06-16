package me.kkfish.fishing;

import me.kkfish.kkfish;
import me.kkfish.managers.Config;
import me.kkfish.misc.MessageManager;
import me.kkfish.utils.ActionBarUtil;
import me.kkfish.utils.ActionBarUtil.MessageType;
import me.kkfish.utils.SchedulerUtil;
import me.kkfish.utils.XSeriesUtil;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import me.kkfish.scheduler.SchedulerTask;
import org.bukkit.util.Vector;

public class LavaHookMechanic implements HookMechanic {

    private final kkfish plugin;
    private final Config config;
    private final MessageManager messageManager;
    private static final java.util.Map<java.util.UUID, SchedulerTask> floatTasks = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Random random = new java.util.Random();

    public LavaHookMechanic(kkfish plugin) {
        this.plugin = plugin;
        this.config = plugin.getCustomConfig();
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public WaterType getWaterType() {
        return WaterType.LAVA;
    }

    @Override
    public void onHookLand(Player player, ArmorStand hookEntity, Location location, Vector velocity, double chargePercentage, String baitName) {
        createLavaSplashEffect(location);
        ActionBarUtil.sendActionBarPersistent(plugin, player, messageManager.getMessage("hook_in_lava", "鱼钩已落入岩浆中，等待鱼儿上钩..."), 40, MessageType.FISHING);
        Vector entryVelocity = velocity.clone();
        handleLavaMovement(player, hookEntity, entryVelocity, chargePercentage, baitName);
    }

    @Override
    public void startFloating(Player player, ArmorStand hookEntity) {
        FloatTask task = new FloatTask(plugin, hookEntity, this);
        task.start(player);
        floatTasks.put(player.getUniqueId(), task.bukkitTask);
    }

    private static class FloatTask implements Runnable {
        private final kkfish plugin;
        private final ArmorStand hookEntity;
        private final LavaHookMechanic mechanic;
        private SchedulerTask bukkitTask;
        private int floatTicks = 0;
        private final double floatAmplitude = 0.15;
        private Location floatStartLoc;

        FloatTask(kkfish plugin, ArmorStand hookEntity, LavaHookMechanic mechanic) {
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
                return;
            }
            floatTicks++;
            double yOffset = Math.sin(floatTicks * 0.1) * floatAmplitude;
            Location currentLoc = floatStartLoc.clone();
            currentLoc.setY(currentLoc.getY() + yOffset);
            try {
                me.kkfish.utils.NmsAdapter.teleportEntityAsync(hookEntity, currentLoc);
            } catch (Exception e) {
                hookEntity.teleport(currentLoc);
            }

            if (floatTicks % 10 == 0) {
                mechanic.playAmbientEffect(currentLoc);
            }
        }
    }

    @Override
    public void playAmbientEffect(Location location) {
        String particleName = config.getLavaAmbientParticle();
        int count = config.getLavaAmbientParticleCount();
        Location particleLoc = location.clone().add(0, 1, 0);
        XSeriesUtil.spawnParticle(particleLoc, particleName, count, 0.15, 0.1, 0.15, 0.02);
    }

    @Override
    public void playBiteEffect(Player player, Location location) {
        String particleName = config.getLavaBiteParticle();
        int count = config.getLavaBiteParticleCount();
        Location particleLoc = location.clone().add(0, 1, 0);
        XSeriesUtil.spawnParticle(particleLoc, particleName, count, 0.3, 0.0, 0.3, 0.2);

        String soundName = config.getLavaBiteSound();
        float volume = config.getLavaBiteSoundVolume();
        float pitch = config.getLavaBiteSoundPitch();
        XSeriesUtil.playSound(location, soundName, volume, pitch + (float) (random.nextDouble() - random.nextDouble()) * 0.4F);
    }

    @Override
    public void playEscapeEffect(Player player, Location location) {
        plugin.getSoundManager().playFailSound(location);
    }

    @Override
    public void cleanup(Player player, ArmorStand hookEntity) {
        SchedulerTask task = floatTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void createLavaSplashEffect(Location location) {
        Location particleLoc = location.clone().add(0, 1, 0);
        XSeriesUtil.spawnParticle(particleLoc, "FLAME", 15, 0.2, 0.1, 0.2, 0.05);
        XSeriesUtil.spawnParticle(particleLoc, "LAVA", 10, 0.2, 0.1, 0.2, 0.01);
        XSeriesUtil.playSound(location, "ENTITY_GENERIC_EXTINGUISH_FIRE", 0.5F, 1.0F);
    }

    private void handleLavaMovement(Player player, ArmorStand hookEntity, Vector entryVelocity, double chargePercentage, String baitName) {
        final SchedulerTask[] taskRef = new SchedulerTask[1];

        Runnable runnable = new Runnable() {
            private double distanceMoved = 0;
            private final double targetDistance = 0.4;
            private final double lavaResistance = 0.05;
            private int tickCount = 0;
            private final int maxTicks = 20;

            @Override
            public void run() {
                tickCount++;

                entryVelocity.multiply(1 - lavaResistance);
                entryVelocity.setY(entryVelocity.getY() - 0.008);

                Location currentLoc = hookEntity.getLocation();
                currentLoc.add(entryVelocity);

                try {
                me.kkfish.utils.NmsAdapter.teleportEntityAsync(hookEntity, currentLoc);
            } catch (Exception e) {
                hookEntity.teleport(currentLoc);
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
