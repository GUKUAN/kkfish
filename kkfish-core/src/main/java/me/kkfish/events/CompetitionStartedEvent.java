package me.kkfish.events;

/**
 * 竞赛开始事件。
 *
 * <p>由 {@code CompetitionService} 在竞赛启动时发布。
 * 订阅者：{@code ScoreboardService}（创建记分板）、{@code MessageService}（广播开始消息）。</p>
 */
public final class CompetitionStartedEvent extends DomainEvent {

    private final String competitionId;
    private final String competitionName;
    private final long durationSeconds;

    public CompetitionStartedEvent(String competitionId, String competitionName, long durationSeconds) {
        this.competitionId = competitionId;
        this.competitionName = competitionName;
        this.durationSeconds = durationSeconds;
    }

    public String getCompetitionId() {
        return competitionId;
    }

    public String getCompetitionName() {
        return competitionName;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }
}
