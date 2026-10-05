package com.neverenoughwind.parse;

import com.neverenoughwind.dao.Data;
import com.neverenoughwind.model.AuctionItem;
import com.neverenoughwind.model.BlockPrice;
import com.neverenoughwind.model.Essence;
import com.neverenoughwind.model.KeyPrice;
import com.neverenoughwind.model.PriceTier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// everything the item browser lists, built once from the data files. plain text only, the screen picks the colors
public final class Catalog {
    public enum Tone { TITLE, LABEL, TEXT, VALUE, GOOD, DIM }

    // right is null for a line of running text, which the screen wraps
    public record Line(String left, Tone leftTone, String right, Tone rightTone) {
        static Line of(String text, Tone tone) {
            return new Line(text, tone, null, null);
        }

        static Line row(String label, String value) {
            return new Line(label, Tone.LABEL, value, Tone.VALUE);
        }

        static Line gap() {
            return new Line("", Tone.TEXT, null, null);
        }
    }

    // icon = item id, tag = the short text at the end of the list row, haystack = what the search looks through
    public record Entry(String name, String icon, String tag, List<Line> lines, String haystack) {
    }

    public record Section(String name, List<Entry> entries) {
        // every word of the query has to be somewhere in the entry
        public List<Entry> search(String query) {
            String[] words = query == null ? new String[0] : query.toLowerCase(Locale.ROOT).trim().split("\\s+");
            List<Entry> out = new ArrayList<>();
            for (Entry e : entries) {
                boolean all = true;
                for (String w : words) all &= w.isEmpty() || e.haystack().contains(w);
                if (all) out.add(e);
            }
            return out;
        }
    }

    private static final Set<String> IRON = Set.of("sword", "axe", "pickaxe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots");
    private static final Set<String> AS_IS = Set.of("bow", "crossbow", "elytra", "shield", "trident", "stick", "shears");

    private Catalog() {}

    // showDrafts = also show gear texts nobody has checked yet
    public static List<Section> build(Data data, boolean showDrafts) {
        return List.of(
                new Section("Essences", essences(data)),
                new Section("Blocks", blocks(data)),
                new Section("Keys", keys(data, showDrafts)),
                new Section("Gear", gear(data, showDrafts)));
    }

    private static List<Entry> essences(Data data) {
        List<Entry> out = new ArrayList<>();
        for (Essence e : data.essences().all()) {
            List<Line> lines = new ArrayList<>();
            lines.add(Line.of(e.name(), Tone.TITLE));
            String kind = kind(e);
            if (kind != null) lines.add(Line.of(kind, Tone.DIM));
            if (e.maxLevel() != null && e.maxLevel() > 1) {
                String cap = e.globalCap() != null && !e.globalCap().equals(e.maxLevel()) ? ", up to " + e.globalCap() + " across all gear" : "";
                lines.add(Line.of("Levels 1 to " + e.maxLevel() + cap, Tone.DIM));
            }
            lines.add(Line.gap());
            lines.add(Line.of(e.description() == null ? "Not documented yet." : e.description(), Tone.TEXT));
            if (e.details() != null) lines.add(Line.of(e.details(), Tone.DIM));

            List<String> levels = e.price().keySet().stream().sorted(Comparator.comparingInt(Catalog::number)).toList();
            String first = null, last = null;
            if (!levels.isEmpty() || !e.levelText().isEmpty()) lines.add(Line.gap());
            for (String level : levels) {
                Optional<PriceTier> tier = data.prices().essencePrice(e, number(level));
                String price = tier.map(t -> t.letter() + " " + t.text()).orElse(e.price().get(level));
                if (tier.isPresent()) {
                    if (first == null) first = tier.get().letter();
                    last = tier.get().letter();
                }
                boolean single = levels.size() == 1 && (e.maxLevel() == null || e.maxLevel() <= 1);
                lines.add(Line.row(single ? "Price" : "Level " + Roman.of(number(level)), price));
                String does = e.levelText().get(level);
                if (does == null && single) does = e.levelText().get("0");
                if (does != null) lines.add(Line.of(does, Tone.GOOD));
            }
            if (e.key() != null) {
                lines.add(Line.gap());
                lines.add(Line.row("Drops from", title(e.key()) + " Key"));
            }
            if (!e.alsoCalled().isEmpty()) lines.add(Line.of("Also written: " + String.join(", ", e.alsoCalled()), Tone.DIM));

            String tag = first == null ? "" : first.equals(last) ? first : first + " - " + last;
            String hay = String.join(" ", e.name(), String.join(" ", e.alsoCalled()), text(e.description()), text(kind),
                    e.key() == null ? "" : e.key() + " key");
            out.add(new Entry(e.name(), "minecraft:knowledge_book", tag, lines, hay.toLowerCase(Locale.ROOT)));
        }
        out.sort(Comparator.comparing(x -> x.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    // "Buff, for armor"
    private static String kind(Essence e) {
        String kind = e.kind() == null ? null : title(e.kind());
        List<String> on = new ArrayList<>();
        if (e.appliesTo().contains("armor")) on.add("armor");
        if (e.appliesTo().contains("weapon_tool")) on.add("weapons and tools");
        if (on.isEmpty()) return kind;
        String where = "for " + String.join(" and ", on);
        return kind == null ? title(where) : kind + ", " + where;
    }

    private static List<Entry> blocks(Data data) {
        List<Entry> out = new ArrayList<>();
        for (BlockPrice b : data.prices().allBlocks()) {
            Optional<PriceTier> tier = data.prices().blockTier(b.tier());
            List<Line> lines = new ArrayList<>();
            lines.add(Line.of("Infinity " + b.name(), Tone.TITLE));
            if (b.category() != null) lines.add(Line.of(b.category(), Tone.DIM));
            lines.add(Line.gap());
            lines.add(Line.row("Price", tier.map(t -> t.letter() + " " + t.text()).orElse("No price known")));
            out.add(new Entry(b.name(), b.item(), tier.map(PriceTier::text).orElse(""), lines,
                    (b.name() + " " + text(b.category()) + " " + text(b.tier())).toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    private static List<Entry> keys(Data data, boolean showDrafts) {
        List<Entry> out = new ArrayList<>();
        for (KeyPrice k : data.prices().allKeys()) {
            List<Line> lines = new ArrayList<>();
            lines.add(Line.of(k.name(), Tone.TITLE));
            lines.add(Line.gap());
            lines.add(Line.row("Price", k.text() == null ? "No price known" : k.text()));

            String word = k.name().split(" ")[0].toLowerCase(Locale.ROOT);
            long essences = data.essences().all().stream().filter(e -> word.equals(e.key())).count();
            if (essences > 0) {
                lines.add(Line.gap());
                lines.add(Line.row("Essences inside", String.valueOf(essences)));
                lines.add(Line.of("Search the Essences tab for \"" + word + " key\" to list them.", Tone.DIM));
            }
            List<String> gear = data.auctionItems().all().stream()
                    .filter(a -> (showDrafts || !a.infoDraft()) && a.source().contains(k.name()))
                    .map(AuctionItem::name).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
            if (!gear.isEmpty()) {
                lines.add(Line.gap());
                lines.add(Line.of("Gear inside", Tone.LABEL));
                lines.add(Line.of(String.join(", ", gear), Tone.TEXT));
            }
            out.add(new Entry(k.name(), "minecraft:tripwire_hook", text(k.text()), lines,
                    (k.name() + " " + String.join(" ", gear)).toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    private static List<Entry> gear(Data data, boolean showDrafts) {
        List<Entry> out = new ArrayList<>();
        for (AuctionItem a : data.auctionItems().all()) {
            boolean info = showDrafts || !a.infoDraft();
            String does = info ? a.does() : null;
            List<String> source = info ? a.source() : List.of();
            List<Line> lines = new ArrayList<>();
            lines.add(Line.of(a.name(), Tone.TITLE));
            for (String f : a.flavor()) lines.add(Line.of(f, Tone.DIM));
            if (does != null || !source.isEmpty() || a.sharpness() != null) lines.add(Line.gap());
            if (does != null) lines.add(Line.of(does, Tone.TEXT));
            if (!source.isEmpty()) lines.add(Line.row("From", String.join(", ", source)));
            if (a.sharpness() != null) lines.add(Line.row("Drops with", "Sharpness " + Roman.of(a.sharpness())));
            if (info && a.infoDraft() && (does != null || !source.isEmpty())) lines.add(Line.of("Draft, not checked yet", Tone.DIM));

            boolean mythical = a.does() != null && a.does().startsWith("Mythical");
            out.add(new Entry(a.name(), icon(a), mythical ? "Mythical" : "", lines,
                    String.join(" ", a.name(), String.join(" ", a.flavor()), text(does), String.join(" ", source),
                            text(a.toolType()), mythical ? "mythical" : "").toLowerCase(Locale.ROOT)));
        }
        out.sort(Comparator.comparing(x -> x.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    // the real item when the data has it, else a plain one of the same type
    private static String icon(AuctionItem a) {
        if (a.item() != null) return a.item();
        String type = a.toolType();
        if (type == null) return "minecraft:paper";
        if (IRON.contains(type)) return "minecraft:iron_" + type;
        return AS_IS.contains(type) ? "minecraft:" + type : "minecraft:paper";
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String title(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String text(String s) {
        return s == null ? "" : s;
    }
}
