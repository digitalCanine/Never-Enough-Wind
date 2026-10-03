package com.neverenoughwind.state;

import com.neverenoughwind.parse.ChatMatch;

import java.util.LinkedHashMap;
import java.util.Map;

// what the chat told us about the party: loot multiplier and the current round of rolls.
// who is in the party comes from the scoreboard, not from here
public final class Party {
    // choice is "need", "greed" or "pass". value is 0 for a pass
    public record Roll(String choice, int value) {
    }

    // a roll this long after the last one starts a new round
    private static final long ROUND_GAP_MS = 30_000;

    private Integer multiplier;
    private final Map<String, Roll> rolls = new LinkedHashMap<>();
    private long lastRollAt;

    public Integer multiplier() {
        return multiplier;
    }

    public Map<String, Roll> rolls() {
        return rolls;
    }

    // now = current time in ms, passed in so tests can pick it. me = your own name
    public void onChat(ChatMatch m, long now, String me) {
        switch (m.id()) {
            case "party_join", "party_leave" -> {
                multiplier = Integer.valueOf(m.get("multiplier"));
                // you joining means a different party, its rolls arent yours
                if (m.id().equals("party_join") && m.get("name").equals(me)) clearRolls();
            }
            // someone won, the roll is over
            case "party_win" -> clearRolls();
            case "party_roll" -> roll(m.get("name"), new Roll(m.get("choice"), Integer.parseInt(m.get("roll"))), now);
            case "party_pass" -> roll(m.get("name"), new Roll("pass", 0), now);
            default -> {
            }
        }
    }

    private void roll(String name, Roll roll, long now) {
        // old rolls stay up until a new round starts: after a pause, or when someone rolls a second time
        if (now - lastRollAt > ROUND_GAP_MS || rolls.containsKey(name)) rolls.clear();
        rolls.put(name, roll);
        lastRollAt = now;
    }

    // highest need wins, greed only counts when nobody needed. null when nobody rolled
    public String leader() {
        String best = null;
        int bestRank = 0, bestValue = -1;
        for (Map.Entry<String, Roll> e : rolls.entrySet()) {
            int rank = e.getValue().choice().equals("need") ? 2 : e.getValue().choice().equals("greed") ? 1 : 0;
            if (rank == 0) continue;
            if (rank > bestRank || (rank == bestRank && e.getValue().value() > bestValue)) {
                best = e.getKey();
                bestRank = rank;
                bestValue = e.getValue().value();
            }
        }
        return best;
    }

    public void clearRolls() {
        rolls.clear();
        lastRollAt = 0;
    }

    public void reset() {
        multiplier = null;
        clearRolls();
    }
}
