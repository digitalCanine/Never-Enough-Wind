package com.neverenoughwind.state;

import com.neverenoughwind.dao.ChatPatternDao;
import com.neverenoughwind.dao.ClanDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// runs against the shipped clan list
class RosterTest {
    Roster roster;

    @BeforeEach
    void load() {
        roster = new Roster();
        roster.putAll(ClanDao.shipped());
    }

    @Test
    void shippedList() {
        assertEquals(1410, roster.size());
        assertEquals(List.of(new Roster.Membership("hoes", "officer")), roster.of("digitalCanine"));
        // names dont care about case
        assertEquals("hoes", roster.of("DIGITALCANINE").get(0).tag());
        assertTrue(roster.of("nobody_by_that_name").isEmpty());
        assertEquals("Facebook Marketplace", roster.clan("META").name());
    }

    @Test
    void listedInTwoClansShowsBothUntilChatSettlesIt() {
        // clan glitched: still on babe's list
        List<Roster.Membership> doc = roster.of("DocSousa");
        assertEquals(2, doc.size());
        // highest rank first
        assertEquals(new Roster.Membership("AXE", "leader"), doc.get(0));
        assertEquals(new Roster.Membership("BABE", "member"), doc.get(1));
        // a nick or a tag they arent listed under changes nothing
        assertFalse(roster.settle("DocSousa", "srmp"));
        assertFalse(roster.settle("someNick", "AXE"));
        assertEquals(2, roster.of("DocSousa").size());
        // their own chat line with the tag settles it
        assertTrue(roster.settle("DocSousa", "axe"));
        assertEquals(List.of(new Roster.Membership("AXE", "leader")), roster.of("DocSousa"));
        assertFalse(roster.settle("DocSousa", "AXE"));
    }

    @Test
    void aNewerLookupReplacesTheClan() {
        long now = System.currentTimeMillis();
        assertTrue(roster.put(new Roster.Clan("hoes", "Helping Organize Every Subject", "Gwungis",
                List.of("notforstaken"), List.of("digitalCanine"), List.of(), now)));
        assertEquals("member", roster.of("digitalCanine").get(0).rank());
        assertTrue(roster.of("Knight_Thunder").isEmpty());
        // an older one doesnt undo it
        assertFalse(roster.put(new Roster.Clan("hoes", "x", "someone", List.of(), List.of(), List.of(), now - 1000)));
        assertEquals("member", roster.of("digitalCanine").get(0).rank());
    }

    @Test
    void relations() {
        Relations r = new Relations();
        r.set(List.of("hoes", "AXE"), List.of("res"), List.of("Srmp", "done"), List.of("USA", "srmp"));
        assertEquals(Relations.Kind.TRADEBANNED, r.of("usa"));
        assertEquals(Relations.Kind.OWN, r.of("axe"));
        assertEquals(Relations.Kind.ALLY, r.of("RES"));
        assertEquals(Relations.Kind.ENEMY, r.of("srmp"));
        assertEquals(Relations.Kind.ENEMY, r.of("Done"));
        assertEquals(Relations.Kind.NEUTRAL, r.of("BABE"));
        // the clan whose chat we read is ours even with nothing configured
        Relations empty = new Relations();
        assertEquals(Relations.Kind.NEUTRAL, empty.of("hoes"));
        empty.detected("hoes");
        assertEquals(Relations.Kind.OWN, empty.of("HOES"));
    }

    @Test
    void clanFindReplyParses() {
        ChatPatternDao chat = ChatPatternDao.load();
        var m = chat.match("Egotistic (Ego)\nleader: \nofficers: Arkaic Wornado \nrecruits: qutaro sveaaaa tiosso ").orElseThrow();
        assertEquals("clan_find", m.id());
        assertEquals("Ego", m.get("clan"));
        assertEquals("Arkaic Wornado", m.get("officers").trim());
        assertEquals("clan_find", chat.match("Aqua Teen Hunger Force (ATHF)\nleader: ").orElseThrow().id());
    }
}
