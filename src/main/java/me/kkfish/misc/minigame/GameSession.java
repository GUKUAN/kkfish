package me.kkfish.misc.minigame;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import me.kkfish.kkfish;
import me.kkfish.managers.Config;
import me.kkfish.fishing.WaterType;

public class GameSession extends BukkitRunnable {
    
    public final Player player;
    public final Location hookLocation;
    public final WaterType waterType;
    private double difficulty;
    public final String targetFish;
    private final double fishSizeMin;
    private final double fishSizeMax;
    private double movementAmplitude;
    public final double fishSize;
    public final String fishLevel;
    private org.bukkit.scheduler.BukkitTask task;
    
    private double greenBarPos = 0.5;
    private double greenBarVel = 0;
    private double fishPos = 0.5;
    private double progress;
    private int invincibleTicks;
    public boolean isSuccess = false;
    private double greenBarWidth = 0.3;
    
    private int moveDir = 0;
    private int moveTick = 0;
    private int cooldown = 0;
    private double speed = 0.0;
    private double acceleration = 0.0;
    private int targetPos = 0;
    private boolean isMoving = false;
    private boolean isDashing = false;
    private int dashTimer = 0;
    private int behaviorType = 0;
    private int behaviorChangeTimer = 0;
    private int behaviorDuration = 0;
    private int lastDashTime = 0;
    private int dashCooldown;
    
    private int[] dirHistory = new int[10];
    private int historyIndex = 0;
    private double[] dangerZone = new double[10];
    private long lastDangerUpdate = 0;
    private int[] positionHistory = new int[5];
    
    private final int MAX_COOLDOWN = 40;
    private final int MIN_COOLDOWN = 10;
    private final double BASE_SPEED = 0.03;
    private final double ACCELERATION_FACTOR = 0.1;
    private final double DECELERATION_FACTOR = 0.15;
    
    private String baitName;
    private double rareFishBonus = 1.0;
    private double sizeBonus = 1.0;
    private double biteRateBonus = 1.0;
    
    private final kkfish plugin;
    private final Config config;
    
    public GameSession(kkfish plugin, Player player, Location hookLocation, WaterType waterType, double chargePercentage, String rodName, String baitName, double rareFishChance) {
        this.plugin = plugin;
        this.config = plugin.getCustomConfig();
        this.player = player;
        this.hookLocation = hookLocation;
        this.waterType = waterType;
        this.difficulty = 1.0 - (chargePercentage / 100.0 * 0.3);
        this.baitName = baitName;
        
        this.invincibleTicks = 60;
        this.dashCooldown = 80;
        
        applyBaitEffects();
        
        this.rareFishBonus *= (1.0 + rareFishChance);
        
        double rodDifficulty = config.getRodDifficulty(rodName);
        this.difficulty *= rodDifficulty;
        
        this.targetFish = getRandomFish();
        
        org.bukkit.configuration.file.FileConfiguration fishConfig = config.getFishConfig();
        this.fishSizeMin = fishConfig.getDouble("fish." + targetFish + ".min-size", 1.0);
        this.fishSizeMax = fishConfig.getDouble("fish." + targetFish + ".max-size", 3.0);
        this.movementAmplitude = fishConfig.getDouble("fish." + targetFish + ".movement-amplitude", 1.0);
        
        double baseSize = fishSizeMin + Math.random() * (fishSizeMax - fishSizeMin);
        this.fishSize = Math.min(baseSize * sizeBonus, fishSizeMax);
        
        this.fishLevel = getRandomFishLevelWithBonus(targetFish);
        
        this.fishPos = 0.1 + Math.random() * 0.8;
        this.targetPos = (int)Math.round(fishPos * 10);
        
        Random random = new Random();
        this.moveDir = random.nextBoolean() ? -1 : 1;
        this.isMoving = true;
        this.moveTick = 0;
        
        int initialProgress = plugin.getCustomConfig().getMainConfig().getInt("fishing-settings.initial-progress", 10);
        this.progress = initialProgress / 100.0;
    }
    
    private void applyBaitEffects() {
        if (baitName == null) {
            return;
        }
        
        List<String> effects = config.getBaitEffects(baitName);
        
        for (String effectType : effects) {
            double value = config.getBaitEffectValueByName(baitName, effectType);
            
            if (effectType.equals("rare")) {
                rareFishBonus = 1.0 + value;
            } else if (effectType.equals("size")) {
                sizeBonus = 1.0 + value;
            } else if (effectType.equals("bite")) {
                biteRateBonus = 1.0 + value;
            }
        }
        
        if (effects.size() <= 1 && config.getBaitEffectValue(baitName) > 0) {
            String oldEffect = config.getBaitEffect(baitName);
            double oldValue = config.getBaitEffectValue(baitName);
            
            if (oldEffect.equals("rare")) {
                rareFishBonus = 1.0 + oldValue;
            } else if (oldEffect.equals("size")) {
                sizeBonus = 1.0 + oldValue;
            } else if (oldEffect.equals("bite")) {
                biteRateBonus = 1.0 + oldValue;
            }
        }
    }
    
    private String getRandomFish() {
        List<String> fishList = plugin.getCustomConfig().getAllFishNames();
        if (fishList.isEmpty()) {
            return plugin.getMessageManager().getMessageWithoutPrefix("fish_unknown", "未知鱼");
        }
        
        if (rareFishBonus > 1.0 && Math.random() < 0.1) {
            List<String> rareFishList = new java.util.ArrayList<>();
            for (String fish : fishList) {
                int rarity = plugin.getCustomConfig().getFishRarity(fish);
                if (rarity >= 3) {
                    rareFishList.add(fish);
                }
            }
            if (!rareFishList.isEmpty()) {
                Random random = new Random();
                return rareFishList.get(random.nextInt(rareFishList.size()));
            }
        }
        
        java.util.LinkedHashMap<String, Double> fishWeights = new java.util.LinkedHashMap<>();
        double totalWeight = 0;
        
        for (String fish : fishList) {
            int rarity = plugin.getCustomConfig().getFishRarity(fish);
            
            double weight = 1.0;
            switch (rarity) {
                case 1:
                    weight = 10.0;
                    break;
                case 2:
                    weight = 5.0;
                    break;
                case 3:
                    weight = 2.0;
                    break;
                case 4:
                    weight = 1.0;
                    break;
                case 5:
                    weight = 0.5;
                    break;
            }
            
            if (rareFishBonus > 1.0) {
                if (rarity >= 3) {
                    weight *= rareFishBonus;
                }
            }
            
            fishWeights.put(fish, weight);
            totalWeight += weight;
        }
        
        double randomValue = Math.random() * totalWeight;
        double currentWeight = 0;
        
        for (java.util.Map.Entry<String, Double> entry : fishWeights.entrySet()) {
            currentWeight += entry.getValue();
            if (randomValue < currentWeight) {
                return entry.getKey();
            }
        }
        
        Random random = new Random();
        return fishList.get(random.nextInt(fishList.size()));
    }
    
    private String getRandomFishLevelWithBonus(String fishName) {
        String level = config.getRandomFishLevel(fishName, this.player);
        
        if (rareFishBonus > 1.0 && Math.random() < 0.05) {
            if (level.contains("common")) {
                return "rare";
            } else if (level.contains("rare")) {
                return "epic";
            } else if (level.contains("epic")) {
                return "legendary";
            }
        }
        
        return level;
    }
    
    public void start() {
        this.task = this.runTaskTimer(plugin, 0, 1);
    }
    
    public void onPlayerInteraction() {
        greenBarVel += 0.035;
    }
    
    @Override
    public void run() {
        updateGreenBar();
        updateFishMovement();
        updateProgress();
        displayGameUI();
        
        if (progress <= 0) {
            if (task != null) {
                task.cancel();
            }
            endGame(false);
        } else if (progress >= 1) {
            if (task != null) {
                task.cancel();
            }
            endGame(true);
        }
    }
    
    private void updateGreenBar() {
        double gravity = 0.007;
        greenBarVel -= gravity;
        
        double maxSpeed = 0.05;
        if (Math.abs(greenBarVel) > maxSpeed) {
            greenBarVel = Math.signum(greenBarVel) * maxSpeed;
        }
        
        double frictionFactor;
        if (greenBarPos < 0.3) {
            frictionFactor = 0.83;
        } else if (greenBarPos < 0.7) {
            frictionFactor = 0.88;
        } else {
            frictionFactor = 0.93;
        }
        
        greenBarVel *= frictionFactor;
        
        if (Math.abs(greenBarVel) < 0.001) {
            greenBarVel = 0;
        }
        
        greenBarPos += greenBarVel;
        
        if (greenBarPos < 0) {
            greenBarPos = 0;
            greenBarVel = Math.abs(greenBarVel) * 0.3;
        } else if (greenBarPos > 1) {
            greenBarPos = 1;
            greenBarVel = -Math.abs(greenBarVel) * 0.3;
        }
    }
    
    private void updateFishMovement() {
        int rarity = plugin.getCustomConfig().getFishRarity(targetFish);
        int currentGridPos = (int)Math.round(fishPos * 10);
        
        updateBehaviorAndDangerZones(currentGridPos, rarity);
        
        lastDashTime++;
        
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        
        if (isMoving) {
            moveTick++;
            
            if (isDashing) {
                dashTimer--;
                if (dashTimer <= 0) {
                    isDashing = false;
                    speed = BASE_SPEED;
                    lastDashTime = 0;
                }
            }
            
            int currentPos = (int)(fishPos * 10);
            int target = targetPos;
            
            int direction = currentPos < target ? 1 : -1;
            
            double targetPosDouble = (double)target / 10.0;
            double remainingDistance = Math.abs(targetPosDouble - fishPos);
            
            if (remainingDistance > 0.15) {
                speed += acceleration * ACCELERATION_FACTOR;
                speed = Math.min(speed, BASE_SPEED * 1.5);
            } else {
                speed *= (1 - DECELERATION_FACTOR);
                speed = Math.max(speed, BASE_SPEED * 0.3);
            }
            
            if (isDashing) {
                double dashSpeedFactor = remainingDistance > 0.15 ? 1.0 : remainingDistance / 0.15;
                speed = BASE_SPEED * 2.0 * movementAmplitude * dashSpeedFactor;
            }
            
            double adjustedSpeed = speed * movementAmplitude;
            
            double actualMoveStep = Math.min(adjustedSpeed, remainingDistance);
            
            fishPos += direction * actualMoveStep;
            
            fishPos = Math.max(0, Math.min(1, fishPos));
            
            if (Math.abs(fishPos - targetPosDouble) < 0.001) {
                fishPos = targetPosDouble;
                isMoving = false;
                
                addPositionToHistory(currentGridPos);
                
                setCooldownByBehavior(behaviorType, rarity);
            }
        } else {
            if (moveTick >= 0) {
                decideNewMovement(currentGridPos, rarity);
                moveTick = 0;
            }
        }
        
        boolean isInGreenBar = Math.abs(greenBarPos - fishPos) < greenBarWidth / 2;
        if (isInGreenBar) {
            double escapeChance = getEscapeChanceByRarity(rarity);
            if (Math.random() < escapeChance) {
                escapeFromGreenBar(currentGridPos, rarity);
            }
        }
    }
    
    private void updateBehaviorAndDangerZones(int currentGridPos, int rarity) {
        long now = System.currentTimeMillis();
        if (now - lastDangerUpdate > 1000) {
            for (int i = 0; i < dangerZone.length; i++) {
                dangerZone[i] = Math.max(0, dangerZone[i] - 0.05);
            }
            lastDangerUpdate = now;
        }
        
        boolean inGreenBar = Math.abs(greenBarPos - fishPos) < greenBarWidth / 2;
        if (inGreenBar) {
            dangerZone[currentGridPos] = Math.min(1.0, dangerZone[currentGridPos] + 0.1);
        }
        
        behaviorChangeTimer++;
        if (behaviorChangeTimer >= behaviorDuration) {
            changeBehavior(rarity);
        }
    }
    
    private void changeBehavior(int rarity) {
        double[] behaviorProbs = {0.4, 0.3, 0.2, 0.1};
        
        if (rarity >= 3) {
            behaviorProbs[1] += 0.2;
            behaviorProbs[2] += 0.1;
            behaviorProbs[0] -= 0.3;
        } else if (rarity >= 2) {
            behaviorProbs[1] += 0.1;
            behaviorProbs[0] -= 0.1;
        }
        
        double rand = Math.random();
        double cumulativeProb = 0;
        for (int i = 0; i < behaviorProbs.length; i++) {
            cumulativeProb += behaviorProbs[i];
            if (rand < cumulativeProb) {
                behaviorType = i;
                break;
            }
        }
        
        behaviorDuration = 20 + new Random().nextInt(40);
        behaviorChangeTimer = 0;
    }
    
    private void setCooldownByBehavior(int behavior, int rarity) {
        int baseCooldown = 0;
        
        switch (behavior) {
            case 0: baseCooldown = MIN_COOLDOWN + new Random().nextInt(15); break;
            case 1: baseCooldown = MIN_COOLDOWN + new Random().nextInt(20); break;
            case 2: baseCooldown = MIN_COOLDOWN + new Random().nextInt(10); break;
            case 3: baseCooldown = MIN_COOLDOWN + new Random().nextInt(25); break;
        }
        
        if (rarity >= 3) {
            baseCooldown = Math.max(MIN_COOLDOWN, baseCooldown);
        }
        
        cooldown = baseCooldown;
    }
    
    private double getEscapeChanceByRarity(int rarity) {
        double baseChance = 0.3;
        
        if (rarity == 2) baseChance = 0.4;
        else if (rarity >= 3) baseChance = 0.5;
        
        return baseChance;
    }
    
    private void addPositionToHistory(int pos) {
        for (int i = positionHistory.length - 1; i > 0; i--) {
            positionHistory[i] = positionHistory[i-1];
        }
        positionHistory[0] = pos;
    }
    
    private boolean isPositionInRecentHistory(int pos) {
        for (int recentPos : positionHistory) {
            if (recentPos == pos) {
                return true;
            }
        }
        return false;
    }
    
    private int getOppositeDirection(int direction) {
        return direction == -1 ? 1 : -1;
    }
    
    private void decideNewMovement(int currentGridPos, int rarity) {
        speed = BASE_SPEED;
        
        int newDir = 0;
        int moveAmount = 0;
        
        switch (behaviorType) {
            case 0:
                newDir = new Random().nextBoolean() ? 1 : -1;
                moveAmount = 1 + new Random().nextInt(3);
                break;
            
            case 1:
                newDir = findSafestDirection(currentGridPos, rarity);
                if (newDir == 0) {
                    newDir = new Random().nextBoolean() ? 1 : -1;
                }
                
                moveAmount = 1 + new Random().nextInt(2);
                break;
            
            case 2:
                newDir = new Random().nextBoolean() ? 1 : -1;
                moveAmount = 2 + new Random().nextInt(3);
                
                if (new Random().nextDouble() < 0.3 && lastDashTime >= dashCooldown) {
                    startDash(rarity);
                }
                break;
            
            case 3:
                if (new Random().nextDouble() < 0.4) {
                    moveAmount = 0;
                    newDir = 0;
                } else {
                    newDir = new Random().nextBoolean() ? 1 : -1;
                    moveAmount = 1;
                }
                
                speed = BASE_SPEED * 0.7;
                break;
        }
        
        if (behaviorType != 3 && !isDashing && lastDashTime >= dashCooldown && new Random().nextDouble() < 0.2) {
            startDash(rarity);
        }
        
        int newTargetPos = currentGridPos;
        if (moveAmount > 0 && newDir != 0) {
            newTargetPos = currentGridPos + newDir * moveAmount;
        }
        
        newTargetPos = Math.max(0, Math.min(9, newTargetPos));
        
        targetPos = newTargetPos;
        moveDir = newDir;
        isMoving = moveAmount > 0;
        
        addMoveToHistory(newDir);
    }
    
    private void addMoveToHistory(int direction) {
        dirHistory[historyIndex] = direction;
        historyIndex = (historyIndex + 1) % dirHistory.length;
    }
    
    private int findSafestDirection(int currentGridPos, int rarity) {
        double leftDanger = 0;
        double rightDanger = 0;
        int leftCount = 0;
        int rightCount = 0;
        
        for (int i = 1; i <= 3; i++) {
            int pos = currentGridPos - i;
            if (pos >= 0) {
                leftDanger += dangerZone[pos];
                leftCount++;
            }
        }
        
        for (int i = 1; i <= 3; i++) {
            int pos = currentGridPos + i;
            if (pos <= 9) {
                rightDanger += dangerZone[pos];
                rightCount++;
            }
        }
        
        leftDanger = leftCount > 0 ? leftDanger / leftCount : 1.0;
        rightDanger = rightCount > 0 ? rightDanger / rightCount : 1.0;
        
        if (Math.abs(leftDanger - rightDanger) < 0.1) {
            return 0;
        } else if (leftDanger < rightDanger) {
            return -1;
        } else {
            return 1;
        }
    }
    
    private void startDash(int rarity) {
        isDashing = true;
        dashTimer = 10 + new Random().nextInt(10);
        
        speed = BASE_SPEED * 2.0 * movementAmplitude;
        
        if (rarity >= 3) {
            dashTimer += 5;
        }
    }
    
    private void escapeFromGreenBar(int currentGridPos, int rarity) {
        int escapeDir;
        
        double greenBarCenter = greenBarPos;
        if (fishPos < greenBarCenter) {
            escapeDir = -1;
        } else if (fishPos > greenBarCenter) {
            escapeDir = 1;
        } else {
            escapeDir = new Random().nextBoolean() ? 1 : -1;
        }
        
        int escapeAmount;
        if (rarity >= 3) {
            escapeAmount = 3 + new Random().nextInt(3);
        } else if (rarity >= 2) {
            escapeAmount = 2 + new Random().nextInt(3);
        } else {
            escapeAmount = 1 + new Random().nextInt(3);
        }
        
        int newGridPos = currentGridPos + (escapeDir * escapeAmount);
        
        newGridPos = Math.max(0, Math.min(9, newGridPos));
        
        if (dangerZone[newGridPos] > 0.7) {
            escapeDir = getOppositeDirection(escapeDir);
            newGridPos = currentGridPos + (escapeDir * escapeAmount);
            newGridPos = Math.max(0, Math.min(9, newGridPos));
        }
        
        targetPos = newGridPos;
        isMoving = true;
        moveDir = escapeDir;
        moveTick = 0;
        
        cooldown = 0;
        
        double dashChance = 0.5;
        if (rarity >= 3) {
            dashChance = 0.7;
        } else if (rarity >= 2) {
            dashChance = 0.6;
        }
        
        if (new Random().nextDouble() < dashChance) {
            startDash(rarity);
        }
    }
    
    private void updateProgress() {
        MinigameManager minigameManager = plugin.getMinigameManager();
        String rodName = minigameManager.getRodNameByPlayer(player);
        
        double baseWidth = 0.15;
        int floatAreaSize = config.getRodFloatAreaSize(rodName);
        greenBarWidth = baseWidth + (floatAreaSize - 3) * 0.03;
        greenBarWidth = Math.max(0.08, Math.min(0.4, greenBarWidth));
        
        boolean enableRarityImpact = plugin.getCustomConfig().getMainConfig().getBoolean("fishing-settings.progress-bar.rarity-impact.enabled", true);
        double raritySlowdownFactor = 1.0;
        
        if (enableRarityImpact) {
            int fishRarity = plugin.getCustomConfig().getFishRarity(targetFish);
            double slowdownPerLevel = plugin.getCustomConfig().getMainConfig().getDouble("fishing-settings.progress-bar.rarity-impact.slowdown-per-rarity-level", 0.15);
            double minSpeedRatio = plugin.getCustomConfig().getMainConfig().getDouble("fishing-settings.progress-bar.rarity-impact.min-increase-speed-ratio", 0.45);
            
            raritySlowdownFactor = 1.0 - (fishRarity - 1) * slowdownPerLevel;
            raritySlowdownFactor = Math.max(minSpeedRatio, raritySlowdownFactor);
        }
        
        boolean isFishInGreenBar = Math.abs(greenBarPos - fishPos) < greenBarWidth / 2;
        
        if (invincibleTicks > 0) {
            invincibleTicks--;
            if (isFishInGreenBar) {
                double increaseSpeed = plugin.getCustomConfig().getMainConfig().getDouble("fishing-settings.progress-bar.increase-speed", 0.015);
                progress += increaseSpeed * (1.0 / difficulty) * raritySlowdownFactor;
                if (progress > 1) progress = 1;
            }
            return;
        }
        
        if (isFishInGreenBar) {
            double increaseSpeed = plugin.getCustomConfig().getMainConfig().getDouble("fishing-settings.progress-bar.increase-speed", 0.015);
            progress += increaseSpeed * (1.0 / difficulty) * raritySlowdownFactor;
            if (progress > 1) progress = 1;
        } else {
            double decreaseSpeed = plugin.getCustomConfig().getMainConfig().getDouble("fishing-settings.progress-bar.decrease-speed", 0.01);
            progress -= decreaseSpeed * difficulty;
            if (progress < 0) progress = 0;
        }
    }
    
    private void displayGameUI() {
        String stylePath;
        if (waterType == WaterType.LAVA) {
            stylePath = "styles.lava";
        } else if (waterType == WaterType.VOID) {
            stylePath = "styles.void";
        } else {
            stylePath = "styles.default";
        }
        String progressBarChar = ChatColor.translateAlternateColorCodes('&', plugin.getCustomConfig().getMainConfig().getString(stylePath + ".progress-char", "&9="));
        String progressBarEmptyChar = ChatColor.translateAlternateColorCodes('&', plugin.getCustomConfig().getMainConfig().getString(stylePath + ".progress-bar-empty-char", "&7-"));
        String greenBarChar = ChatColor.translateAlternateColorCodes('&', plugin.getCustomConfig().getMainConfig().getString(stylePath + ".green-bar-char", "&a|"));
        String greenBarEdgeChar = ChatColor.translateAlternateColorCodes('&', plugin.getCustomConfig().getMainConfig().getString(stylePath + ".green-bar-edge-char", "&2|"));
        String backgroundChar = ChatColor.translateAlternateColorCodes('&', plugin.getCustomConfig().getMainConfig().getString(stylePath + ".background-char", "&7|"));
        String fishIndicatorChar = ChatColor.translateAlternateColorCodes('&', plugin.getCustomConfig().getMainConfig().getString(stylePath + ".fish-indicator-char", "&9|||"));
        
        StringBuilder greenBar = new StringBuilder("[");
        int totalBars = 30;
        int greenBarCenter = (int)(greenBarPos * totalBars);
        int fishPosInBar = (int)(fishPos * totalBars);
        
        fishPosInBar = Math.min(fishPosInBar, totalBars - 1);
        
        String[] barSegments = new String[totalBars];
        
        int greenBarLength = (int)(greenBarWidth * totalBars);
        int edgeLeft = greenBarCenter - greenBarLength / 2;
        int edgeRight = greenBarCenter + greenBarLength / 2;
        for (int i = 0; i < totalBars; i++) {
            if (Math.abs(i - greenBarCenter) <= greenBarLength / 2) {
                if (i == edgeLeft || i == edgeRight) {
                    barSegments[i] = greenBarEdgeChar;
                } else {
                    barSegments[i] = greenBarChar;
                }
            } else {
                barSegments[i] = backgroundChar;
            }
        }
        
        if (Math.abs(fishPosInBar - edgeLeft) <= 1 || Math.abs(fishPosInBar - edgeRight) <= 1) {
            barSegments[fishPosInBar] = fishIndicatorChar;
        } else {
            barSegments[fishPosInBar] = fishIndicatorChar;
        }
        
        for (String segment : barSegments) {
            greenBar.append(segment);
        }
        greenBar.append("]");
        
        StringBuilder progressBar = new StringBuilder("[");
        int progressLength = (int)(progress * 20);
        
        for (int i = 0; i < 20; i++) {
            progressBar.append(i < progressLength ? progressBarChar : progressBarEmptyChar);
        }
        progressBar.append("]");
        
        player.sendTitle(greenBar.toString(), progressBar.toString(), 0, 10, 0);
    }
    
    private String processColorConfig(String colorConfig, String defaultColorName) {
        if (colorConfig == null) {
            return ChatColor.valueOf(defaultColorName).toString();
        }
        
        if (colorConfig.startsWith("&")) {
            return ChatColor.translateAlternateColorCodes('&', colorConfig);
        }
        
        try {
            return ChatColor.valueOf(colorConfig.toUpperCase()).toString();
        } catch (Exception e) {
            plugin.getCustomConfig().debugLog("无效的颜色配置: " + colorConfig + ", 使用默认值: " + defaultColorName);
            return ChatColor.valueOf(defaultColorName).toString();
        }
    }
    
    private void endGame(boolean success) {
        this.isSuccess = success;
        
        MinigameManager minigameManager = plugin.getMinigameManager();
        minigameManager.endGame(player);
    }
    
    public double getActualFishValue() {
        double baseValue = plugin.getCustomConfig().getFishConfig().getDouble("fish." + targetFish + ".value", 10.0);
        
        double minSize = plugin.getCustomConfig().getFishConfig().getDouble("fish." + targetFish + ".min-size", 20.0);
        double maxSize = plugin.getCustomConfig().getFishConfig().getDouble("fish." + targetFish + ".max-size", 60.0);
        
        double sizeMultiplier = fishSize / maxSize;
        
        double rarityMultiplier = 1.0;
        String rarityName = plugin.getCustomConfig().getRarityNameByLevel(fishLevel);
        rarityMultiplier = plugin.getCustomConfig().getRarityValueMultiplier(rarityName);
        
        double valueBonus = 1.0;
        if (player != null) {
            String hookMaterial = plugin.getDB().getPlayerHookMaterial(player.getUniqueId().toString());
            switch (hookMaterial.toLowerCase()) {
                case "iron":
                    valueBonus = 1.1;
                    break;
                case "gold":
                    valueBonus = 1.2;
                    break;
                case "diamond":
                    valueBonus = 1.3;
                    break;
            }
        }
        
        double finalValue = baseValue * sizeMultiplier * rarityMultiplier * valueBonus;
        
        if (plugin.isRealisticSeasonsEnabled() && plugin.getCustomConfig().isSeasonalPriceFluctuationEnabled()) {
            String currentSeason = plugin.getCurrentSeason();
            if (currentSeason != null) {
                double seasonalMultiplier = plugin.getCustomConfig().getSeasonalPriceMultiplier(currentSeason);
                finalValue *= seasonalMultiplier;
                
                double baseFluctuation = plugin.getCustomConfig().getBasePriceFluctuation();
                double randomFluctuation = 1.0 + (new Random().nextDouble() - 0.5) * 2 * baseFluctuation;
                finalValue *= randomFluctuation;
            }
        }
        
        return Math.round(finalValue * 100) / 100.0;
    }
    
    public ItemStack createFishItem() {
        return plugin.getFish().createFishItem(targetFish, false, this.player, this.fishSize, this.fishLevel);
    }
    
    public boolean isCancelled() {
        return task == null || task.isCancelled();
    }
    
    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}