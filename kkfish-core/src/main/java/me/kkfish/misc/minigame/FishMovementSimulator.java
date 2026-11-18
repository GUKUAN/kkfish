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
    private final double activity;

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

    // 鱼的落点范围：0~10 共 11 个点，正好铺满 0%~100% 的底栏
    private static final int FIELD_EDGE = 10;

    // 历史/危险区
    private int[] dirHistory = new int[10];
    private int historyIndex = 0;
    private double[] dangerZone = new double[FIELD_EDGE + 1];
    private long lastDangerUpdate = 0;
    private int[] positionHistory = new int[5];

    // 常量
    private static final int MAX_COOLDOWN = 40;
    private static final int MIN_COOLDOWN = 10;
    private static final double BASE_SPEED = 0.03;
    private static final double ACCELERATION_FACTOR = 0.1;
    private static final double DECELERATION_FACTOR = 0.15;

    public FishMovementSimulator(kkfish plugin, String targetFish, double movementAmplitude, double activity, Random random, double initialFishPos) {
        this.plugin = plugin;
        this.config = plugin.getCustomConfig();
        this.targetFish = targetFish;
        this.movementAmplitude = movementAmplitude;
        this.activity = activity;
        this.random = random;
        this.fishPos = initialFishPos;
        this.targetPos = (int) Math.round(fishPos * FIELD_EDGE);
        this.moveDir = random.nextBoolean() ? -1 : 1;
        this.isMoving = true;
        this.dashCooldown = scaleIntervalByActivity(80, this.activity);
    }

    static int scaleIntervalByActivity(int baseTicks, double activity) {
        return Math.max(1, (int) Math.round(baseTicks / activity));
    }

    /**
     * 主移动更新：根据绿条位置和稀有度更新鱼的位置。
     */
    public void update(double greenBarPos, double greenBarWidth) {
        update(greenBarPos, greenBarWidth, 1);
    }

    public void update(double greenBarPos, double greenBarWidth, int fishIndicatorWidth) {
        int rarity = config.getFishRarity(targetFish);
        int currentGridPos = (int) Math.round(fishPos * FIELD_EDGE);
        currentGridPos = Math.max(0, Math.min(currentGridPos, FIELD_EDGE));

        updateBehaviorAndDangerZones(currentGridPos, rarity, greenBarPos, greenBarWidth, fishIndicatorWidth);

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

            int currentPos = (int) (fishPos * FIELD_EDGE);
            int target = targetPos;

            int direction = currentPos < target ? 1 : -1;

            double targetPosDouble = (double) target / FIELD_EDGE;
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

        boolean isInGreenBar = MinigameField.inBar(fishPos, greenBarPos, greenBarWidth, fishIndicatorWidth);
        if (isInGreenBar) {
            double escapeChance = getEscapeChanceByRarity(rarity);
            if (random.nextDouble() < escapeChance) {
                // 用移动后的实际位置算逃逸起点，避免这一 tick 的位移让目标格跑偏
                int escapeGridPos = (int) Math.round(fishPos * FIELD_EDGE);
                escapeGridPos = Math.max(0, Math.min(escapeGridPos, FIELD_EDGE));
                escapeFromGreenBar(escapeGridPos, rarity, greenBarPos);
            }
        }
    }

    private void updateBehaviorAndDangerZones(int currentGridPos, int rarity, double greenBarPos, double greenBarWidth, int fishIndicatorWidth) {
        long now = System.currentTimeMillis();
        if (now - lastDangerUpdate > 1000) {
            for (int i = 0; i < dangerZone.length; i++) {
                dangerZone[i] = Math.max(0, dangerZone[i] - 0.05);
            }
            lastDangerUpdate = now;
        }

        boolean inGreenBar = MinigameField.inBar(fishPos, greenBarPos, greenBarWidth, fishIndicatorWidth);
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

        behaviorDuration = scaleIntervalByActivity(20 + random.nextInt(40), activity);
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

        cooldown = scaleIntervalByActivity(baseCooldown, activity);
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
            if (newTargetPos < 0 || newTargetPos > FIELD_EDGE) {
                // 撞到边界就掉头，否则目标被夹回原地会让鱼贴着边缘反复选路
                newDir = getOppositeDirection(newDir);
                newTargetPos = currentGridPos + newDir * moveAmount;
            }
        }

        newTargetPos = Math.max(0, Math.min(FIELD_EDGE, newTargetPos));

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
            if (pos <= FIELD_EDGE) {
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

        int newGridPos = targetWithinField(currentGridPos, escapeDir, escapeAmount);

        if (dangerZone[newGridPos] > 0.7) {
            // 往反方向再躲一次；反方向也出界时保持当前目标
            int dangerFreeDir = newGridPos > currentGridPos ? -1 : 1;
            int dangerFreePos = targetWithinField(currentGridPos, dangerFreeDir, escapeAmount);
            if (dangerFreePos != currentGridPos) {
                escapeDir = dangerFreeDir;
                newGridPos = dangerFreePos;
            }
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

    /**
     * 计算落在底栏内的目标格，越界时朝反方向走。
     * 返回值保证在 0~FIELD_EDGE 之间，避免鱼在边缘原地打转。
     */
    private int targetWithinField(int currentGridPos, int direction, int amount) {
        int target = currentGridPos + direction * amount;
        if (target < 0 || target > FIELD_EDGE) {
            target = currentGridPos - direction * amount;
        }
        return Math.max(0, Math.min(FIELD_EDGE, target));
    }

    public double getFishPos() {
        return fishPos;
    }
}
