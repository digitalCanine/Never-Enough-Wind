package com.neverenoughwind.state;

import com.neverenoughwind.parse.EventParser;

import java.util.List;

// decides when an upcoming event is worth a reminder: once at each of these minute marks
public final class Reminder {
    public static final List<Integer> MARKS = List.of(60, 30, 15, 5, 1);

    private String lastEvent;
    private int lastMinutes = Integer.MAX_VALUE;

    // true when this header reading just reached a mark we havent shown yet
    public boolean reached(EventParser.Upcoming next) {
        if (next == null || next.running()) {
            lastEvent = null;
            lastMinutes = Integer.MAX_VALUE;
            return false;
        }
        boolean sameEvent = next.event().equals(lastEvent);
        int before = sameEvent ? lastMinutes : Integer.MAX_VALUE;
        lastEvent = next.event();
        lastMinutes = next.minutes();
        // the first reading after login never counts, only a drop onto a mark
        if (!sameEvent || next.minutes() >= before) return false;
        return MARKS.contains(next.minutes());
    }
}
