package me.kkfish.events;

import java.util.List;
import java.util.Map;

/**
 * 竞赛结束事件。
 *
 * <p>由 {@code CompetitionService} 在竞赛结束时发布。
 * 订阅者：{@code ScoreboardService}（移除记分板）、{@code RewardService}（发放竞赛奖励）。</p>
 */
public final class CompetitionEndedEvent extends DomainEvent {

    private final String competitionId;
    private final String competitionName;
    /** 排名结果：玩家名 → 得分（已排序，第一名在前）。 */
    private final Map<String, Double> rankings;

    public CompetitionEndedEvent(String competitionId, String competitionName, Map<String, Double> rankings) {
        this.competitionId = competitionId;
        this.competitionName = competitionName;
        this.rankings = rankings;
    }

    public String getCompetitionId() {
        return competitionId;
    }

    public String getCompetitionName() {
        return competitionName;
    }

    public Map<String, Double> getRankings() {
        return rankings;
    }
}
