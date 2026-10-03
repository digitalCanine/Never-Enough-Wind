package com.neverenoughwind.parse;

import com.neverenoughwind.dao.Data;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageCalcTest {
    static ItemParser parser;

    @BeforeAll
    static void load() {
        parser = new ItemParser(Data.load());
    }

    static ItemInfo gear(String id, String... lore) {
        return parser.parse(new ItemSnapshot(id, "x", false, List.of(lore), Map.of()));
    }

    @Test
    void wikiNumbers() {
        // dyrnwyn: diamond sword 7, sharpness 24 -> 19.5 on the wiki
        Damage d = DamageCalc.compute(7, 24, 0, 0, List.of());
        assertEquals(19.5, d.hit());
        assertFalse(d.atLeast());
        // backstabber: iron sword 6, sharpness 17 -> 15
        assertEquals(15.0, DamageCalc.compute(6, 17, 0, 0, List.of()).hit());
    }

    @Test
    void critOnlyMultipliesTheBase() {
        // netherite sword 8, sharpness 30, strength 3
        Damage d = DamageCalc.compute(8, 30, 3, 0, List.of());
        assertEquals(32.5, d.hit());
        assertEquals(41.0, d.crit());
        assertEquals(List.of("Sharpness XXX", "Strength III"), d.parts());
        // cripple's weakness III takes 12 off
        assertEquals(20.5, DamageCalc.compute(8, 30, 3, 3, List.of()).hit());
    }

    @Test
    void offHandEssencesAreListedAndCapped() {
        ItemInfo sword = gear("minecraft:netherite_sword", "Sharpness XXX", "When drawn", "it blazed with fire", "",
                "Essence", "Magic Disrupt III", "Feed Blocker III", "Increased Melee Damage II");
        ItemInfo shovel = gear("minecraft:diamond_shovel", "Sharpness XX", "Relic of the old gods used during mortal combat", "",
                "Essence", "Fatal Strike", "Bloodlust", "Increased Melee Damage III");
        Damage d = DamageCalc.compute(8, 30, 0, 0, List.of(
                new DamageCalc.Source("this item", sword), new DamageCalc.Source("off hand", shovel)));
        assertTrue(d.atLeast());
        // 2 + 3 levels, but the cap across gear is 3
        assertTrue(d.notCounted().contains("Increased Melee Damage III (this item, off hand)"));
        assertTrue(d.notCounted().contains("Bloodlust (off hand)"));
        assertEquals(2, d.notCounted().size());
        assertTrue(d.sometimes().isEmpty());
    }

    @Test
    void situationalIsNotAtLeast() {
        ItemInfo axe = gear("minecraft:diamond_axe", "Sharpness X", "Once used by an infamous merchant.", "",
                "Essence", "Backstab II");
        Damage d = DamageCalc.compute(10, 10, 0, 0, List.of(new DamageCalc.Source("this item", axe)));
        assertFalse(d.atLeast());
        assertEquals(List.of("Backstab II (this item)"), d.sometimes());
    }

    @Test
    void numberText() {
        assertEquals("32.5", DamageCalc.text(32.5));
        assertEquals("41", DamageCalc.text(41.0));
        assertEquals("19.5", DamageCalc.text(19.54));
    }
}
