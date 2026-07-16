package me.kkfish.managers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.kkfish.kkfish;
import me.kkfish.fishing.WaterType;

public class FishAnimationService {

    private static final double FISH_Y_OFFSET = -1.5;

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
            if (callback != null) callback.onAnimationComplete();
            return;
        }

        World world = player.getWorld();

        ItemStack cleanFishItem = new ItemStack(fishItem.getType(), 1);
        ItemMeta meta = cleanFishItem.getItemMeta();
        if (meta != null) {
            if (fishItem.getItemMeta().hasDisplayName()) meta.setDisplayName(fishItem.getItemMeta().getDisplayName());
            if (fishItem.getItemMeta().hasLore()) meta.setLore(fishItem.getItemMeta().getLore());
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            cleanFishItem.setItemMeta(meta);
        }

        ArmorStand fishEntity = world.spawn(fishStartLocation, ArmorStand.class);
        fishEntity.setVisible(false);
        fishEntity.setGravity(false);
        fishEntity.setMarker(true);
        fishEntity.getEquipment().setHelmet(cleanFishItem);

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
                fishStartLocation.getX(), fishStartLocation.getZ(), 0.3, fishStartLocation.getY(), 1.5, world, 0.6).runTaskTimer(plugin, 0, 1);
    }

    static void safeTeleport(org.bukkit.entity.Entity entity, org.bukkit.Location loc) {
        me.kkfish.utils.NmsAdapter.teleportEntityAsync(entity, loc);
    }

    Particle getSafeParticle(String particleName, Particle fallback) {
        if (particleCache.containsKey(particleName)) {
            Particle cached = particleCache.get(particleName);
            return cached != null ? cached : fallback;
        }
        Particle result = me.kkfish.utils.XSeriesUtil.getParticle(particleName);
        particleCache.put(particleName, result);
        return result != null ? result : fallback;
    }

    void spawnSafeParticle(World world, Particle particle, Location location, int count, double spreadX, double spreadY, double spreadZ, double extra, Object data) {
        me.kkfish.utils.XSeriesUtil.spawnParticleWithData(location, particle.name(), count, spreadX, spreadY, spreadZ, extra, data);
    }

    private class OldFishAnimationTask extends BukkitRunnable {
        private final ArmorStand fishEntity;
        private final Player player;
        private final Fish.AnimationCompleteCallback callback;
        private final World world;
        private final double startX, startY, startZ;
        private final ItemStack displayedItem;

        private int ticks;
        private boolean isComplete;
        private float fishYaw;
        private final int JUMP_TO_HEAD_STAGE = 1, STAY_AT_HEAD_STAGE = 2, GO_TO_INVENTORY_STAGE = 3;
        private int currentStage;
        private ArmorStand floatingTextEntity;
        private final String fishName;
        private final double fishSize;

        public OldFishAnimationTask(int maxTicks, ArmorStand fishEntity,
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
            this.displayedItem = fishEntity.getEquipment().getHelmet();

            this.ticks = 0;
            this.isComplete = false;
            this.currentStage = JUMP_TO_HEAD_STAGE;

            this.fishName = parseFishName(displayedItem);
            this.fishSize = parseFishSize(displayedItem);

            createFloatingText();
        }

        private String parseFishName(ItemStack fishItem) {
            if (fishItem != null && fishItem.hasItemMeta() && fishItem.getItemMeta().hasDisplayName())
                return fishItem.getItemMeta().getDisplayName();
            return fishItem != null ? fishItem.getType().name() : "";
        }

        private double parseFishSize(ItemStack fishItem) {
            if (fishItem == null || !fishItem.hasItemMeta() || !fishItem.getItemMeta().hasLore()) return 0.0;
            List<String> lore = fishItem.getItemMeta().getLore();
            for (String line : lore) {
                try {
                    String cleanLine = ChatColor.stripColor(line).trim();
                    if (cleanLine.contains("大小: ") && cleanLine.contains("cm")) {
                        int sizeStart = cleanLine.indexOf("大小: ") + 4;
                        int cmPos = cleanLine.indexOf("cm");
                        if (sizeStart > 0 && cmPos > sizeStart)
                            return Double.parseDouble(cleanLine.substring(sizeStart, cmPos).trim());
                    }
                    if (cleanLine.matches("\\d+(\\.\\d+)?")) return Double.parseDouble(cleanLine);
                    if (cleanLine.contains("cm")) {
                        String numStr = cleanLine.replaceAll("[^\\d.]", "");
                        if (!numStr.isEmpty()) return Double.parseDouble(numStr);
                    }
                } catch (Exception ignored) {}
            }
            return 0.0;
        }

        private void createFloatingText() {
            Location textLoc = fishEntity.getLocation().add(0, 2.3, 0);
            floatingTextEntity = world.spawn(textLoc, ArmorStand.class);
            floatingTextEntity.setGravity(false);
            floatingTextEntity.setVisible(false);
            floatingTextEntity.setCustomNameVisible(true);
            floatingTextEntity.setCustomName(ChatColor.YELLOW + fishName + " (" + String.format("%.1f", fishSize) + "cm)");
            floatingTextEntity.setMarker(true);
        }

        private void updateFloatingTextLocation() {
            if (floatingTextEntity != null && floatingTextEntity.isValid())
                safeTeleport(floatingTextEntity, fishEntity.getLocation().add(0, 2.3, 0));
        }

        private void removeFloatingText() {
            if (floatingTextEntity != null && floatingTextEntity.isValid()) floatingTextEntity.remove();
        }

        @Override
        public void run() {
            if (!this.isComplete && this.fishEntity.isValid()) {
                fishYaw += 15;
                if (fishYaw >= 360) fishYaw -= 360;
                fishEntity.setRotation(fishYaw, 0);

                updateFloatingTextLocation();
                switch (currentStage) {
                    case JUMP_TO_HEAD_STAGE: runJumpToHeadAnimation(); break;
                    case STAY_AT_HEAD_STAGE: runStayAtHeadAnimation(); break;
                    case GO_TO_INVENTORY_STAGE: runGoToInventoryAnimation(); break;
                }
                ++this.ticks;
            } else {
                finishAnimation();
            }
        }

        private void runJumpToHeadAnimation() {
            Location headLocation = player.getEyeLocation().add(0, 0.5, 0);

            int baseDuration = config.getFishJumpToHeadBaseDuration();
            double distanceMultiplier = config.getFishJumpToHeadDistanceMultiplier();
            int maxDuration = config.getFishJumpToHeadMaxDuration();
            double initialJumpHeight = config.getFishJumpToHeadInitialJumpHeight();

            double dx = headLocation.getX() - startX;
            double dy = headLocation.getY() - startY;
            double dz = headLocation.getZ() - startZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            double totalDuration = Math.min(baseDuration + distance * distanceMultiplier, maxDuration);
            double t = Math.min(ticks / totalDuration, 1.0);

            double mx = (startX + headLocation.getX()) / 2.0;
            double mz = (startZ + headLocation.getZ()) / 2.0;
            double peakY = Math.max(startY, headLocation.getY()) + initialJumpHeight + distance * 0.4;

            double omt = 1.0 - t;
            double microFloat = 0.04 * Math.sin(ticks * 0.7);
            double x = omt * omt * startX + 2 * omt * t * mx + t * t * headLocation.getX();
            double y = omt * omt * startY + 2 * omt * t * peakY + t * t * headLocation.getY() + microFloat + FISH_Y_OFFSET;
            double z = omt * omt * startZ + 2 * omt * t * mz + t * t * headLocation.getZ();

            fishEntity.teleport(new Location(world, x, y, z));

            Particle waterSplash = getSafeParticle("WATER_SPLASH", null);
            if (waterSplash != null) spawnSafeParticle(world, waterSplash, fishEntity.getLocation(), 1, 0.1, 0.1, 0.1, 0.05, null);

            if (t >= 0.95) {
                fishEntity.teleport(headLocation.clone().add(0, FISH_Y_OFFSET, 0));
                currentStage = STAY_AT_HEAD_STAGE;
                ticks = 0;
                Particle happy = getSafeParticle("VILLAGER_HAPPY", null);
                if (happy != null) spawnSafeParticle(world, happy, headLocation, 10, 0.3, 0.3, 0.3, 0.1, null);
            }
        }

        private void runStayAtHeadAnimation() {
            Location headLocation = player.getEyeLocation().add(0, 0.5, 0);

            Particle happy = getSafeParticle("VILLAGER_HAPPY", null);
            if (happy != null) spawnSafeParticle(world, happy, fishEntity.getLocation(), 2, 0.2, 0.2, 0.2, 0.05, null);

            double floatOffset = 0.1 * Math.sin((ticks % 20) / 20.0 * Math.PI * 2);
            double swayAmount = 0.05 * Math.sin((ticks % 30) / 30.0 * Math.PI * 2);
            double radians = Math.toRadians(player.getLocation().getYaw());

            fishEntity.teleport(headLocation.clone().add(
                Math.sin(radians) * swayAmount * 0.2,
                floatOffset + FISH_Y_OFFSET,
                Math.cos(radians) * swayAmount * 0.2));

            if (ticks >= 40) { currentStage = GO_TO_INVENTORY_STAGE; ticks = 0; }
        }

        private void runGoToInventoryAnimation() {
            Location eyeLoc = player.getEyeLocation();
            Location curLoc = fishEntity.getLocation();

            double dist = curLoc.distance(eyeLoc);
            if (dist <= 0.1) { isComplete = true; return; }

            Vector dir = eyeLoc.clone().subtract(curLoc).toVector().normalize();
            double speed = Math.max(0.4 * Math.min(dist * 3, 1.0), 0.1);
            double stage = 1.0;
            if (ticks < 3) { double ap = ticks / 3.0; stage = ap * ap * 1.5; }
            else if (dist < 0.3) stage = dist * 3;

            double moveDist = speed * stage;
            if (dist <= moveDist) fishEntity.teleport(eyeLoc);
            else fishEntity.teleport(curLoc.clone().add(dir.clone().multiply(moveDist)));

            int pCount = dist < 0.5 ? 5 : 3;
            Particle crack = getSafeParticle("ITEM_CRACK", null);
            if (crack != null && displayedItem != null)
                spawnSafeParticle(world, crack, fishEntity.getLocation(), pCount, 0.1, 0.1, 0.1, 0.1, displayedItem);
            Particle cloud = getSafeParticle("CLOUD", null);
            if (cloud != null) spawnSafeParticle(world, cloud, fishEntity.getLocation(), 2, 0.05, 0.05, 0.05, 0.01, null);
        }

        private void finishAnimation() {
            this.cancel();
            removeFloatingText();
            plugin.getSoundManager().playSuccessSound(player.getLocation());

            Particle cloud = getSafeParticle("CLOUD", null);
            if (cloud != null) spawnSafeParticle(world, cloud, player.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1, null);
            Particle happy = getSafeParticle("VILLAGER_HAPPY", null);
            if (happy != null) spawnSafeParticle(world, happy, player.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1, null);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (fishEntity.isValid()) fishEntity.remove();
                    if (callback != null) callback.onAnimationComplete();
                }
            }.runTaskLater(plugin, 3L);
        }
    }
}
