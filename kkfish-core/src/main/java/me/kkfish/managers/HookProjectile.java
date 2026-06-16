package me.kkfish.managers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.configuration.file.FileConfiguration;

import me.kkfish.kkfish;
import me.kkfish.fishing.HookMechanic;
import me.kkfish.fishing.HookMechanicFactory;
import me.kkfish.fishing.WaterType;
import me.kkfish.misc.MessageManager;
import me.kkfish.player.PlayerContext;
import me.kkfish.player.PlayerContextStore;
import me.kkfish.player.RuntimeData;
import me.kkfish.player.SessionData;
import me.kkfish.utils.ActionBarUtil;
import me.kkfish.utils.ActionBarUtil.MessageType;
import me.kkfish.utils.SchedulerUtil;
import me.kkfish.scheduler.SchedulerTask;
import me.kkfish.utils.XSeriesUtil;

import java.util.List;

/**
 * 负责鱼钩抛掷、抛物线轨迹、水域/地面检测、入水/落地处理及粒子效果。
 * 从 Fish.java 拆分而来。
 */
public class HookProjectile {

    private final kkfish plugin;
    private final Config config;
    private final MessageManager messageManager;
    private final PlayerContextStore playerContextStore;
    private final HookMechanicFactory hookMechanicFactory;
    private final Fish fish;

    private static java.lang.reflect.Method cachedIsPassableMethod = null;

    private static final Set<String> PASSABLE_BLOCK_NAMES = new HashSet<>(java.util.Arrays.asList(
        "TORCH", "REDSTONE_TORCH", "LANTERN", "SEA_LANTERN", "GLOWSTONE",
        "REDSTONE_LAMP", "REDSTONE_LAMP_OFF", "CAMPFIRE", "SOUL_CAMPFIRE",
        "SOUL_TORCH", "WALL_TORCH", "SOUL_WALL_TORCH", "LIGHT_BLOCK",
        "SOUL_LANTERN", "WALL_LANTERN", "SOUL_WALL_LANTERN"
    ));

    public HookProjectile(kkfish plugin, Config config, MessageManager messageManager,
                          PlayerContextStore playerContextStore, HookMechanicFactory hookMechanicFactory, Fish fish) {
        this.plugin = plugin;
        this.config = config;
        this.messageManager = messageManager;
        this.playerContextStore = playerContextStore;
        this.hookMechanicFactory = hookMechanicFactory;
        this.fish = fish;
    }

    private PlayerContext getContext(Player player) {
        if (player == null || playerContextStore == null) return null;
        return playerContextStore.getContext(player.getUniqueId());
    }

    public void throwFishHook(Player player, double chargePercentage) {
        if (chargePercentage > 100.0) {
            chargePercentage = 0;
        }

        String baitName = checkAndConsumeBait(player);

        final double finalChargePercentage = chargePercentage;
        final String finalBaitName = baitName;

        plugin.getSoundManager().playCastSound(player.getLocation());

        ArmorStand hookEntity = createHookEntity(player);

        Vector direction = calculateParabolicTrajectory(player, chargePercentage);

        handleHookTrajectory(player, hookEntity, direction, finalChargePercentage, finalBaitName);
    }

    private String checkAndConsumeBait(Player player) {
        ItemStack offhandItem = player.getInventory().getItemInOffHand();
        String baitName = null;
        if (offhandItem != null && offhandItem.getType() != Material.AIR) {
            ItemMeta meta = offhandItem.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                for (String line : lore) {
                    if (line.contains(plugin.getMessageManager().getMessageWithoutPrefix("bait_usage_tip", "放于副手，蓄力抛出时消耗"))) {
                            String displayName = meta.getDisplayName();
                            String currentBaitName = org.bukkit.ChatColor.stripColor(displayName);

                            if (!plugin.getCustomConfig().hasBaitPermission(player, currentBaitName)) {
                                player.sendMessage(plugin.getMessageManager().getMessage("bait_no_permission", "§d你没有权限使用这个鱼饵！"));
                                break;
                            }

                            offhandItem.setAmount(offhandItem.getAmount() - 1);
                            if (offhandItem.getAmount() <= 0) {
                                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                            } else {
                                player.getInventory().setItemInOffHand(offhandItem);
                            }
                            player.sendMessage(plugin.getMessageManager().getMessage("bait_used", "§a已消耗一个鱼饵，获得特殊效果！"));

                            baitName = currentBaitName;
                            break;
                        }
                }
            }
        }
        return baitName;
    }

    private ArmorStand createHookEntity(Player player) {
        World world = player.getWorld();
        UUID playerId = player.getUniqueId();
        PlayerContext ctx = getContext(player);

        Location startLocation = player.getEyeLocation().clone();

        ArmorStand hookEntity = world.spawn(startLocation, ArmorStand.class);
        hookEntity.setVisible(false);
        hookEntity.setGravity(false);
        hookEntity.setSmall(true);
        hookEntity.setMarker(true);
        hookEntity.setInvulnerable(true);
        hookEntity.setCustomNameVisible(false);
        hookEntity.setArms(false);
        hookEntity.setBasePlate(false);

        String hookMaterialNameFromDB = plugin.getDB().getPlayerHookMaterial(playerId.toString());

        if (!plugin.getCustomConfig().hasHookMaterialPermission(player, hookMaterialNameFromDB)) {
            Material woodMaterial = XSeriesUtil.getMaterial("OAK_LOG");
            if (ctx != null) {
                ctx.getSession().setHookMaterial(woodMaterial);
            }
            player.sendMessage(plugin.getMessageManager().getMessage("hook_no_permission", "§d你没有权限使用这个鱼钩材质！已自动切换为木质鱼钩。"));
        }

        Material hookMaterial = fish.getPlayerHookMaterial(player);

        hookEntity.getEquipment().setHelmet(new ItemStack(hookMaterial));
        try {
            hookEntity.getEquipment().setHelmetDropChance(0);
        } catch (Exception e) {
        }
        hookEntity.getEquipment().setItemInMainHand(null);

        if (ctx != null) {
            ctx.getSession().setFishingSession(hookEntity);
        }

        return hookEntity;
    }

    private Vector calculateParabolicTrajectory(Player player, double chargePercentage) {
        Vector direction = player.getLocation().getDirection().clone();
        double power = chargePercentage / 100.0;
        double speed = 0.3 + power * 0.3;

        direction.multiply(speed);
        direction.setY(direction.getY() + 0.08 + power * 0.05);

        return direction;
    }

    private void handleHookTrajectory(Player player, ArmorStand hookEntity, Vector direction, double chargePercentage, String baitName) {
        final SchedulerTask[] taskRef = new SchedulerTask[1];
        final PlayerContext ctx = getContext(player);

        taskRef[0] = SchedulerUtil.scheduleTask(plugin, new Runnable() {
            private int ticks = 0;
            private final Vector velocity = direction.clone();
            private final double gravity = 0.06 - (chargePercentage / 100.0 * 0.03);
            private boolean endVoidCountdownStarted = false;
            private int endVoidCountdownTicks = 0;

            @Override
            public void run() {
                ticks++;

                velocity.setY(velocity.getY() - gravity);

                Location currentLoc = hookEntity.getLocation();
                currentLoc.add(velocity);

                safeTeleport(hookEntity, currentLoc);

                WaterType waterType = hookMechanicFactory.detectWaterType(currentLoc, player);

                boolean isOnGround = checkIfHookOnGround(currentLoc.getBlock());

                if (waterType == null && hookMechanicFactory.isEndVoidCandidate(currentLoc, player)) {
                    if (!endVoidCountdownStarted) {
                        endVoidCountdownStarted = true;
                        endVoidCountdownTicks = 0;
                    }
                    endVoidCountdownTicks++;

                    if (endVoidCountdownTicks >= config.getEndCountdownTicks()) {
                        waterType = WaterType.VOID;
                        endVoidCountdownStarted = false;
                    }
                } else {
                    endVoidCountdownStarted = false;
                }

                boolean shouldCheckEscape = ticks > 100 && config.isFishEscapeBeforeMinigameEnabled();

                if (waterType != null || isOnGround || shouldCheckEscape) {
                    if (taskRef[0] != null) {
                        taskRef[0].cancel();
                        if (ctx != null) {
                            ctx.getRuntime().setTrajectoryTask(null);
                        }
                    }

                    if (waterType != null) {
                        if (ctx != null) {
                            ctx.getSession().setWaterType(waterType);
                            HookMechanic mechanic = hookMechanicFactory.create(waterType);
                            ctx.getSession().setHookMechanic(mechanic);
                            mechanic.onHookLand(player, hookEntity, currentLoc, velocity, chargePercentage, baitName);
                        } else {
                            HookMechanic mechanic = hookMechanicFactory.create(waterType);
                            mechanic.onHookLand(player, hookEntity, currentLoc, velocity, chargePercentage, baitName);
                        }
                    } else if (isOnGround) {
                        handleHookOnGround(player, hookEntity, currentLoc, velocity);
                    } else if (config.isFishEscapeBeforeMinigameEnabled()) {
                        handleHookFailure(player);
                    }
                }
            }
        }, 0, 1);
        if (ctx != null) {
            ctx.getRuntime().setTrajectoryTask(taskRef[0]);
        }
    }

    private boolean checkIfHookOnGround(Block block) {
        try {
            if (cachedIsPassableMethod == null) {
                cachedIsPassableMethod = block.getClass().getMethod("isPassable");
            }
            if (cachedIsPassableMethod != null) {
                boolean isPassable = (Boolean) cachedIsPassableMethod.invoke(block);
                if (!isPassable) {
                    return !isHookPassableBlock(block);
                }
                return false;
            }
        } catch (Exception ignored) {
        }

        if (block.getType().isSolid()) {
            return !isHookPassableBlock(block);
        }
        return false;
    }

    private boolean isHookPassableBlock(Block block) {
        return PASSABLE_BLOCK_NAMES.contains(block.getType().name());
    }

    public void handleHookInWater(Player player, ArmorStand hookEntity, Location currentLoc, Vector velocity, double chargePercentage, String baitName) {
        createWaterSplashEffect(currentLoc);

        ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("hook_in_water", "鱼钩已落入水中，等待鱼儿上钩..."), 40, MessageType.FISHING);

        final Vector entryVelocity = velocity.clone();

        handleHookInWaterMovement(player, hookEntity, entryVelocity, chargePercentage, baitName);
    }

    private void handleHookInWaterMovement(Player player, ArmorStand hookEntity, Vector entryVelocity, double chargePercentage, String baitName) {
        final BukkitRunnable[] runnableRef = new BukkitRunnable[1];
        final SchedulerTask[] taskRef = new SchedulerTask[1];

        runnableRef[0] = new BukkitRunnable() {
            private double distanceMoved = 0;
            private final double targetDistance = 0.5;
            private final double waterResistance = 0.03;
            private int tickCount = 0;
            private final int maxTicks = 20;

            @Override
            public void run() {
                tickCount++;

                entryVelocity.multiply(1 - waterResistance);
                entryVelocity.setY(entryVelocity.getY() - 0.01);

                Location currentLoc = hookEntity.getLocation();
                currentLoc.add(entryVelocity);

                safeTeleport(hookEntity, currentLoc);

                distanceMoved += Math.abs(entryVelocity.getY());

                if (distanceMoved >= targetDistance || currentLoc.getBlockY() <= 0 || tickCount >= maxTicks) {
                    if (taskRef[0] != null) {
                        SchedulerUtil.cancelTask(taskRef[0]);
                    }

                    startHookFloatingEffect(player, hookEntity);

                    // 通过 Fish 协调器调度咬钩检查
                    fish.scheduleBiteCheck(player, chargePercentage, baitName);
                }
            }
        };

        taskRef[0] = SchedulerUtil.scheduleTask(plugin, runnableRef[0], 0, 1);
    }

    private void startHookFloatingEffect(Player player, ArmorStand hookEntity) {
        final PlayerContext ctx = getContext(player);
        SchedulerTask task = SchedulerUtil.scheduleTask(plugin, new BukkitRunnable() {
            private int floatTicks = 0;
            private final double floatAmplitude = 0.2;
            private final Location floatStartLoc = hookEntity.getLocation().clone();

            @Override
            public void run() {
                if (!hookEntity.isValid()) {
                    try {
                        this.cancel();
                    } catch (IllegalStateException ignored) {
                        // 任务未调度，无需取消
                    }
                    if (ctx != null) {
                        ctx.getRuntime().setFloatingEffectTask(null);
                    }
                    return;
                }
                floatTicks++;

                double yOffset = Math.sin(floatTicks * 0.1) * floatAmplitude;
                Location currentLoc = floatStartLoc.clone();
                currentLoc.setY(currentLoc.getY() + yOffset);

                safeTeleport(hookEntity, currentLoc);
            }
        }, 0, 1);
        if (ctx != null) {
            ctx.getRuntime().setFloatingEffectTask(task);
        }
    }

    private void handleHookOnGround(Player player, ArmorStand hookEntity, Location currentLoc, Vector velocity) {
        ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("hook_not_in_water", "鱼钩没有落入水中！"), 40, MessageType.FISHING);

        final BukkitRunnable[] runnableRef = new BukkitRunnable[1];
        final SchedulerTask[] taskRef = new SchedulerTask[1];

        runnableRef[0] = new BukkitRunnable() {
            private double distanceMoved = 0;
            private final double targetDistance = 0.5;
            private final Vector groundVelocity = velocity.clone().normalize();

            @Override
            public void run() {
                Location currentLoc = hookEntity.getLocation();
                currentLoc.add(groundVelocity.clone().multiply(0.1));

                safeTeleport(hookEntity, currentLoc);

                distanceMoved += 0.1;

                boolean isBlockPassable = true;
                try {
                    Block block = currentLoc.getBlock();
                    java.lang.reflect.Method isPassableMethod = block.getClass().getMethod("isPassable");
                    if (isPassableMethod != null) {
                        isBlockPassable = (Boolean) isPassableMethod.invoke(block);
                    }
                } catch (Exception e) {
                    isBlockPassable = !currentLoc.getBlock().getType().isSolid();
                }

                if (distanceMoved >= targetDistance || !isBlockPassable) {
                    if (taskRef[0] != null) {
                        SchedulerUtil.cancelTask(taskRef[0]);
                    }
                    fish.endSession(player);
                }
            }
        };

        taskRef[0] = SchedulerUtil.scheduleTask(plugin, runnableRef[0], 0, 1);
    }

    private void handleHookFailure(Player player) {
        ActionBarUtil.sendActionBarPersistent(kkfish.getInstance(), player, messageManager.getMessage("hook_not_in_water", "鱼钩没有落入水中！"), 40, MessageType.FISHING);
        fish.endSession(player);
    }

    // ==================== 粒子效果 ====================

    public void spawnParticleEffects(Location location) {
        if (!config.isFishEmergeParticleEnabled()) return;

        try {
            double offsetX = config.getFishEmergeParticleOffsetX();
            double offsetY = config.getFishEmergeParticleOffsetY();
            double offsetZ = config.getFishEmergeParticleOffsetZ();

            Location particleLocation = location.clone().add(offsetX, offsetY, offsetZ);

            String particleTypeStr = config.getFishEmergeParticleType();

            int count = config.getFishEmergeParticleCount();
            double spreadX = config.getFishEmergeParticleSpreadX();
            double spreadY = config.getFishEmergeParticleSpreadY();
            double spreadZ = config.getFishEmergeParticleSpreadZ();
            double extra = config.getFishEmergeParticleExtra();

            try {
                Class.forName("org.bukkit.Particle$DustOptions");
                if ("REDSTONE".equalsIgnoreCase(particleTypeStr)) {
                    int red = config.getFishEmergeParticleRed();
                    int green = config.getFishEmergeParticleGreen();
                    int blue = config.getFishEmergeParticleBlue();
                    float size = config.getFishEmergeParticleSize();

                    plugin.getEntityBatchProcessor().addParticle(
                            particleTypeStr,
                            particleLocation,
                            count,
                            spreadX, spreadY, spreadZ,
                            extra,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(red, green, blue), size)
                    );
                } else {
                    plugin.getEntityBatchProcessor().addParticle(
                            particleTypeStr,
                            particleLocation,
                            count,
                            spreadX, spreadY, spreadZ,
                            extra,
                            null
                    );
                }
            } catch (ClassNotFoundException e) {
                plugin.getEntityBatchProcessor().addParticle(
                        "CLOUD",
                        particleLocation,
                        count,
                        spreadX, spreadY, spreadZ,
                        extra,
                        null
                );
            }
        } catch (Exception e) {
            kkfish.log(plugin.getMessageManager().getMessageWithoutPrefix("log.fishing_particle_spawn_failed", "§eFailed to spawn particle effects: " + e.getMessage(), e.getMessage()));
            try {
                double offsetX = config.getFishEmergeParticleOffsetX();
                double offsetY = config.getFishEmergeParticleOffsetY();
                double offsetZ = config.getFishEmergeParticleOffsetZ();
                Location particleLocation = location.clone().add(offsetX, offsetY, offsetZ);

                plugin.getEntityBatchProcessor().addParticle(
                        "CLOUD",
                        particleLocation,
                        15,
                        config.getFishEmergeParticleSpreadX(),
                        config.getFishEmergeParticleSpreadY(),
                        config.getFishEmergeParticleSpreadZ(),
                        0.1,
                        null
                );
            } catch (Exception ex) {
                kkfish.log("§c" + "Failed to spawn fallback particle effects: " + ex.getMessage());
            }
        }
    }

    public void createWaterSplashEffect(Location location) {
        if (!config.isHookWaterSplashParticleEnabled()) return;

        String particleType = config.getHookWaterSplashParticleType();
        int count = config.getHookWaterSplashParticleCount();
        double spread = config.getHookWaterSplashParticleSpreadX();
        double speed = 0.3;

        try {
            plugin.getEntityBatchProcessor().addParticle(particleType, location, count, spread, spread, spread, speed, null);

            plugin.getEntityBatchProcessor().addParticle("DRIP_WATER", location, count / 2, spread * 0.8, spread * 0.8, spread * 0.8, 0.1, null);

            plugin.getEntityBatchProcessor().addParticle("BUBBLE_POP", location, count / 3, spread * 0.7, spread * 0.7, spread * 0.7, 0.05, null);
        } catch (Exception e) {
            // 如果粒子类型不存在，使用默认值
            plugin.getEntityBatchProcessor().addParticle("WATER_SPLASH", location, count, spread, spread, spread, speed, null);

            // 额外添加水滴粒子增强效果
            plugin.getEntityBatchProcessor().addParticle("DRIP_WATER", location, count / 2, spread * 0.8, spread * 0.8, spread * 0.8, 0.1, null);

            // 添加气泡粒子增强视觉效果
            plugin.getEntityBatchProcessor().addParticle("BUBBLE_POP", location, count / 3, spread * 0.7, spread * 0.7, spread * 0.7, 0.05, null);
        }

        // 播放溅水声音效果
        plugin.getSoundManager().playWaterSplashSound(location);
    }

    public void createLavaSplashEffect(Location location) {
        if (!config.isHookWaterSplashParticleEnabled()) return;

        String particleType = config.getHookWaterSplashParticleType();
        int count = config.getHookWaterSplashParticleCount();
        double spread = config.getHookWaterSplashParticleSpreadX();

        try {
            plugin.getEntityBatchProcessor().addParticle("FLAME", location, count, spread, spread, spread, 0.2, null);

            plugin.getEntityBatchProcessor().addParticle("LAVA", location, count / 2, spread * 0.8, spread * 0.8, spread * 0.8, 0.1, null);

            plugin.getEntityBatchProcessor().addParticle("SMOKE", location, count / 3, spread * 0.7, spread * 0.7, spread * 0.7, 0.05, null);
        } catch (Exception e) {
            plugin.getEntityBatchProcessor().addParticle("FLAME", location, count, spread, spread, spread, 0.2, null);
        }

        plugin.getSoundManager().playWaterSplashSound(location);
    }

    public void createVoidSplashEffect(Location location) {
        if (!config.isHookWaterSplashParticleEnabled()) return;

        int count = config.getHookWaterSplashParticleCount();
        double spread = config.getHookWaterSplashParticleSpreadX();

        try {
            plugin.getEntityBatchProcessor().addParticle("END_ROD", location, count, spread, spread, spread, 0.1, null);

            plugin.getEntityBatchProcessor().addParticle("DRAGON_BREATH", location, count / 2, spread * 0.8, spread * 0.8, spread * 0.8, 0.05, null);

            plugin.getEntityBatchProcessor().addParticle("PORTAL", location, count / 3, spread * 0.7, spread * 0.7, spread * 0.7, 0.1, null);
        } catch (Exception e) {
            plugin.getEntityBatchProcessor().addParticle("END_ROD", location, count, spread, spread, spread, 0.1, null);
        }

        plugin.getSoundManager().playWaterSplashSound(location);
    }

    static void safeTeleport(org.bukkit.entity.Entity entity, org.bukkit.Location loc) {
        me.kkfish.utils.NmsAdapter.teleportEntityAsync(entity, loc);
    }
}
