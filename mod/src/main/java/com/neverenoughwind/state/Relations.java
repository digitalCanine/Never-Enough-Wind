package com.neverenoughwind.state;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// how the player stands with each clan. tags are compared without case
public final class Relations {
    public enum Kind { OWN, ALLY, ENEMY, NEUTRAL }

    private final Set<String> own = new HashSet<>(), ally = new HashSet<>(), enemy = new HashSet<>();
    // the clan whose clan chat we can read. always counts as own, on top of the list
    private String detected;

    public void set(List<String> own, List<String> ally, List<String> enemy) {
        this.own.clear();
        this.ally.clear();
        this.enemy.clear();
        own.forEach(t -> this.own.add(key(t)));
        ally.forEach(t -> this.ally.add(key(t)));
        enemy.forEach(t -> this.enemy.add(key(t)));
    }

    public void detected(String tag) {
        detected = tag == null ? null : key(tag);
    }

    public Kind of(String tag) {
        if (tag == null) return Kind.NEUTRAL;
        String k = key(tag);
        if (k.equals(detected) || own.contains(k)) return Kind.OWN;
        if (ally.contains(k)) return Kind.ALLY;
        if (enemy.contains(k)) return Kind.ENEMY;
        return Kind.NEUTRAL;
    }

    private static String key(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
