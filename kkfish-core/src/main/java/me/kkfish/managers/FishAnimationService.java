package me.kkfish.managers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.kkfish.kkfish;
import me.kkfish.fishing.WaterType;

/**
 * 负责鱼从水中飞向玩家头顶的动画效果，包括粒子轨迹。
 * 从 Fish.java 拆分而来。
 */
public class FishAnimationService {

    private final kkfish plugin;
    private final Config config;
    private final Fish fish;
    private final Random random;

    private final Map<String, Particle> particleCache = new HashMap<>();

    public FishAnimationService(kkfish plugin, Config config, Fish fish, Random random) {
        this.plugin = plugin;
        this.config = config;
        this.fish = fish;
        this.random = random;
    }

    public void animateFishToPlayer(Player player, Location fishStartLocation, ItemStack fishItem, double fishValue) {
        animateFishToPlayer(player, fishStartLocation, fishStartLocation, fishItem, fishValue, WaterType.WATER, null);
    }

    public void animateFishToPlayer(Player player, Location fishStartLocation, Location hookLocation, ItemStack fishItem, double fishValue, WaterType waterType, Fish.AnimationCompleteCallback callback) {
        if (!config.isFishAnimationEnabled()) {
            if (callback != null) {
                callback.onAnimationComplete();
            }
            return;
        }

        World world = player.getWorld();

        ItemStack cleanFishItem = new ItemStack(fishItem.getType(), 1);
        ItemMeta meta = cleanFishItem.getItemMeta();
        if (meta != null) {
            if (fishItem.getItemMeta().hasDisplayName()) {
                meta.setDisplayName(fishItem.getItemMeta().getDisplayName());
            }
            if (fishItem.getItemMeta().hasLore()) {
                meta.setLore(fishItem.getItemMeta().getLore());
            }
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            cleanFishItem.setItemMeta(meta);
        }

        Item fishEntity = world.dropItemNaturally(hookLocation, cleanFishItem);
        fishEntity.setPickupDelay(Integer.MAX_VALUE);

        try {
            java.lang.reflect.Method setInvulnerableMethod = fishEntity.getClass().getMethod("setInvulnerable", boolean.class);
            setInvulnerableMethod.invoke(fishEntity, true);
        } catch (Exception e) {
        }

        try {
            fishEntity.setFireTicks(0);
        } catch (Exception e) {
        }

        try {
            java.lang.reflect.Method setGlowingMethod = fishEntity.getClass().getMethod("setGlowing", boolean.class);
            setGlowingMethod.invoke(fishEntity, true);
        } catch (Exception e) {
        }

        try {
            java.lang.reflect.Method setGravityMethod = fishEntity.getClass().getMethod("setGravity", boolean.class);
            setGravityMethod.invoke(fishEntity, false);
        } catch (Exception e) {
        }

        fishEntity.setVelocity(new Vector(0, 0, 0));

        // 通过 Fish 协调器调用粒子/溅水效果（由 HookProjectile 提供）
        if (waterType == WaterType.LAVA) {
            fish.createLavaSplashEffect(hookLocation);
        } else if (waterType == WaterType.VOID) {
            fish.createVoidSplashEffect(hookLocation);
        } else {
            fish.createWaterSplashEffect(hookLocation);
        }
        fish.spawnParticleEffects(hookLocation);

        double distance;
        if (hookLocation.getWorld() != null && hookLocation.getWorld().equals(player.getWorld())) {
            distance = hookLocation.distance(player.getLocation());
        } else {
            distance = 10.0;
        }
        int animationTicks = (int) Math.min(40 + distance * 4, 100);

        new OldFishAnimationTask(animationTicks, fishEntity, player, callback,
                hookLocation.getX(), hookLocation.getZ(), 0.3, hookLocation.getY(), 1.5, world, 0.6).runTaskTimer(plugin, 0, 1);
    }

    static void safeTeleport(org.bukkit.entity.Entity entity, org.bukkit.Location loc) {
        me.kkfish.utils.NmsAdapter.teleportEntityAsync(entity, loc);
    }

    Particle getSafeParticle(String particleName, Particle fallback) {
        if (particleCache.containsKey(particleName)) {
            Particle cached = particleCache.get(particleName);
            return cached != null ? cached : fallback;
        }
        // 使用 XSeries 解析粒子，保证多版本兼容
        Particle result = me.kkfish.utils.XSeriesUtil.getParticle(particleName);
        particleCache.put(particleName, result);
        return result != null ? result : fallback;
    }

    void spawnSafeParticle(World world, Particle particle, Location location, int count, double spreadX, double spreadY, double spreadZ, double extra, Object data) {
        // 通过 XSeries 生成粒子，禁止直接调用 world.spawnParticle
        me.kkfish.utils.XSeriesUtil.spawnParticleWithData(location, particle.name(), count, spreadX, spreadY, spreadZ, extra, data);
    }

    private void spawnParticleTrail(Location location, int ticks) {
        try {
            World world = location.getWorld();
            if (world != null) {
                Particle waterSplashParticle = getSafeParticle("WATER_SPLASH", null);
                if (waterSplashParticle != null) {
                    spawnSafeParticle(world, waterSplashParticle, location, 1, 0.15, 0.15, 0.15, 0.02, null);
                }
                if (ticks % 8 == 0) {
                    Particle cloudParticle = getSafeParticle("CLOUD", null);
                    if (cloudParticle != null) {
                        spawnSafeParticle(world, cloudParticle, location, 1, 0.08, 0.08, 0.08, 0.01, null);
                    }
                }
            }
        } catch (Exception e) {
            try {
                World world = location.getWorld();
                if (world != null) {
                    Particle dripWaterParticle = getSafeParticle("DRIP_WATER", null);
                    if (dripWaterParticle != null) {
                        spawnSafeParticle(world, dripWaterParticle, location, 1, 0.15, 0.15, 0.15, 0.02, null);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 鱼飞向玩家头顶的动画任务（三阶段：跳跃到头顶 → 停留 → 飞入背包）。
     */
    private class OldFishAnimationTask extends BukkitRunnable {
        private final Item fishEntity;
        private final Player player;
        private final Fish.AnimationCompleteCallback callback;
        private final World world;
        private final double startX;
        private final double startY;
        private final double startZ;

        private int ticks;
        private boolean isComplete;
        private boolean isAtHead;
        private int headStayTicks;
        private final int HEAD_STAY_TICKS;
        private final int ANIMATION_STAGES = 3;
        private final int JUMP_TO_HEAD_STAGE = 1;
        private final int STAY_AT_HEAD_STAGE = 2;
        private final int GO_TO_INVENTORY_STAGE = 3;
        private int currentStage;
        private ArmorStand floatingTextEntity;
        private final String fishName;
        private final double fishSize;

        public OldFishAnimationTask(int maxTicks, Item fishEntity,
                                  Player player, Fish.AnimationCompleteCallback callback, double startX,
                                  double startZ, double peakProgress, double startY, double parabolaFactor,
                                  World world, double maxSpeed) {
            this.fishEntity = fishEntity;
            this.player = player;
            this.callback = callback;
            this.world = world;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;

            this.ticks = 0;
            this.isComplete = false;
            this.isAtHead = false;
            this.headStayTicks = 0;
            this.currentStage = JUMP_TO_HEAD_STAGE;

            this.HEAD_STAY_TICKS = 40;

            this.fishName = parseFishName(fishEntity.getItemStack());
            this.fishSize = parseFishSize(fishEntity.getItemStack());

            createFloatingText();
        }

        private String parseFishName(ItemStack fishItem) {
            if (fishItem.hasItemMeta() && fishItem.getItemMeta().hasDisplayName()) {
                return fishItem.getItemMeta().getDisplayName();
            }
            return fishItem.getType().name();
        }

        private double parseFishSize(ItemStack fishItem) {
            if (fishItem.hasItemMeta() && fishItem.getItemMeta().hasLore()) {
                List<String> lore = fishItem.getItemMeta().getLore();
                for (String line : lore) {
                    try {
                        String cleanLine = ChatColor.stripColor(line).trim();
                        if (cleanLine.contains("大小: ") && cleanLine.contains("cm")) {
                            int sizeStart = cleanLine.indexOf("大小: ") + 4;
                            int cmPos = cleanLine.indexOf("cm");
                            if (sizeStart > 0 && cmPos > sizeStart) {
                                String sizeStr = cleanLine.substring(sizeStart, cmPos).trim();
                                return Double.parseDouble(sizeStr);
                            }
                        }
                        if (cleanLine.matches("\\d+(\\.\\d+)?")) {
                            return Double.parseDouble(cleanLine);
                        }
                        if (cleanLine.contains("cm")) {
                            String numStr = cleanLine.replaceAll("[^\\d.]", "");
                            if (!numStr.isEmpty()) {
                                return Double.parseDouble(numStr);
                            }
                        }
                    } catch (Exception e) {
                    }
                }
            }
            return 0.0;
        }

        private void createFloatingText() {
            Location textLoc = fishEntity.getLocation().add(0, 0.8, 0);
            floatingTextEntity = world.spawn(textLoc, ArmorStand.class);
            floatingTextEntity.setGravity(false);
            floatingTextEntity.setVisible(false);
            floatingTextEntity.setCustomNameVisible(true);
            floatingTextEntity.setCustomName(ChatColor.YELLOW + fishName + " (" + String.format("%.1f", fishSize) + "cm)");
            floatingTextEntity.setMarker(true);
        }

        private void updateFloatingTextLocation() {
            if (floatingTextEntity != null && floatingTextEntity.isValid()) {
                safeTeleport(floatingTextEntity, fishEntity.getLocation().add(0, 0.8, 0));
            }
        }

        private void removeFloatingText() {
            if (floatingTextEntity != null && floatingTextEntity.isValid()) {
                floatingTextEntity.remove();
            }
        }

        @Override
        public void run() {
            if (!this.isComplete && this.fishEntity.isValid()) {
                updateFloatingTextLocation();

                switch (currentStage) {
                    case JUMP_TO_HEAD_STAGE:
                        runJumpToHeadAnimation();
                        break;
                    case STAY_AT_HEAD_STAGE:
                        runStayAtHeadAnimation();
                        break;
                    case GO_TO_INVENTORY_STAGE:
                        runGoToInventoryAnimation();
                        break;
                }

                ++this.ticks;
            } else {
                finishAnimation();
            }
        }

        private void runJumpToHeadAnimation() {
            Location headLocation = player.getEyeLocation().add(0, 0.5, 0);

            fishEntity.setPickupDelay(Integer.MAX_VALUE);

            int baseDuration = config.getFishJumpToHeadBaseDuration();
            double distanceMultiplier = config.getFishJumpToHeadDistanceMultiplier();
            int maxDuration = config.getFishJumpToHeadMaxDuration();
            double initialJumpHeight = config.getFishJumpToHeadInitialJumpHeight();
            double easingFactor = config.getFishJumpToHeadEasingFactor();

            Location startLocation = new Location(world, startX, startY, startZ);
            double distance = startLocation.distance(headLocation);

            double totalDuration = Math.min(baseDuration + distance * distanceMultiplier, maxDuration);

            double progress = Math.min(ticks / totalDuration, 1.0);

            double smoothedProgress = 1 - Math.pow(1 - progress, easingFactor);

            double x = startX + (headLocation.getX() - startX) * smoothedProgress;
            double z = startZ + (headLocation.getZ() - startZ) * smoothedProgress;

            double startY = this.startY;
            double endY = headLocation.getY();
            double heightDifference = endY - startY;

            double midY = Math.max(startY, endY) + initialJumpHeight;
            double y = startY + heightDifference * smoothedProgress + initialJumpHeight * Math.sin(Math.PI * smoothedProgress);

            Location targetLoc = new Location(world, x, y, z);
            safeTeleport(fishEntity, targetLoc);

            Particle waterSplashParticle = getSafeParticle("WATER_SPLASH", null);
            if (waterSplashParticle != null) {
                spawnSafeParticle(world, waterSplashParticle, fishEntity.getLocation(), 1, 0.1, 0.1, 0.1, 0.05, null);
            }

            if (progress >= 0.95 || fishEntity.getLocation().distance(headLocation) < 0.5) {
                try {
                    java.lang.reflect.Method setGravityMethod = fishEntity.getClass().getMethod("setGravity", boolean.class);
                    setGravityMethod.invoke(fishEntity, false);
                } catch (Exception e) {
                }
                fishEntity.setVelocity(new Vector(0, 0, 0));
                safeTeleport(fishEntity, headLocation);

                currentStage = STAY_AT_HEAD_STAGE;

                Particle villagerHappyParticle = getSafeParticle("VILLAGER_HAPPY", null);
                if (villagerHappyParticle != null) {
                    spawnSafeParticle(world, villagerHappyParticle, headLocation, 10, 0.3, 0.3, 0.3, 0.1, null);
                }
            }
        }

        private void runStayAtHeadAnimation() {
            Location headLocation = player.getEyeLocation().add(0, 0.5, 0);

            fishEntity.setPickupDelay(Integer.MAX_VALUE);

            try {
                java.lang.reflect.Method setGravityMethod = fishEntity.getClass().getMethod("setGravity", boolean.class);
                setGravityMethod.invoke(fishEntity, false);
            } catch (Exception e) {
            }

            Particle villagerHappyParticle = getSafeParticle("VILLAGER_HAPPY", null);
            if (villagerHappyParticle != null) {
                spawnSafeParticle(world, villagerHappyParticle, fishEntity.getLocation(), 2, 0.2, 0.2, 0.2, 0.05, null);
            }

            Vector difference = headLocation.clone().subtract(fishEntity.getLocation()).toVector();

            Vector restoringForce = difference.clone().multiply(0.2);

            double floatSpeed = 0.05;
            double floatProgress = (ticks % 20) / 20.0;
            double floatOffset = 0.1 * Math.sin(floatProgress * Math.PI * 2);

            Location targetFloatLocation = headLocation.clone().add(0, floatOffset, 0);

            Vector floatDirection = targetFloatLocation.clone().subtract(fishEntity.getLocation()).toVector();
            Vector floatForce = floatDirection.multiply(0.15);

            double swaySpeed = 0.03;
            double swayProgress = (ticks % 30) / 30.0;
            double swayAmount = 0.05 * Math.sin(swayProgress * Math.PI * 2);

            float yaw = player.getLocation().getYaw();
            double radians = Math.toRadians(yaw);
            double swayX = Math.sin(radians) * swayAmount;
            double swayZ = Math.cos(radians) * swayAmount;

            Vector totalForce = new Vector(
                restoringForce.getX() + swayX * 0.2 + floatForce.getX(),
                restoringForce.getY() + floatForce.getY(),
                restoringForce.getZ() + swayZ * 0.2 + floatForce.getZ()
            );

            totalForce.add(new Vector(
                (random.nextDouble() - 0.5) * 0.01,
                (random.nextDouble() - 0.5) * 0.01,
                (random.nextDouble() - 0.5) * 0.01
            ));

            fishEntity.setVelocity(totalForce);

            if (ticks >= HEAD_STAY_TICKS) {
                try {
                    java.lang.reflect.Method setGravityMethod = fishEntity.getClass().getMethod("setGravity", boolean.class);
                    setGravityMethod.invoke(fishEntity, true);
                } catch (Exception e) {
                }

                currentStage = GO_TO_INVENTORY_STAGE;
                ticks = 0;
            }
        }

        private void runGoToInventoryAnimation() {
            fishEntity.setPickupDelay(Integer.MAX_VALUE);

            Location inventoryLocation = player.getEyeLocation();

            Location currentLocation = fishEntity.getLocation();

            double distanceToTarget = currentLocation.distance(inventoryLocation);

            double minDistanceThreshold = 0.1;

            if (distanceToTarget > minDistanceThreshold) {
                Vector direction = inventoryLocation.clone().subtract(currentLocation).toVector();
                direction.normalize();

                double baseSpeedFactor = 0.4;

                double distanceFactor = Math.min(distanceToTarget * 3, 1.0);
                double speedFactor = baseSpeedFactor * distanceFactor;

                speedFactor = Math.max(speedFactor, 0.1);

                double stageFactor = 1.0;
                if (ticks < 3) {
                    double accelerationProgress = (double) ticks / 3;
                    stageFactor = accelerationProgress * accelerationProgress * 1.5;
                } else if (distanceToTarget < 0.3) {
                    stageFactor = distanceToTarget * 3;
                }

                Vector velocity = direction.clone().multiply(speedFactor * stageFactor);

                velocity.add(new Vector(
                    (random.nextDouble() - 0.5) * 0.02,
                    (random.nextDouble() - 0.5) * 0.02,
                    (random.nextDouble() - 0.5) * 0.02
                ));

                fishEntity.setVelocity(velocity);

                try {
                    java.lang.reflect.Method setGravityMethod = fishEntity.getClass().getMethod("setGravity", boolean.class);
                    setGravityMethod.invoke(fishEntity, false);
                } catch (Exception e) {
                }

                int particleCount = 3;
                if (distanceToTarget < 0.5) {
                    particleCount = 5;
                }
                Particle itemCrackParticle = getSafeParticle("ITEM_CRACK", null);
                if (itemCrackParticle != null) {
                    spawnSafeParticle(world, itemCrackParticle, fishEntity.getLocation(), particleCount, 0.1, 0.1, 0.1, 0.1, fishEntity.getItemStack());
                }

                Particle cloudParticle = getSafeParticle("CLOUD", null);
                if (cloudParticle != null) {
                    spawnSafeParticle(world, cloudParticle, fishEntity.getLocation(), 2, 0.05, 0.05, 0.05, 0.01, null);
                }

                if (distanceToTarget < 0.3) {
                    try {
                        java.lang.reflect.Method setGlowingMethod = fishEntity.getClass().getMethod("setGlowing", boolean.class);
                        setGlowingMethod.invoke(fishEntity, ticks % 2 == 0);
                    } catch (Exception e) {
                    }
                } else if (ticks > 0) {
                    try {
                        java.lang.reflect.Method setGlowingMethod = fishEntity.getClass().getMethod("setGlowing", boolean.class);
                        setGlowingMethod.invoke(fishEntity, true);
                    } catch (Exception e) {
                    }
                }
            } else {
                fishEntity.setVelocity(new Vector(0, 0, 0));

                Particle itemCrackParticle = getSafeParticle("ITEM_CRACK", null);
                if (itemCrackParticle != null) {
                    spawnSafeParticle(world, itemCrackParticle, inventoryLocation, 8, 0.2, 0.2, 0.2, 0.1, fishEntity.getItemStack());
                }
                Particle cloudParticle = getSafeParticle("CLOUD", null);
                if (cloudParticle != null) {
                    spawnSafeParticle(world, cloudParticle, inventoryLocation, 10, 0.2, 0.2, 0.2, 0.1, null);
                }
                Particle villagerHappyParticle = getSafeParticle("VILLAGER_HAPPY", null);
                if (villagerHappyParticle != null) {
                    spawnSafeParticle(world, villagerHappyParticle, inventoryLocation, 5, 0.1, 0.1, 0.1, 0.1, null);
                }

                isComplete = true;
            }
        }

        private void finishAnimation() {
            this.cancel();

            removeFloatingText();

            plugin.getSoundManager().playSuccessSound(player.getLocation());

            Particle cloudParticle = getSafeParticle("CLOUD", null);
            if (cloudParticle != null) {
                spawnSafeParticle(world, cloudParticle, player.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1, null);
            }
            Particle villagerHappyParticle = getSafeParticle("VILLAGER_HAPPY", null);
            if (villagerHappyParticle != null) {
                spawnSafeParticle(world, villagerHappyParticle, player.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1, null);
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (fishEntity.isValid()) {
                        fishEntity.remove();
                    }
                    if (callback != null) {
                        callback.onAnimationComplete();
                    }
                }
            }.runTaskLater(plugin, 3L);
        }
    }
}
