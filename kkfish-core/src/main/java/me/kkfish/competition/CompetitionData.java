package me.kkfish.competition;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

public class CompetitionData {
    private final UUID playerUUID;
    private final String playerName;
    private final AtomicInteger totalAmount = new AtomicInteger(0);
    private final DoubleAdder totalValue = new DoubleAdder();
    private final AtomicLong maxSingleValue = new AtomicLong(0);
    private final DoubleAdder totalPoints = new DoubleAdder();

    public CompetitionData(UUID playerUUID, String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
    }

    public void addAmount() {
        totalAmount.incrementAndGet();
    }

    public void addValue(double value) {
        totalValue.add(value);
        maxSingleValue.updateAndGet(current ->
            value > Double.longBitsToDouble(current) ? Double.doubleToLongBits(value) : current
        );
    }

    public void addPoints(double points) {
        totalPoints.add(points);
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public int getTotalAmount() {
        return totalAmount.get();
    }

    public double getTotalValue() {
        return totalValue.sum();
    }

    public double getMaxSingleValue() {
        return Double.longBitsToDouble(maxSingleValue.get());
    }

    public double getTotalPoints() {
        return totalPoints.sum();
    }
}
