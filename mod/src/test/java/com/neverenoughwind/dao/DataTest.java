package com.neverenoughwind.dao;

import com.neverenoughwind.model.AuctionItem;
import com.neverenoughwind.model.Essence;
import com.neverenoughwind.model.GearRules;
import com.neverenoughwind.model.MatchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// runs against the real files in data/
class DataTest {
    static Data data;

    @BeforeAll
    static void load() {
        data = Data.load();
    }

    @Test
    void essencesLoad() {
        assertEquals(258, data.essences().all().size());
        Essence antiMage = data.essences().byName("Anti-Mage").orElseThrow();
        assertEquals(3, antiMage.maxLevel());
        assertEquals(3, antiMage.globalCap());
        // gear lore writes it without the hyphen
        assertEquals(antiMage, data.essences().byName("Anti Mage").orElseThrow());
        assertEquals(antiMage, data.essences().byId("anti_mage").orElseThrow());
        assertTrue(data.essences().byName("not an essence").isEmpty());
    }

    @Test
    void oddCapsBecomeNumbers() {
        // "4?" in the sheet
        assertEquals(4, data.essences().byName("Poison Resistance").orElseThrow().globalCap());
        assertNull(data.essences().byName("Bind").orElseThrow().globalCap());
    }

    @Test
    void essencePrices() {
        Essence imd = data.essences().byName("Increased Melee Damage").orElseThrow();
        var x = data.prices().essencePrice(imd, 3).orElseThrow();
        assertEquals("X", x.letter());
        assertEquals(1728, x.minDeggs());
        assertEquals(3456, x.maxDeggs());
        // bidding wars has no number
        Essence antiMage = data.essences().byName("Anti-Mage").orElseThrow();
        assertFalse(data.prices().essencePrice(antiMage, 3).orElseThrow().hasNumber());
        assertTrue(data.prices().essencePrice(antiMage, 9).isEmpty());
    }

    @Test
    void sharpening() {
        // debugmenu said 20 to 30 costs 13s and 14d
        assertEquals(846, data.prices().sharpenCost(20, 30));
        assertEquals(585, data.prices().sharpenCost(24, 30));
        assertEquals(0, data.prices().sharpenCost(30, 30));
        assertEquals(-1, data.prices().sharpenCost(30, 31));
    }

    @Test
    void levelTextAndDeggText() {
        assertEquals("30% less magic damage", data.essences().byName("Anti Mage").orElseThrow().levelText().get("3"));
        assertEquals("Healing halved", data.essences().byName("Reduce Heal").orElseThrow().levelText().get("0"));
        assertTrue(data.essences().byName("Bind").orElseThrow().levelText().isEmpty());
        assertEquals("15s", PriceDao.text(960));
        assertEquals("9s 9d", PriceDao.text(585));
        assertEquals("1sh", PriceDao.text(1728));
        assertEquals("1.5sh", PriceDao.text(2592));
        assertEquals("40d", PriceDao.text(40));
    }

    @Test
    void worlds() {
        WorldDao w = data.worlds();
        long shared = 3685739608433952248L;
        String over = "minecraft:overworld";
        assertEquals("Spawn", w.find(-6476551909250050175L, over).orElseThrow().name());
        assertEquals("Arch", w.find(-2236854711765881049L, "minecraft:the_end").orElseThrow().name());
        assertEquals("arch", w.find(-2236854711765881049L, "minecraft:the_end").orElseThrow().icon());
        // chillspawn borrows the spawn icon
        assertEquals("Chillspawn", w.find(-6276508145740963404L, "minecraft:enc").orElseThrow().name());
        assertEquals("spawn", w.find(-6276508145740963404L, "minecraft:enc").orElseThrow().icon());
        assertTrue(w.find(12345L, over).isEmpty());
        // throne and azure share a seed: azure until a spawn point says otherwise
        assertTrue(w.shared(shared, over));
        assertFalse(w.shared(-6476551909250050175L, over));
        assertEquals("Azure", w.find(shared, over).orElseThrow().name());
        assertEquals("Azure", w.find(shared, "minecraft:the_end").orElseThrow().name());
        assertEquals("Throne", w.bySpawn(shared, new int[]{-11, 64, 269}).orElseThrow().name());
        assertEquals("Azure", w.bySpawn(shared, new int[]{-10, 64, 269}).orElseThrow().name());
        // the stale end spawn point that arrives after the real one settles nothing
        assertTrue(w.bySpawn(shared, new int[]{0, 50, 0}).isEmpty());
        // once the subserver is known it carries through portals
        assertEquals("Throne", w.onServer(shared, "minecraft:the_nether", "Throne").orElseThrow().name());
        assertEquals("Throne", w.onServer(shared, "minecraft:the_end", "Throne").orElseThrow().name());
        // the spawn server has an end on that seed too, reached from chillspawn
        assertEquals("Spawn", w.find(-6276508145740963404L, "minecraft:enc").orElseThrow().server());
        assertEquals("Spawn", w.onServer(shared, "minecraft:the_end", "Spawn").orElseThrow().name());
        assertTrue(w.onServer(shared, "minecraft:the_nether", "Spawn").isEmpty());
        assertTrue(w.onServer(shared, over, null).isEmpty());
    }

    @Test
    void unitCalc() {
        PriceDao p = data.prices();
        assertEquals(15552.0, p.convert("9sh", "d").orElseThrow());
        assertEquals(1.0, p.convert("64d", "s").orElseThrow());
        assertEquals(27.0, p.convert("1sh", "s").orElseThrow());
        assertEquals(0.5, p.convert("32d", "S").orElseThrow());
        assertEquals(4.0, p.convert("1d", "ember").orElseThrow());
        assertEquals(1.5, p.convert("1.5SH", "sh").orElseThrow());
        assertTrue(p.convert("9", "d").isEmpty());
        assertTrue(p.convert("9xx", "d").isEmpty());
        assertTrue(p.convert("9sh", "nope").isEmpty());
        assertTrue(p.convert("sh", "d").isEmpty());
    }

    @Test
    void keysAndBlocks() {
        assertEquals(288, data.prices().key("Tempest").orElseThrow().minDeggs());
        // item names dont match the chart exactly
        assertEquals("Wizard Key", data.prices().key("Wizard's").orElseThrow().name());
        assertEquals("Giant Key", data.prices().key("Giant's").orElseThrow().name());
        assertEquals("Summoner's Key", data.prices().key("Summoner's").orElseThrow().name());
        assertEquals("Malphas Key", data.prices().key("Malphas Building").orElseThrow().name());
        assertEquals("Legendary Key", data.prices().key("Legendary").orElseThrow().name());
        // "Key of Midas" has nothing in front
        assertEquals("Midas Key", data.prices().key("").orElseThrow().name());
        assertEquals("Less than 1d", data.prices().key("Abyssal").orElseThrow().text());
        assertTrue(data.prices().key("Nonexistent").isEmpty());
        // infinity blocks: own tier, else the group range, else nothing
        assertEquals("B 2s", data.prices().infinityPrice("minecraft:oak_log").orElseThrow());
        assertEquals("20d - 32d", data.prices().infinityPrice("minecraft:oak_slab").orElseThrow());
        assertEquals("20d - 32d", data.prices().infinityPrice("minecraft:warped_stairs").orElseThrow());
        assertEquals("25d - 32d", data.prices().infinityPrice("minecraft:cobblestone_wall").orElseThrow());
        // fences and gates count as walls
        assertEquals("25d - 32d", data.prices().infinityPrice("minecraft:crimson_fence").orElseThrow());
        assertEquals("25d - 32d", data.prices().infinityPrice("minecraft:oak_fence_gate").orElseThrow());
        assertTrue(data.prices().infinityPrice("minecraft:oak_door").isEmpty());
        assertEquals("2s", data.prices().blockTier(data.prices().block("minecraft:oak_log").orElseThrow().tier()).orElseThrow().text());
        assertEquals("B", data.prices().block("minecraft:oak_log").orElseThrow().tier());
        assertTrue(data.prices().block("minecraft:bedrock").isEmpty());
    }

    @Test
    void auctionItemsByFlavor() {
        // old copies miss the period, upgraded ones are netherite
        AuctionItem dyrnwyn = data.auctionItems()
                .find("minecraft:netherite_sword", List.of("When drawn", "it blazed with fire")).orElseThrow();
        assertEquals("Dyrnwyn", dyrnwyn.name());
        assertEquals(24, dyrnwyn.sharpness());
        assertEquals("red", dyrnwyn.style().color());
        assertFalse(dyrnwyn.style().bold());
        assertTrue(data.auctionItems().find("minecraft:diamond_sword", List.of("some other lore")).isEmpty());
    }

    @Test
    void sharedFlavorUsesToolType() {
        List<String> tornado = List.of("Found occuring naturally in Great Plains,", "tornadoes are lesser known as",
                "the Breath of Vayu, god of the winds.", "Not all tornadoes occur from Vayu,",
                "so it is not entirely correct", "to blame the god every time one happens.");
        // material doesnt matter, an upgraded axe is still the axe
        assertEquals("Tornado Axe", data.auctionItems().find("minecraft:netherite_axe", tornado).orElseThrow().name());
        assertEquals("Tornado Staff", data.auctionItems().find("minecraft:iron_hoe", tornado).orElseThrow().name());
        assertTrue(data.auctionItems().find("minecraft:iron_sword", tornado).isEmpty());
    }

    @Test
    void gradientAndFallback() {
        AuctionItem shovel = data.auctionItems().all().stream()
                .filter(i -> i.name().equals("Elemental Shovel")).findFirst().orElseThrow();
        assertTrue(shovel.style().isGradient());
        assertEquals("#00F3FF", shovel.style().gradientFrom());
        assertEquals("red", data.auctionItems().fallbackStyle().color());
        assertTrue(data.auctionItems().fallbackStyle().bold());
        assertTrue(data.auctionItems().isGearType("minecraft:netherite_sword"));
        assertFalse(data.auctionItems().isGearType("minecraft:tripwire_hook"));
    }

    @Test
    void itemRules() {
        assertEquals(8, data.itemRules().types().size());
        MatchRule key = data.itemRules().type("keys").orElseThrow().rules().get(0);
        assertEquals("minecraft:tripwire_hook", key.item());
        Matcher m = key.name().matcher("Abyssal Midas Key");
        assertTrue(m.matches());
        assertEquals("Abyssal", m.group("key"));
        assertTrue(key.lore().matcher("Opens midas chest").find());
        assertEquals(7, data.itemRules().type("currency").orElseThrow().items().size());
        // every currency says what its for
        assertTrue(data.itemRules().type("currency").orElseThrow().items().stream().allMatch(k -> k.use() != null));
        assertEquals("Two remnants trade for one nether star at Vaulto.", data.itemRules().type("currency").orElseThrow().items().stream()
                .filter(k -> k.name().equals("Star Remnant")).findFirst().orElseThrow().use());
        assertEquals(2, data.itemRules().type("glamour").orElseThrow().rules().size());
    }

    @Test
    void gearRules() {
        GearRules g = data.itemRules().gear();
        assertEquals("Essence", g.essenceHeader());
        Matcher m = g.essenceLine().matcher("Magic Disrupt III");
        assertTrue(m.matches());
        assertEquals("Magic Disrupt", m.group("essence"));
        assertEquals("III", m.group("level"));
        assertTrue(g.essenceLine().matcher("Fatal Strike").matches());
        Matcher souls = g.accumulates().matcher("- Accumulates 3 Fire Souls");
        assertTrue(souls.matches());
        assertEquals("Fire", souls.group("soul"));
        assertNotNull(g.soulbound());
    }
}
