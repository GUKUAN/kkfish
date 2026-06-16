package me.kkfish.misc.minigame;

import java.util.Random;

import me.kkfish.kkfish;
import me.kkfish.managers.Config;

/**
 * 小游戏鱼类移动模拟器：负责鱼的位置、行为、危险区、冲刺、逃跑等移动逻辑。
 * 从 GameSession 抽取，职责单一。
 *
 * <p>状态由本类持有，GameSession 通过 getter 读取 fishPos 供渲染和进度判定使用。</p>
 */
public class FishMovementSimulator {

    private final kkfish plugin;
    private final Config config;
    private final Random random;
    private final String targetFish;
    private final double movementAmplitude;

    // 鱼位置（0~1）
    private double fishPos = 0.5;
    private int targetPos = 5;

    // 移动状态
    private int moveDir = 0;
    private int moveTick = 0;
    private int cooldown = 0;
    private double speed = 0.0;
    private double acceleration = 0.0;
    private boolean isMoving = false;
    private boolean isDashing = false;
    private int dashTimer = 0;
    private int behaviorType = 0;
    private int behaviorChangeTimer = 0;
    private int behaviorDuration = 0;
    private int lastDashTime = 0;
    private int dashCooldown;

    // 历史/危险区
    private int[] dirHistory = new int[10];
    private int historyIndex = 0;
    private double[] dangerZone = new double[10];
    private long lastDangerUpdate = 0;
    private int[] positionHistory = new int[5];

    // 常量
    private static final int MAX_COOLDOWN = 40;
    private static final int MIN_COOLDOWN = 10;
    private static final double BASE_SPEED = 0.03;
    private static final double ACCELERATION_FACTOR = 0.1;
    private static final double DECELERATION_FACTOR = 0.15;

    public FishMovementSimulator(kkfish plugin, String targetFish, double movementAmplitude, Random random, double initialFishPos) {
        this.plugin = plugin;
        this.config = plugin.getCustomConfig();
        this.targetFish = targetFish;
        this.movementAmplitude = movementAmplitude;
        this.random = random;
        this.fishPos = initialFishPos;
        this.targetPos = (int) Math.round(fishPos * 10);
        this.moveDir = random.nextBoolean() ? -1 : 1;
        this.isMoving = true;
        this.dashCooldown = 80;
    }

    /**
     * 主移动更新：根据绿条位置和稀有度更新鱼的位置。
     */
    public void update(double greenBarPos, double greenBarWidth) {
        int rarity = config.getFishRarity(targetFish);
        int currentGridPos = (int) Math.round(fishPos * 10);
        currentGridPos = Math.min(currentGridPos, 9);

        updateBehaviorAndDangerZones(currentGridPos, rarity, greenBarPos, greenBarWidth);

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

            int currentPos = (int) (fishPos * 10);
            int target = targetPos;

            int direction = currentPos < target ? 1 : -1;

            double targetPosDouble = (double) target / 10.0;
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
            if (random.nextDouble() < escapeChance) {
                escapeFromGreenBar(currentGridPos, rarity, greenBarPos);
            }
        }
    }

    private void updateBehaviorAndDangerZones(int currentGridPos, int rarity, double greenBarPos, double greenBarWidth) {
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

        double rand = random.nextDouble();
        double cumulativeProb = 0;
        for (int i = 0; i < behaviorProbs.length; i++) {
            cumulativeProb += behaviorProbs[i];
            if (rand < cumulativeProb) {
                behaviorType = i;
                break;
            }
        }

        behaviorDuration = 20 + random.nextInt(40);
        behaviorChangeTimer = 0;
    }

    private void setCooldownByBehavior(int behavior, int rarity) {
        int baseCooldown = 0;

        switch (behavior) {
            case 0: baseCooldown = MIN_COOLDOWN + random.nextInt(15); break;
            case 1: baseCooldown = MIN_COOLDOWN + random.nextInt(20); break;
            case 2: baseCooldown = MIN_COOLDOWN + random.nextInt(10); break;
            case 3: baseCooldown = MIN_COOLDOWN + random.nextInt(25); break;
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
            positionHistory[i] = positionHistory[i - 1];
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
                newDir = random.nextBoolean() ? 1 : -1;
                moveAmount = 1 + random.nextInt(3);
                break;

            case 1:
                newDir = findSafestDirection(currentGridPos);
                if (newDir == 0) {
                    newDir = random.nextBoolean() ? 1 : -1;
                }
                moveAmount = 1 + random.nextInt(2);
                break;

            case 2:
                newDir = random.nextBoolean() ? 1 : -1;
                moveAmount = 2 + random.nextInt(3);

                if (random.nextDouble() < 0.3 && lastDashTime >= dashCooldown) {
                    startDash(rarity);
                }
                break;

            case 3:
                if (random.nextDouble() < 0.4) {
                    moveAmount = 0;
                    newDir = 0;
                } else {
                    newDir = random.nextBoolean() ? 1 : -1;
                    moveAmount = 1;
                }
                speed = BASE_SPEED * 0.7;
                break;
        }

        if (behaviorType != 3 && !isDashing && lastDashTime >= dashCooldown && random.nextDouble() < 0.2) {
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

    private int findSafestDirection(int currentGridPos) {
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
        dashTimer = 10 + random.nextInt(10);

        speed = BASE_SPEED * 2.0 * movementAmplitude;

        if (rarity >= 3) {
            dashTimer += 5;
        }
    }

    private void escapeFromGreenBar(int currentGridPos, int rarity, double greenBarPos) {
        int escapeDir;

        double greenBarCenter = greenBarPos;
        if (fishPos < greenBarCenter) {
            escapeDir = -1;
        } else if (fishPos > greenBarCenter) {
            escapeDir = 1;
        } else {
            escapeDir = random.nextBoolean() ? 1 : -1;
        }

        int escapeAmount;
        if (rarity >= 3) {
            escapeAmount = 3 + random.nextInt(3);
        } else if (rarity >= 2) {
            escapeAmount = 2 + random.nextInt(3);
        } else {
            escapeAmount = 1 + random.nextInt(3);
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

        if (random.nextDouble() < dashChance) {
            startDash(rarity);
        }
    }

    public double getFishPos() {
        return fishPos;
    }
}
