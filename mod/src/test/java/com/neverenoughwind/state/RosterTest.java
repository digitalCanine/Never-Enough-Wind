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
        assertEquals(List.of(new Roster.Membership("WOOL", "leader")), roster.of("Solaresque"));
        // names dont care about case
        assertEquals("WOOL", roster.of("SOLARESQUE").get(0).tag());
        assertTrue(roster.of("nobody_by_that_name").isEmpty());
        assertEquals("Sheep Clan", roster.clan("wool").name());
    }

    @Test
    void listedInTwoClansShowsBothUntilChatSettlesIt() {
        // clan glitched: still on another clan's list
        roster.put(new Roster.Clan("LAMB", "Lamb Clan", "Aquaesque", List.of(), List.of("Solaresque"), List.of(), System.currentTimeMillis()));
        List<Roster.Membership> both = roster.of("Solaresque");
        assertEquals(2, both.size());
        // highest rank first
        assertEquals(new Roster.Membership("WOOL", "leader"), both.get(0));
        assertEquals(new Roster.Membership("LAMB", "member"), both.get(1));
        // a nick or a tag they arent listed under changes nothing
        assertFalse(roster.settle("Solaresque", "zzzz"));
        assertFalse(roster.settle("someNick", "WOOL"));
        assertEquals(2, roster.of("Solaresque").size());
        // their own chat line with the tag settles it
        assertTrue(roster.settle("Solaresque", "wool"));
        assertEquals(List.of(new Roster.Membership("WOOL", "leader")), roster.of("Solaresque"));
        assertFalse(roster.settle("Solaresque", "WOOL"));
    }

    @Test
    void aNewerLookupReplacesTheClan() {
        long now = System.currentTimeMillis();
        assertTrue(roster.put(new Roster.Clan("WOOL", "Sheep Clan", "Solaresque",
                List.of("Nowher"), List.of("xBrow"), List.of(), now)));
        assertEquals("member", roster.of("xBrow").get(0).rank());
        assertTrue(roster.of("SheepySoviet").isEmpty());
        // an older one doesnt undo it
        assertFalse(roster.put(new Roster.Clan("WOOL", "x", "someone", List.of(), List.of(), List.of(), now - 1000)));
        assertEquals("member", roster.of("xBrow").get(0).rank());
    }

    @Test
    void relations() {
        Relations r = new Relations();
        r.set(List.of("wool", "LAMB"), List.of("ram"), List.of("Wolf", "fox"), List.of("BEAR", "wolf"));
        assertEquals(Relations.Kind.TRADEBANNED, r.of("bear"));
        assertEquals(Relations.Kind.OWN, r.of("lamb"));
        assertEquals(Relations.Kind.ALLY, r.of("RAM"));
        assertEquals(Relations.Kind.ENEMY, r.of("wolf"));
        assertEquals(Relations.Kind.ENEMY, r.of("Fox"));
        assertEquals(Relations.Kind.NEUTRAL, r.of("GOAT"));
        // the clan whose chat we read is ours even with nothing configured
        Relations empty = new Relations();
        assertEquals(Relations.Kind.NEUTRAL, empty.of("wool"));
        empty.detected("wool");
        assertEquals(Relations.Kind.OWN, empty.of("WOOL"));
    }

    @Test
    void clanFindReplyParses() {
        ChatPatternDao chat = ChatPatternDao.load();
        var m = chat.match("Sheep Clan (WOOL)\nleader: \nofficers: Nowher xBrow \nrecruits: gusliv Aquaesque HyperJero ").orElseThrow();
        assertEquals("clan_find", m.id());
        assertEquals("WOOL", m.get("clan"));
        assertEquals("Nowher xBrow", m.get("officers").trim());
        assertEquals("clan_find", chat.match("Lamb Clan (LAMB)\nleader: ").orElseThrow().id());
    }
}
