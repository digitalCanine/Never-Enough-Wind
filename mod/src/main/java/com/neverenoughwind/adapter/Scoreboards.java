package com.neverenoughwind.adapter;

import net.minecraft.client.MinecraftClient;
import com.neverenoughwind.parse.EventParser;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Scoreboards {
    // the party is a hidden team named with 24 hex characters
    private static final Pattern PARTY_TEAM = Pattern.compile("^[0-9a-f]{24}$");

    private Scoreboards() {}

    // your line on a running event's sidebar. everyone has a score there, not only the 15 the sidebar shows
    // lines = rows the vanilla sidebar draws
    public record OwnScore(String label, int secondsLeft, int score, int rank, int players, int lines) {
    }

    // null when no event sidebar is up or you have no score on it
    public static OwnScore ownScore() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return null;
        Scoreboard board = mc.world.getScoreboard();
        ScoreboardObjective side = board.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (side == null) return null;
        EventParser.Title title = EventParser.title(side.getDisplayName().getString());
        if (title == null) return null;
        String me = mc.player.getNameForScoreboard();
        Integer mine = null;
        int players = 0, lines = 0;
        List<Integer> all = new ArrayList<>();
        for (ScoreboardEntry e : board.getScoreboardEntries(side)) {
            players++;
            if (!e.hidden()) lines++;
            all.add(e.value());
            if (e.owner().equals(me)) mine = e.value();
        }
        if (mine == null) return null;
        int rank = 1;
        for (int v : all) {
            if (v > mine) rank++;
        }
        return new OwnScore(title.label(), title.secondsLeft(), mine, rank, players, Math.min(lines, 15));
    }

    // the party's team name, a different one for every party. null when not in a party
    public static String partyId() {
        Team team = partyTeam();
        return team == null ? null : team.getName();
    }

    private static Team partyTeam() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return null;
        String me = mc.player.getNameForScoreboard();
        for (Team team : mc.world.getScoreboard().getTeams()) {
            if (PARTY_TEAM.matcher(team.getName()).matches() && team.getPlayerList().contains(me)) return team;
        }
        return null;
    }

    // online party members with yourself first, empty when not in a party
    public static List<String> partyMembers() {
        Team team = partyTeam();
        if (team == null) return List.of();
        String me = MinecraftClient.getInstance().player.getNameForScoreboard();
        List<String> out = new ArrayList<>();
        out.add(me);
        team.getPlayerList().stream().filter(n -> !n.equals(me)).sorted(String.CASE_INSENSITIVE_ORDER).forEach(out::add);
        return out;
    }
}
