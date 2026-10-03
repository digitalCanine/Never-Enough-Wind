package com.neverenoughwind.parse;

import com.neverenoughwind.dao.Data;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// lore copied from real dumps
class ItemParserTest {
    static ItemParser parser;

    @BeforeAll
    static void load() {
        parser = new ItemParser(Data.load());
    }

    static ItemSnapshot item(String id, String name, boolean styled, String... lore) {
        return new ItemSnapshot(id, name, styled, List.of(lore), Map.of());
    }

    @Test
    void renamedDyrnwyn() {
        ItemInfo info = parser.parse(item("minecraft:netherite_sword", "『 absolute parity 』", false,
                "Fire Aspect VIII", "Knockback V", "Sharpness XXX", "Unbreaking IV",
                "When drawn", "it blazed with fire", "", "Player Kills: 475", "",
                "Essence", "Magic Disrupt III", "  --> Melee, Projectile", "Feed Blocker III", "  --> Melee, Projectile",
                "Wither II", "  --> Melee, Projectile, Magic Damage"));
        assertEquals("gear", info.category());
        assertEquals("Dyrnwyn", info.auction().name());
        assertTrue(info.renamed());
        assertEquals("red", info.restoreStyle().color());
        assertEquals(30, info.sharpness());
        assertEquals(List.of("When drawn", "it blazed with fire"), info.flavor());
        assertEquals(3, info.essences().size());
        EssenceEntry md = info.essences().get(0);
        assertEquals("Magic Disrupt", md.name());
        assertEquals(3, md.level());
        assertNotNull(md.essence());
        assertEquals("Melee, Projectile", md.triggers());
        assertEquals("Melee, Projectile, Magic Damage", info.essences().get(2).triggers());
    }

    @Test
    void openSlotAndUnleveled() {
        // "-" marks the open slot, fatal strike has no level
        ItemInfo info = parser.parse(item("minecraft:diamond_shovel", "『 one more encore 』", false,
                "Sharpness XX", "Relic of the old gods used during mortal combat", "to determine which realm will survive.",
                "Once weakened, no challenger can survive", "a strike from this Shovel.", "",
                "- Accumulates 3 Fire Souls", "", "Essence", "Fatal Strike", "  --> Melee", "Bloodlust", "  --> Melee, Projectile", "-"));
        assertEquals(2, info.essences().size());
        assertEquals(0, info.essences().get(0).level());
        assertEquals("Fatal Strike", info.essences().get(0).essence().name());
        assertEquals(1, info.souls().size());
        assertEquals("Fire", info.souls().get(0).type());
        assertEquals(3, info.souls().get(0).count());
    }

    @Test
    void jesterGearIsLeftAlone() {
        // generated name, no description: gear, but no color to restore even when renamed
        ItemInfo info = parser.parse(item("minecraft:netherite_leggings", "『 legs of the mighty 』", false,
                "Fire Protection X", "Protection X", "Thorns II", "Unbreaking VII", "",
                "- Accumulates 1 Ice Soul", "", "Essence", "Strength III", "Anti Mage I", "Heal On Teleport II",
                "  --> Teleportation", "  <-- Heal"));
        assertEquals("gear", info.category());
        assertNull(info.auction());
        assertNull(info.restoreStyle());
        assertEquals(3, info.essences().size());
        // lore has no hyphen, the data does
        assertEquals("Anti-Mage", info.essences().get(1).essence().name());
        assertEquals("Heal", info.essences().get(2).effects());
    }

    @Test
    void serverStyledNameIsNotRepainted() {
        ItemInfo info = parser.parse(item("minecraft:leather_boots", "Bandit's Boots", true,
                "Fancy ninja footwear.", "", "- Increases Movement Speed by 100%", "", "June 2025", "",
                "Essence", "Haste II", "Damage Resistance"));
        assertEquals("Bandit's Boots", info.auction().name());
        assertFalse(info.renamed());
        assertNull(info.restoreStyle());
        assertEquals(2, info.essences().size());
    }

    @Test
    void unknownRenamedGearGetsFallback() {
        ItemInfo info = parser.parse(item("minecraft:diamond_sword", "my sword", false,
                "Sharpness X", "Some description nobody has dumped yet.", "", "Player Kills: 1"));
        assertEquals("gear", info.category());
        assertNull(info.auction());
        assertEquals("red", info.restoreStyle().color());
        assertTrue(info.restoreStyle().bold());
    }

    @Test
    void dupedAndVanillaStayPlain() {
        ItemInfo duped = parser.parse(item("minecraft:diamond_leggings", "Diamond Leggings", false,
                "Ayy I", "Lmao V", "Sorry, this item has been sterilized.", " - Dupe Police"));
        assertNull(duped.restoreStyle());
        assertTrue(parser.parse(item("minecraft:diamond_sword", "Pointy", false)).isEmpty());
        assertTrue(parser.parse(item("minecraft:stone", "Stone", false)).isEmpty());
    }

    @Test
    void essenceBook() {
        ItemInfo info = parser.parse(item("minecraft:knowledge_book", "Essence of Headshot", true,
                "A very tight, alien tome, pulsating with", "the essence of an attack.", "", "Headshot I", "",
                "The alien language is unreadable,", "but a quantum equation is writ in glowing symbols:", "", "  --> Projectile"));
        assertEquals("essence", info.category());
        assertEquals(1, info.essences().size());
        assertEquals("Headshot", info.essences().get(0).name());
        assertEquals(1, info.essences().get(0).level());
        assertEquals("Projectile", info.essences().get(0).triggers());
    }

    @Test
    void otherTypes() {
        assertEquals("keys", parser.parse(item("minecraft:tripwire_hook", "Abyssal Midas Key", true,
                "Opens midas chest", "in the auction house", "", "Abyssal Event")).category());
        assertEquals("Abyssal", parser.parse(item("minecraft:tripwire_hook", "Abyssal Midas Key", true,
                "Opens midas chest", "in the auction house")).detail());
        assertEquals("infinite", parser.parse(item("minecraft:oak_slab", "Infinity Oak Slab", true,
                "An infinite source of Oak Slab", "that can be placed and broken at a distance.")).category());
        assertEquals("magic", parser.parse(item("minecraft:book", "Ice Bomb Spellbook", true,
                "Turns ice into proximity ice.", "", "- Accumulates 1 ice soul", "- Consumes 1 ice soul")).category());
        ItemInfo cookie = parser.parse(item("minecraft:cookie", "Shade Cookie", true,
                "Permanently grants the Shade aura when eaten.", "Can be dropped and eaten by another player."));
        assertEquals("glamour", cookie.category());
        assertEquals("Shade", cookie.detail());
        assertEquals("currency", parser.parse(item("minecraft:dragon_egg", "Dragon Egg", false)).category());
        assertEquals("rarity", parser.parse(item("minecraft:bone", "Enderbone", true, "A bone taken from an Enderman")).category());
        // a renamed tripwire hook with no lore is just a hook
        assertTrue(parser.parse(item("minecraft:tripwire_hook", "Chaos Midas Key", false)).isEmpty());
    }

    @Test
    void roman() {
        assertEquals(30, Roman.toInt("XXX"));
        assertEquals(24, Roman.toInt("XXIV"));
        assertEquals(0, Roman.toInt("abc"));
        assertEquals("XIV", Roman.of(14));
    }
}
