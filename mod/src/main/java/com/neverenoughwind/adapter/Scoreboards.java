package com.neverenoughwind.adapter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Scoreboards {
    // the party is a hidden team named with 24 hex characters
    private static final Pattern PARTY_TEAM = Pattern.compile("^[0-9a-f]{24}$");

    private Scoreboards() {}

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
