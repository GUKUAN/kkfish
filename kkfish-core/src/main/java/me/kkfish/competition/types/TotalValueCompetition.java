package me.kkfish.competition.types;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

import me.kkfish.competition.Competition;
import me.kkfish.competition.CompetitionConfig;
import me.kkfish.competition.CompetitionData;
import me.kkfish.managers.Compete;

public class TotalValueCompetition extends Competition {

    public TotalValueCompetition(Compete manager, CompetitionConfig config, int duration) {
        super(manager, config, duration);
    }

    @Override
    public void recordCatch(Player player, String fishName, double value) {
        if (config.hasFishList() && !config.getFishList().containsKey(fishName)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        CompetitionData data = playerData.computeIfAbsent(playerId, k -> new CompetitionData(playerId, player.getName()));
        data.addValue(value);
    }

    @Override
    public void sortData(List<CompetitionData> dataList) {
        dataList.sort((a, b) -> Double.compare(b.getTotalValue(), a.getTotalValue()));
    }

    @Override
    public String getPlayerScoreValue(CompetitionData data) {
        return String.format("%.2f", data.getTotalValue());
    }

    @Override
    protected String getResultMessageFormat() {
        return manager.getPlugin().getMessageManager().getMessage("competition_result_total_value", "&eRank %rank%: %player% - Total value: %value%");
    }
}
