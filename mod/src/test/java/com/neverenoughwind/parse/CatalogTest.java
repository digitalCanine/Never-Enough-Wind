package com.neverenoughwind.parse;

import com.neverenoughwind.dao.Data;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogTest {
    private static Data data;

    @BeforeAll
    static void load() {
        data = Data.load();
    }

    private static Catalog.Section section(List<Catalog.Section> all, String name) {
        return all.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void everySectionHasItsEntries() {
        List<Catalog.Section> all = Catalog.build(data, false);
        assertEquals(258, section(all, "Essences").entries().size());
        assertEquals(351, section(all, "Blocks").entries().size());
        assertEquals(18, section(all, "Keys").entries().size());
        assertEquals(237, section(all, "Gear").entries().size());
    }

    @Test
    void searchNeedsEveryWordAndIgnoresCase() {
        Catalog.Section essences = section(Catalog.build(data, false), "Essences");
        assertEquals("Magic Disrupt", essences.search("MAGIC disr").get(0).name());
        assertTrue(essences.search("zzzz nothing").isEmpty());
        assertEquals(258, essences.search("  ").size());
        // the key an essence comes from is searchable
        assertFalse(essences.search("paradox key").isEmpty());
    }

    @Test
    void anEssenceShowsItsPricePerLevel() {
        Catalog.Entry wither = section(Catalog.build(data, false), "Essences").search("wither").stream()
                .filter(e -> e.name().equals("Wither")).findFirst().orElseThrow();
        assertTrue(wither.lines().stream().anyMatch(l -> "Level III".equals(l.left()) && l.right() != null));
        assertFalse(wither.tag().isEmpty());
    }

    @Test
    void filtersNarrowTheListAndSortingOrdersIt() {
        Catalog.Section essences = section(Catalog.build(data, false), "Essences");
        Catalog.Filter key = essences.filters().stream().filter(f -> f.name().equals("Key")).findFirst().orElseThrow();
        assertEquals(5, key.options().size());
        List<Catalog.Entry> paradox = essences.search("", java.util.Map.of("Key", "Paradox"), Catalog.Sort.DEFAULT);
        assertEquals(21, paradox.size());
        // a filter left on "All" is stored as null and lets everything through
        java.util.Map<String, String> all = new java.util.HashMap<>();
        all.put("Key", null);
        assertEquals(258, essences.search("", all, Catalog.Sort.DEFAULT).size());
        List<Catalog.Entry> cheap = essences.search("", java.util.Map.of(), Catalog.Sort.CHEAP);
        List<Catalog.Entry> costly = essences.search("", java.util.Map.of(), Catalog.Sort.COSTLY);
        assertTrue(cheap.get(0).price() >= 0 && cheap.get(0).price() <= costly.get(0).price());
        // whatever has no price ends up last either way
        assertTrue(cheap.get(cheap.size() - 1).price() < 0 || cheap.stream().allMatch(e -> e.price() >= 0));
    }

    @Test
    void draftsOnlyShowWhenAsked() {
        Catalog.Entry hidden = section(Catalog.build(data, false), "Gear").search("sicario").get(0);
        Catalog.Entry shown = section(Catalog.build(data, true), "Gear").search("sicario").get(0);
        assertFalse(hidden.lines().stream().anyMatch(l -> "From".equals(l.left())));
        assertTrue(shown.lines().stream().anyMatch(l -> "From".equals(l.left())));
    }
}
