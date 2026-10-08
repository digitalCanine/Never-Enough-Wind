package com.neverenoughwind.state;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

// kills and deaths the player saw go by in chat, per real name. names and two numbers, nothing else
public final class KillCounts {
    // name as last written -> {kills, deaths}
    private final Map<String, int[]> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public void kill(String name) {
        counts.computeIfAbsent(name, n -> new int[2])[0]++;
    }

    public void death(String name) {
        counts.computeIfAbsent(name, n -> new int[2])[1]++;
    }

    // null when nothing was seen for that player
    public int[] of(String name) {
        return name == null ? null : counts.get(name);
    }

    public Map<String, int[]> all() {
        return counts;
    }

    public void putAll(Map<String, int[]> saved) {
        if (saved == null) return;
        saved.forEach((name, c) -> {
            if (name != null && c != null && c.length == 2) counts.put(name, c);
        });
    }

    // "12 kills, 4 deaths"
    public static String text(int[] c) {
        return String.format(Locale.ROOT, "%d %s, %d %s", c[0], c[0] == 1 ? "kill" : "kills", c[1], c[1] == 1 ? "death" : "deaths");
    }
}
