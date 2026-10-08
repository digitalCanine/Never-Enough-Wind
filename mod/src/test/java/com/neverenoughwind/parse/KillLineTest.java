package com.neverenoughwind.parse;

import com.neverenoughwind.state.KillCounts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KillLineTest {
    @Test
    void killerFirstLines() {
        assertEquals(new KillLine("Nowher", List.of("Solaresque")), KillLine.parse("Solaresque rekt Nowher and got a double kill!"));
        assertEquals(new KillLine("Nowher", List.of("Solaresque")), KillLine.parse("Solaresque sucked Nowher dry"));
        assertEquals(new KillLine("Nowher", List.of("Solaresque")), KillLine.parse("Solaresque borrowed soul of Nowher"));
        assertEquals(new KillLine("Nowher", List.of("Solaresque")), KillLine.parse("Solaresque beefed Nowher for 66!"));
    }

    @Test
    void victimFirstLines() {
        assertEquals(new KillLine("Nowher", List.of("Solaresque")), KillLine.parse("Nowher was slain by Solaresque using [Zord]"));
        assertEquals(new KillLine("Nowher", List.of("Solaresque")), KillLine.parse("Nowher died because of Solaresque's Skeleton Scout"));
        assertEquals(new KillLine("Nowher", List.of("Solaresque")), KillLine.parse("Nowher withered away while fighting Solaresque"));
        assertEquals(new KillLine("Nowher", List.of("Solaresque")), KillLine.parse("Nowher was killed by 🔥 Cremation 🔥 while trying to hurt Solaresque"));
        // the caller drops what isnt a player
        assertEquals(new KillLine("Nowher", List.of("magic", "Solaresque")), KillLine.parse("Nowher was killed by magic while trying to escape Solaresque"));
        assertEquals(new KillLine("Nowher", List.of("Spider")), KillLine.parse("Nowher was burned to a crisp while fighting Spider"));
    }

    @Test
    void dyingAlone() {
        assertEquals(new KillLine("Nowher", List.of()), KillLine.parse("Nowher fell from a high place"));
        assertEquals(new KillLine("Nowher", List.of()), KillLine.parse("Nowher died to the void (Death #18118)"));
    }

    @Test
    void notDeaths() {
        assertNull(KillLine.parse("Nowher is on RAMPAGE!!!"));
        assertNull(KillLine.parse("Nowher lost 5 fish as they teleported away"));
        assertNull(KillLine.parse("Nowher got 3 kills from Solaresque as they ran away"));
    }

    @Test
    void countsReadNaturally() {
        KillCounts c = new KillCounts();
        c.kill("Solaresque");
        c.death("solaresque");
        c.death("Solaresque");
        assertEquals("1 kill, 2 deaths", KillCounts.text(c.of("SOLARESQUE")));
        assertNull(c.of("Nowher"));
    }
}
