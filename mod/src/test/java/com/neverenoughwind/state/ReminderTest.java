package com.neverenoughwind.state;

import com.neverenoughwind.parse.EventParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// header and title lines copied from real logs
class ReminderTest {
    @Test
    void headerAndTitle() {
        assertEquals(new EventParser.Upcoming("Bait Event", 172), EventParser.header("Bait Event in 172 min"));
        assertEquals(new EventParser.Upcoming("Battle for Minewind", 25), EventParser.header("Battle for Minewind in 25 min"));
        assertTrue(EventParser.header("Beef Event in progress!").running());
        assertNull(EventParser.header(""));
        assertNull(EventParser.header(null));
        assertEquals(new EventParser.Title("Fish Caught", 271), EventParser.title("Fish Caught (04:31)"));
        assertNull(EventParser.title("partyHealth"));
    }

    @Test
    void remindsOnceAtEachMark() {
        Reminder r = new Reminder();
        // logging in at exactly 5 minutes is not a reminder
        assertFalse(r.reached(EventParser.header("Bait Event in 5 min")));
        assertFalse(r.reached(EventParser.header("Bait Event in 5 min")));
        assertFalse(r.reached(EventParser.header("Bait Event in 4 min")));
        assertFalse(r.reached(EventParser.header("Bait Event in 2 min")));
        assertTrue(r.reached(EventParser.header("Bait Event in 1 min")));
        // read again in the same minute: no second reminder
        assertFalse(r.reached(EventParser.header("Bait Event in 1 min")));
        assertFalse(r.reached(EventParser.header("Bait Event in progress!")));
        // the next event starts a fresh countdown
        assertFalse(r.reached(EventParser.header("Fox Hunt in 61 min")));
        assertTrue(r.reached(EventParser.header("Fox Hunt in 60 min")));
        assertFalse(r.reached(EventParser.header("Fox Hunt in 59 min")));
        assertTrue(r.reached(EventParser.header("Fox Hunt in 30 min")));
    }
}
