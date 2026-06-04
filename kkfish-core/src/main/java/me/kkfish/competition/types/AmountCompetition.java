package me.kkfish.competition.types;

import me.kkfish.competition.Competition;
import me.kkfish.competition.CompetitionConfig;
import me.kkfish.competition.CompetitionData;
import me.kkfish.managers.Compete;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AmountCompetition extends Competition {

    public AmountCompetition(Compete manager, CompetitionConfig config, int duration) {
        super(manager, config, duration);
    }

    @Override
    public void recordCatch(Player player, String fishName, double value) {
        if (config.hasFishList() && !config.getFishList().containsKey(fishName)) {
            return;
        }
        UUID playerId = player.getUniqueId();
        CompetitionData data = playerData.computeIfAbsent(playerId, k -> new CompetitionData(playerId, player.getName()));
        data.addAmount();
    }

    @Override
    public void sortData(List<CompetitionData> dataList) {
        dataList.sort((a, b) -> Integer.compare(b.getTotalAmount(), a.getTotalAmount()));
    }

    @Override
    public String getPlayerScoreValue(CompetitionData data) {
        return String.valueOf(data.getTotalAmount());
    }

    @Override
    protected String getResultMessageFormat() {
        return manager.getPlugin().getMessageManager().getMessage("competition_result_amount", "&eRank %rank%: %player% - Fish caught: %value%");
    }
}
