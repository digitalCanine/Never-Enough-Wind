package com.neverenoughwind.state;

import com.neverenoughwind.dao.ChatPatternDao;
import com.neverenoughwind.parse.ChatMatch;
import com.neverenoughwind.state.ChatTabs.Tab;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTabsTest {
    private static ChatPatternDao chat;

    @BeforeAll
    static void load() {
        chat = ChatPatternDao.load();
    }

    private static Tab tab(String line) {
        return ChatTabs.of(chat.match(line).map(ChatMatch::category).orElse(null));
    }

    @Test
    void linesLandInTheirTab() {
        assertEquals(Tab.MAIN, tab("WOOL.Steve: hello"));
        assertEquals(Tab.MAIN, tab("something nobody has seen before"));
        assertEquals(Tab.CLAN, tab("[WOOL] Steve: hello"));
        assertEquals(Tab.WHISPERS, tab("Steve >> hello"));
        assertEquals(Tab.WHISPERS, tab(">> Steve: hello"));
        assertEquals(Tab.EVENTS, tab("Team aqua wins the beef event!"));
        assertEquals(Tab.SYSTEM, tab("Welcome Steve!"));
        assertEquals(Tab.SYSTEM, tab("Steve rolled need 51!"));
        assertEquals(Tab.SYSTEM, tab("Steve drowned"));
    }

    @Test
    void mainShowsWhatItIsToldTo() {
        Set<Tab> none = EnumSet.noneOf(Tab.class), clan = EnumSet.of(Tab.CLAN);
        assertTrue(ChatTabs.shows(Tab.MAIN, Tab.CLAN, clan, none));
        assertFalse(ChatTabs.shows(Tab.MAIN, Tab.CLAN, none, none));
        assertFalse(ChatTabs.shows(Tab.MAIN, Tab.SYSTEM, clan, none));
        // a tab with its own window leaves main
        assertFalse(ChatTabs.shows(Tab.MAIN, Tab.CLAN, clan, clan));
        assertTrue(ChatTabs.shows(Tab.CLAN, Tab.CLAN, none, none));
        assertFalse(ChatTabs.shows(Tab.CLAN, Tab.MAIN, clan, none));
    }

    @Test
    void prefillComesFromTheData() {
        assertEquals("/c ", chat.prefill("clan"));
        assertEquals(null, chat.prefill("main"));
    }
}
