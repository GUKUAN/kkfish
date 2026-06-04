package me.kkfish.competition.types;

import me.kkfish.competition.Competition;
import me.kkfish.competition.CompetitionConfig;
import me.kkfish.competition.CompetitionData;
import me.kkfish.managers.Compete;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PointsOnlyCompetition extends Competition {

    private final Map<String, Double> fishList;
    private final double pointMultiplier;

    public PointsOnlyCompetition(Compete manager, CompetitionConfig config, int duration) {
        super(manager, config, duration);
        this.fishList = config.getFishList();
        this.pointMultiplier = config.getPointMultiplier();
    }

    @Override
    public void recordCatch(Player player, String fishName, double value) {
        if (config.hasFishList() && !fishList.containsKey(fishName)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        CompetitionData data = playerData.computeIfAbsent(playerId, k -> new CompetitionData(playerId, player.getName()));
        
        double points = calculatePoints(fishName, value);
        data.addPoints(points);
    }

    private double calculatePoints(String fishName, double value) {
        if (config.hasFishList()) {
            if (!fishList.containsKey(fishName)) {
                return 0.0;
            }
            double basePoints = fishList.get(fishName);
            return basePoints * pointMultiplier;
        }
        return value * pointMultiplier;
    }

    @Override
    public void sortData(List<CompetitionData> dataList) {
        dataList.sort((a, b) -> Double.compare(b.getTotalPoints(), a.getTotalPoints()));
    }

    @Override
    public String getPlayerScoreValue(CompetitionData data) {
        return String.format("%.2f", data.getTotalPoints());
    }

    @Override
    protected String getResultMessageFormat() {
        return manager.getPlugin().getMessageManager().getMessage("competition_result_points", "&eRank %rank%: %player% - Points: %value%");
    }
}
