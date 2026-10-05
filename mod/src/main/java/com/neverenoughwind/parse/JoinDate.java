package com.neverenoughwind.parse;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the profile's "First joined: N days ago" line. accounts from before the merge lost their join date,
// so the server counts from 1970 and shows twenty thousand days
public final class JoinDate {
    private static final Pattern LINE = Pattern.compile("^First joined: (\\d+) days ago$");

    private JoinDate() {}

    // today = days since 1970-01-01. true when the line counts from there, give or take a day
    public static boolean lost(String line, long today) {
        if (line == null) return false;
        Matcher m = LINE.matcher(line.trim());
        if (!m.matches() || m.group(1).length() > 9) return false;
        return Long.parseLong(m.group(1)) >= today - 2;
    }
}
