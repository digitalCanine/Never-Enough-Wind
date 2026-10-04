package com.neverenoughwind.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EventParser {
    // minutes is -1 while the event is running
    public record Upcoming(String event, int minutes) {
        public boolean running() {
            return minutes < 0;
        }
    }

    // sidebar title of a running event: what is counted and the time left
    public record Title(String label, int secondsLeft) {
    }

    private static final Pattern HEADER = Pattern.compile("^(?<event>.+) in (?<minutes>\\d+) min$");
    private static final Pattern RUNNING = Pattern.compile("^(?<event>.+) in progress!$");
    private static final Pattern TITLE = Pattern.compile("^(?<label>.+) \\((?<minutes>\\d+):(?<seconds>\\d\\d)\\)$");

    private EventParser() {}

    // tab header like "Bait Event in 12 min" or "Beef Event in progress!". null when it says neither
    public static Upcoming header(String header) {
        if (header == null) return null;
        String line = header.replaceAll("§.", "").trim();
        Matcher m = HEADER.matcher(line);
        if (m.matches()) return new Upcoming(m.group("event"), Integer.parseInt(m.group("minutes")));
        Matcher r = RUNNING.matcher(line);
        return r.matches() ? new Upcoming(r.group("event"), -1) : null;
    }

    // "Fish Caught (04:31)". null when the title has no timer, so its not an event sidebar
    public static Title title(String title) {
        if (title == null) return null;
        Matcher m = TITLE.matcher(title.replaceAll("§.", "").trim());
        if (!m.matches()) return null;
        return new Title(m.group("label"), Integer.parseInt(m.group("minutes")) * 60 + Integer.parseInt(m.group("seconds")));
    }
}
