package com.neverenoughwind.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// who died in a death line, and who the line blames. blamed = names in the order they appear, mobs and plain words included:
// the caller keeps the first one that is a player
public record KillLine(String victim, List<String> blamed) {
    private static final String NAME = "[A-Za-z0-9_]{2,16}";
    // lines the death list also covers that arent a death
    private static final Pattern NOT_A_DEATH = Pattern.compile("^" + NAME + " (is on RAMPAGE|lost \\d+ (fish|kills)|got \\d+ (fish|kills) from)");
    private static final Pattern KILLER_FIRST = Pattern.compile(
            "^(" + NAME + ") (?:rekt|sucked|borrowed soul of|beefed|pwned) (" + NAME + ")\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern VICTIM = Pattern.compile("^(" + NAME + ") ");
    private static final Pattern BLAME = Pattern.compile("\\b(?:by|because of|fighting|escape|due to|fired from|hurt|world as) (" + NAME + ")\\b");

    // null when the line isnt a death
    public static KillLine parse(String line) {
        if (line == null || NOT_A_DEATH.matcher(line).find()) return null;
        Matcher m = KILLER_FIRST.matcher(line);
        if (m.find()) return new KillLine(m.group(2), List.of(m.group(1)));
        m = VICTIM.matcher(line);
        if (!m.find()) return null;
        String victim = m.group(1);
        List<String> blamed = new ArrayList<>();
        Matcher b = BLAME.matcher(line);
        while (b.find()) {
            if (!b.group(1).equals(victim)) blamed.add(b.group(1));
        }
        return new KillLine(victim, List.copyOf(blamed));
    }
}
