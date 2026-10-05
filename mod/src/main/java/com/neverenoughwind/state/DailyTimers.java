package com.neverenoughwind.state;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// when each daily can be done again. times are passed in so tests can pick them
public final class DailyTimers {
    private static final long HOUR = 3_600_000L;
    private static final java.util.regex.Pattern MINUTES = java.util.regex.Pattern.compile(": (\\d{1,6}) min");

    public enum Kind {
        BOSS("Daily boss", "Daily boss is ready", 24 * HOUR),
        DAILY("/daily", "/daily is ready", 24 * HOUR),
        WEEKLY("/weekly", "/weekly is ready", 7 * 24 * HOUR),
        WILD("Wild key", "Wild key is ready", 24 * HOUR),
        // every vote site has its own 24 h wait. the timer is for the first one that comes back
        VOTE("Votes", "You can vote again", 24 * HOUR);

        public final String label, readyText;
        public final long cooldown;

        Kind(String label, String readyText, long cooldown) {
            this.label = label;
            this.readyText = readyText;
            this.cooldown = cooldown;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // unknown = the mod has never seen it claimed, so it cant say anything
    public enum State { UNKNOWN, RUNNING, READY }

    private final Map<Kind, Long> readyAt = new EnumMap<>(Kind.class);
    // timers seen running this session, so "ready" is only announced for ones that ran out while playing
    private final Set<Kind> watched = EnumSet.noneOf(Kind.class);

    public void claimed(Kind kind, long now) {
        readyAt.put(kind, now + kind.cooldown);
    }

    // the server said how long is left
    public void sync(Kind kind, int minutesLeft, long now) {
        readyAt.put(kind, now + minutesLeft * 60_000L);
    }

    // it can be done right now
    public void reset(Kind kind, long now) {
        readyAt.put(kind, now);
        watched.remove(kind);
    }

    public State state(Kind kind, long now) {
        Long at = readyAt.get(kind);
        return at == null ? State.UNKNOWN : at > now ? State.RUNNING : State.READY;
    }

    public long msLeft(Kind kind, long now) {
        Long at = readyAt.get(kind);
        return at == null ? 0 : Math.max(0, at - now);
    }

    // the timers that ran out since the last call
    public List<Kind> justReady(long now) {
        List<Kind> out = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            State s = state(kind, now);
            if (s == State.RUNNING) watched.add(kind);
            else if (s == State.READY && watched.remove(kind)) out.add(kind);
        }
        return out;
    }

    // the /votetime rows, "site: 1438 min" each: the shortest wait among them, -1 when there is none
    public static int leastMinutes(String rows) {
        int least = -1;
        if (rows == null) return least;
        java.util.regex.Matcher m = MINUTES.matcher(rows);
        while (m.find()) {
            int min = Integer.parseInt(m.group(1));
            if (least < 0 || min < least) least = min;
        }
        return least;
    }

    // "2 d 3 h", "5 h 12 min", "12 min", "under a minute"
    public static String span(long ms) {
        long min = ms / 60_000L;
        if (min < 1) return "under a minute";
        long d = min / 1440, h = min % 1440 / 60, m = min % 60;
        if (d > 0) return d + " d " + h + " h";
        if (h > 0) return h + " h " + m + " min";
        return m + " min";
    }

    // for the file: kind -> time it is ready, in ms since 1970
    public Map<String, Long> export() {
        Map<String, Long> out = new HashMap<>();
        readyAt.forEach((k, v) -> out.put(k.key(), v));
        return out;
    }

    public void restore(Map<String, Long> saved) {
        readyAt.clear();
        watched.clear();
        if (saved == null) return;
        for (Kind kind : Kind.values()) {
            Long at = saved.get(kind.key());
            if (at != null) readyAt.put(kind, at);
        }
    }
}
