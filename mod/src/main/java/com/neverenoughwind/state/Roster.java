package com.neverenoughwind.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// who is in which clan. starts from the shipped list, then follows what the player sees in game
public final class Roster {
    public static final List<String> RANKS = List.of("leader", "officer", "member", "recruit");

    public record Membership(String tag, String rank) {
    }

    // one clan as /clan find lists it. checked = when it was looked up, in ms
    public record Clan(String tag, String name, String leader, List<String> officers, List<String> members,
                       List<String> recruits, long checked) {
    }

    // lowercase tag -> clan
    private final Map<String, Clan> clans = new LinkedHashMap<>();
    // lowercase player name -> their clans, highest rank first
    private final Map<String, List<Membership>> byPlayer = new HashMap<>();
    // lowercase player name -> the tag their own chat line showed, which beats a stale roster entry
    private final Map<String, String> settled = new HashMap<>();

    private static String key(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    public int size() {
        return clans.size();
    }

    public List<Clan> clans() {
        return List.copyOf(clans.values());
    }

    public Map<String, String> settled() {
        return Map.copyOf(settled);
    }

    public Clan clan(String tag) {
        return tag == null ? null : clans.get(key(tag));
    }

    // adds or replaces a clan, but never with an older lookup than the one we have
    public boolean put(Clan clan) {
        Clan old = clans.get(key(clan.tag()));
        if (old != null && old.checked() > clan.checked()) return false;
        clans.put(key(clan.tag()), clan);
        reindex();
        return true;
    }

    public void putAll(List<Clan> list) {
        for (Clan clan : list) {
            Clan old = clans.get(key(clan.tag()));
            if (old == null || old.checked() <= clan.checked()) clans.put(key(clan.tag()), clan);
        }
        reindex();
    }

    // a real name seen in chat with this tag. only counts when the roster already lists them there,
    // a nick that isnt anyone's real name changes nothing
    public boolean settle(String name, String tag) {
        if (name == null || tag == null) return false;
        List<Membership> all = byPlayer.get(key(name));
        if (all == null || all.size() < 2) return false;
        for (Membership m : all) {
            if (m.tag().equalsIgnoreCase(tag)) {
                String before = settled.put(key(name), key(tag));
                return !key(tag).equals(before);
            }
        }
        return false;
    }

    public void settleAll(Map<String, String> saved) {
        saved.forEach((name, tag) -> settled.put(key(name), key(tag)));
    }

    // the clans to show for a player: one when its clear, several when the roster lists them in more than one
    public List<Membership> of(String player) {
        if (player == null) return List.of();
        List<Membership> all = byPlayer.get(key(player));
        if (all == null) return List.of();
        String pick = settled.get(key(player));
        if (pick != null) {
            for (Membership m : all) {
                if (key(m.tag()).equals(pick)) return List.of(m);
            }
        }
        return all;
    }

    private void reindex() {
        byPlayer.clear();
        for (Clan c : clans.values()) {
            add(c.leader(), c.tag(), "leader");
            for (String n : c.officers()) add(n, c.tag(), "officer");
            for (String n : c.members()) add(n, c.tag(), "member");
            for (String n : c.recruits()) add(n, c.tag(), "recruit");
        }
        for (List<Membership> list : byPlayer.values()) {
            list.sort((a, b) -> Integer.compare(RANKS.indexOf(a.rank()), RANKS.indexOf(b.rank())));
        }
    }

    private void add(String player, String tag, String rank) {
        if (player == null || player.isBlank()) return;
        byPlayer.computeIfAbsent(key(player), k -> new ArrayList<>()).add(new Membership(tag, rank));
    }
}
