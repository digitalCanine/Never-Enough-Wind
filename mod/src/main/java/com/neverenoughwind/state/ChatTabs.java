package com.neverenoughwind.state;

import java.util.Locale;
import java.util.Set;

// which chat tab a line belongs to, and which lines a tab shows
public final class ChatTabs {
    public enum Tab {
        MAIN("Main"),
        CLAN("Clan", "clan_chat"),
        WHISPERS("Whispers", "dm_received", "dm_sent"),
        EVENTS("Events", "event_countdown", "event_starting", "event_start", "event_placement", "event_result", "event_end",
                "castle_result", "spawn_government"),
        SYSTEM("System", "party", "death", "welcome", "vote", "sharpen", "system", "server_muted");

        public final String label;
        private final Set<String> categories;

        Tab(String label, String... categories) {
            this.label = label;
            this.categories = Set.of(categories);
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private ChatTabs() {}

    // people talking and anything the patterns dont know stay in main
    public static Tab of(String category) {
        if (category != null) {
            for (Tab t : Tab.values()) {
                if (t.categories.contains(category)) return t;
            }
        }
        return Tab.MAIN;
    }

    // inMain = tabs whose lines main shows too. a tab with its own window never shows in main
    public static boolean shows(Tab view, Tab line, Set<Tab> inMain, Set<Tab> popped) {
        if (view == line) return true;
        return view == Tab.MAIN && inMain.contains(line) && !popped.contains(line);
    }
}
