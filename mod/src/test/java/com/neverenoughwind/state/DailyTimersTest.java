package com.neverenoughwind.state;

import com.neverenoughwind.state.DailyTimers.Kind;
import com.neverenoughwind.state.DailyTimers.State;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyTimersTest {
    private static final long HOUR = 3_600_000L;

    @Test
    void unknownUntilClaimedThenRunsADay() {
        DailyTimers t = new DailyTimers();
        assertEquals(State.UNKNOWN, t.state(Kind.BOSS, 0));
        t.claimed(Kind.BOSS, 1000);
        assertEquals(State.RUNNING, t.state(Kind.BOSS, 1000 + 23 * HOUR));
        assertEquals(State.READY, t.state(Kind.BOSS, 1000 + 24 * HOUR));
        assertEquals(HOUR, t.msLeft(Kind.BOSS, 1000 + 23 * HOUR));
    }

    @Test
    void theServersOwnCountdownWins() {
        DailyTimers t = new DailyTimers();
        t.sync(Kind.WEEKLY, 1188, 0);
        assertEquals("19 h 48 min", DailyTimers.span(t.msLeft(Kind.WEEKLY, 0)));
        assertEquals(State.READY, t.state(Kind.WEEKLY, 1188 * 60_000L));
    }

    @Test
    void readyIsAnnouncedOnceAndOnlyForTimersSeenRunning() {
        DailyTimers t = new DailyTimers();
        // ran out while the game was closed: nothing to announce
        t.restore(java.util.Map.of("daily", 500L));
        assertTrue(t.justReady(1000).isEmpty());
        t.claimed(Kind.WILD, 1000);
        assertTrue(t.justReady(2000).isEmpty());
        assertEquals(List.of(Kind.WILD), t.justReady(1000 + 24 * HOUR));
        assertTrue(t.justReady(1000 + 25 * HOUR).isEmpty());
    }

    @Test
    void resetMakesItReadyWithoutAnnouncing() {
        DailyTimers t = new DailyTimers();
        t.claimed(Kind.WILD, 0);
        t.justReady(10);
        t.reset(Kind.WILD, 20);
        assertEquals(State.READY, t.state(Kind.WILD, 20));
        assertTrue(t.justReady(30).isEmpty());
    }

    @Test
    void spansReadNaturally() {
        assertEquals("under a minute", DailyTimers.span(59_000));
        assertEquals("12 min", DailyTimers.span(12 * 60_000L));
        assertEquals("5 h 12 min", DailyTimers.span(5 * HOUR + 12 * 60_000L));
        assertEquals("6 d 23 h", DailyTimers.span(7 * 24 * HOUR - 60_000L));
    }

    @Test
    void savesAndComesBack() {
        DailyTimers t = new DailyTimers();
        t.claimed(Kind.BOSS, 0);
        DailyTimers u = new DailyTimers();
        u.restore(t.export());
        assertEquals(State.RUNNING, u.state(Kind.BOSS, HOUR));
        assertEquals(State.UNKNOWN, u.state(Kind.DAILY, HOUR));
    }
}
