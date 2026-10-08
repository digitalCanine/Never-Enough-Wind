package com.neverenoughwind.state;

import com.neverenoughwind.dao.ChatPatternDao;
import com.neverenoughwind.parse.ChatMatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// lines copied from real chat logs
class PartyTest {
    static ChatPatternDao chat;

    @BeforeAll
    static void load() {
        chat = ChatPatternDao.load();
    }

    static ChatMatch line(String text) {
        return chat.match(text).orElseThrow();
    }

    @Test
    void patternsMatchRealLines() {
        assertEquals("party_roll", line("RavenX123173 rolled greed 53!").id());
        assertEquals("party_pass", line("stuerzl passed!").id());
        assertEquals("party_join", line("Solaresque joined party! Max loot multiplier is now 266%.").id());
        assertEquals("boss_daily", line("Claimed mail: Fresh Blood! [Summoner's Midas Key]").id());
        assertEquals("public_chat", line("WOOL.Dogeram: oh snap").id());
        // color codes are stripped before matching
        assertEquals("party_pass", line("§estuerzl passed!").id());
        assertTrue(chat.match("some line nothing knows").isEmpty());
        // someone saying it in public chat is not a roll
        assertEquals("public_chat", line("stuerzl: stuerzl passed!").id());
    }

    @Test
    void multiplierFollowsJoinAndLeave() {
        Party p = new Party();
        assertNull(p.multiplier());
        p.onChat(line("Nowher joined party! Max loot multiplier is now 399%."), 0, "Solaresque");
        assertEquals(399, p.multiplier());
        p.onChat(line("Nowher left party. Max loot multiplier is now 266%."), 0, "Solaresque");
        assertEquals(266, p.multiplier());
    }

    @Test
    void needBeatsGreed() {
        Party p = new Party();
        p.onChat(line("aaa rolled greed 99!"), 1000, "Solaresque");
        assertEquals("aaa", p.leader());
        p.onChat(line("bbb rolled need 12!"), 2000, "Solaresque");
        p.onChat(line("ccc passed!"), 3000, "Solaresque");
        p.onChat(line("ddd rolled need 40!"), 4000, "Solaresque");
        assertEquals("ddd", p.leader());
        assertEquals(4, p.rolls().size());
        assertEquals("pass", p.rolls().get("ccc").choice());
    }

    @Test
    void oldRollsStayUntilANewRound() {
        Party p = new Party();
        p.onChat(line("aaa rolled greed 50!"), 1000, "Solaresque");
        p.onChat(line("bbb rolled greed 60!"), 5000, "Solaresque");
        // nothing clears them by time alone
        assertEquals(2, p.rolls().size());
        // a roll long after starts a new round
        p.onChat(line("ccc rolled greed 10!"), 5000 + 60_000, "Solaresque");
        assertEquals(1, p.rolls().size());
        assertEquals("ccc", p.leader());
        // so does the same player rolling again
        p.onChat(line("ddd rolled greed 20!"), 5000 + 61_000, "Solaresque");
        p.onChat(line("ccc rolled need 5!"), 5000 + 62_000, "Solaresque");
        assertEquals(1, p.rolls().size());
        assertEquals("need", p.rolls().get("ccc").choice());
    }

    @Test
    void aWinEndsTheRoll() {
        Party p = new Party();
        p.onChat(line("Solaresque rolled need 51!"), 1000, "Solaresque");
        p.onChat(line("aaa rolled greed 80!"), 2000, "Solaresque");
        assertEquals("Solaresque", p.leader());
        p.onChat(line("Solaresque wins Blood Midas Key!"), 3000, "Solaresque");
        assertTrue(p.rolls().isEmpty());
        assertNull(p.leader());
        assertEquals("party_top_roller", line("Claimed mail: Top Roller! [Blood Midas Key]").id());
    }

    @Test
    void joiningAnotherPartyClearsRolls() {
        Party p = new Party();
        p.onChat(line("aaa rolled greed 80!"), 1000, "Solaresque");
        // someone else joining changes nothing
        p.onChat(line("bbb joined party! Max loot multiplier is now 399%."), 2000, "Solaresque");
        assertEquals(1, p.rolls().size());
        p.onChat(line("Solaresque joined party! Max loot multiplier is now 266%."), 3000, "Solaresque");
        assertTrue(p.rolls().isEmpty());
        assertEquals(266, p.multiplier());
    }

    @Test
    void onlyPassesMeansNoLeader() {
        Party p = new Party();
        p.onChat(line("aaa passed!"), 1000, "Solaresque");
        assertNull(p.leader());
    }
}
