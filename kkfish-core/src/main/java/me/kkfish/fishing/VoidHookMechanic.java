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

public class VoidHookMechanic implements HookMechanic {

    private final kkfish plugin;
    private final Config config;
    private final MessageManager messageManager;
    private static final java.util.Map<java.util.UUID, SchedulerTask> floatTasks = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Random random = new java.util.Random();

    public VoidHookMechanic(kkfish plugin) {
        this.plugin = plugin;
        this.config = plugin.getCustomConfig();
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public WaterType getWaterType() {
        return WaterType.VOID;
    }

    @Override
    public void onHookLand(Player player, ArmorStand hookEntity, Location location, org.bukkit.util.Vector velocity, double chargePercentage, String baitName) {
        createVoidEntryEffect(location);
        ActionBarUtil.sendActionBarPersistent(plugin, player, messageManager.getMessage("hook_in_void", "鱼钩已进入虚空，等待鱼儿上钩..."), 40, MessageType.FISHING);

        startFloating(player, hookEntity);
        plugin.getFish().scheduleBiteCheck(player, chargePercentage, baitName);
    }

    @Override
    public void startFloating(Player player, ArmorStand hookEntity) {
        final SchedulerTask[] taskRef = new SchedulerTask[1];

        Runnable runnable = new Runnable() {
            private int floatTicks = 0;
            private final double floatAmplitude = 0.15;
            private final Location floatStartLoc = hookEntity.getLocation().clone();

            @Override
            public void run() {
                if (!hookEntity.isValid()) {
                    if (taskRef[0] != null) {
                        taskRef[0].cancel();
                    }
                    floatTasks.remove(hookEntity.getUniqueId());
                    return;
                }
                floatTicks++;
                double yOffset = Math.sin(floatTicks * 0.08) * floatAmplitude;
                Location currentLoc = floatStartLoc.clone();
                currentLoc.setY(currentLoc.getY() + yOffset);
                try {
                java.lang.reflect.Method m = hookEntity.getClass().getMethod("teleportAsync", org.bukkit.Location.class);
                m.invoke(hookEntity, currentLoc);
            } catch (Exception e) {
                try { java.lang.reflect.Method m = hookEntity.getClass().getMethod("teleportAsync", org.bukkit.Location.class); m.invoke(hookEntity, currentLoc); } catch (Exception ex) { hookEntity.teleport(currentLoc); }
            }

                if (floatTicks % 8 == 0) {
                    playAmbientEffect(currentLoc);
                }
            }
        };

        taskRef[0] = SchedulerUtil.runEntityTaskTimer(plugin, player, runnable, 0, 1);
        floatTasks.put(hookEntity.getUniqueId(), taskRef[0]);
    }



    @Override
    public void playAmbientEffect(Location location) {
        String particleName = config.getVoidAmbientParticle();
        int count = config.getVoidAmbientParticleCount();
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = 0.5;
        double x = location.getX() + Math.cos(angle) * radius;
        double z = location.getZ() + Math.sin(angle) * radius;
        Location particleLoc = new Location(location.getWorld(), x, location.getY() + 1 - 0.15, z);
        XSeriesUtil.spawnParticle(particleLoc, particleName, count, 0, 0, 0, 0.0);
    }

    @Override
    public void playBiteEffect(Player player, Location location) {
        String particleName = config.getVoidBiteParticle();
        int count = config.getVoidBiteParticleCount();
        Location particleLoc = location.clone().add(0, 1, 0);
        XSeriesUtil.spawnParticle(particleLoc, particleName, count, 0.3, 0.0, 0.3, 0.2);

        XSeriesUtil.spawnParticle(particleLoc, "DRAGON_BREATH", 5, 0.3, 0.0, 0.3, 0.1);

        String soundName = config.getVoidBiteSound();
        float volume = config.getVoidBiteSoundVolume();
        float pitch = config.getVoidBiteSoundPitch();
        XSeriesUtil.playSound(location, soundName, volume, pitch + (float) (random.nextDouble() - random.nextDouble()) * 0.4F);
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

    private void createVoidEntryEffect(Location location) {
        Location particleLoc = location.clone().add(0, 1, 0);
        XSeriesUtil.spawnParticle(particleLoc, "END_ROD", 20, 0.3, 0.3, 0.3, 0.1);
        XSeriesUtil.spawnParticle(particleLoc, "DRAGON_BREATH", 10, 0.2, 0.2, 0.2, 0.05);
        XSeriesUtil.playSound(location, "ITEM_TRIDENT_THUNDER", 0.3F, 0.8F);
    }
}
