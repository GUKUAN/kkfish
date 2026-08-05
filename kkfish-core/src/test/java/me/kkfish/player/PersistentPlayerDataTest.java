package me.kkfish.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistentPlayerDataTest {

    @Test
    void recordCatchIncrementsCountAndMaxSize() {
        PersistentPlayerData data = new PersistentPlayerData();

        data.recordCatch("cod", 12.5);
        data.recordCatch("cod", 20.0);
        data.recordCatch("cod", 15.0);

        PersistentPlayerData.FishRecordData record = data.getFishRecords().get("cod");
        assertEquals(3, record.getCount());
        assertEquals(20.0, record.getMaxSize());
    }

    @Test
    void successCountEqualsAttemptsMinusFails() {
        PersistentPlayerData data = new PersistentPlayerData();

        data.recordAttempt();
        data.recordAttempt();
        data.recordAttempt();
        data.recordFail();

        assertEquals(3, data.getTotalAttempts());
        assertEquals(1, data.getFailCount());
        assertEquals(2, data.getSuccessCount());
    }

    @Test
    void snapshotCopiesStatsAndRecords() {
        PersistentPlayerData data = new PersistentPlayerData();
        data.recordCatch("salmon", 30.0);
        data.recordAttempt();
        data.recordAttempt();
        data.recordFail();

        PersistentPlayerData snap = data.snapshot();

        assertEquals(1, snap.getFishRecords().get("salmon").getCount());
        assertEquals(30.0, snap.getFishRecords().get("salmon").getMaxSize());
        assertEquals(2, snap.getTotalAttempts());
        assertEquals(1, snap.getFailCount());

        snap.recordAttempt();
        assertEquals(2, data.getTotalAttempts(), "snapshot 修改不应影响原数据");
    }

    @Test
    void clearResetsAllStats() {
        PersistentPlayerData data = new PersistentPlayerData();
        data.recordCatch("cod", 10.0);
        data.recordAttempt();
        data.recordFail();
        data.setLanguage("zh");

        data.clear();

        assertEquals(0, data.getFishRecords().size());
        assertEquals(0, data.getTotalAttempts());
        assertEquals(0, data.getFailCount());
        assertEquals(null, data.getLanguage());
    }
}
