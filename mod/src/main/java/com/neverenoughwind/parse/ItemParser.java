package com.neverenoughwind.parse;

import com.neverenoughwind.dao.Data;
import com.neverenoughwind.model.AuctionItem;
import com.neverenoughwind.model.GearRules;
import com.neverenoughwind.model.ItemType;
import com.neverenoughwind.model.KnownItem;
import com.neverenoughwind.model.MatchRule;
import com.neverenoughwind.model.NameStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemParser {
    // minewind writes enchants as lore lines, they arent part of the description
    private static final Pattern ENCHANT_LINE = Pattern.compile(
            "^[A-Z][A-Za-z' ]+ [IVXLC]+$|^(Curse of .+|Mending|Silk Touch|Infinity|Aqua Affinity|Flame|Channeling|Multishot)$");
    private static final Pattern SHARPNESS_LINE = Pattern.compile("^Sharpness ([IVXLC]+)$");

    private final Data data;

    public ItemParser(Data data) {
        this.data = data;
    }

    // never throws, a broken item just comes back as NONE
    public ItemInfo parse(ItemSnapshot item) {
        try {
            return parseUnsafe(item);
        } catch (RuntimeException e) {
            return ItemInfo.NONE;
        }
    }

    private ItemInfo parseUnsafe(ItemSnapshot item) {
        if (item == null || item.item() == null) return ItemInfo.NONE;
        List<String> lore = item.lore();

        for (ItemType type : data.itemRules().types()) {
            String detail = matchType(type, item);
            if (detail == null) continue;
            List<EssenceEntry> essences = type.id().equals("essence") ? bookEssence(lore) : List.of();
            return new ItemInfo(type.id(), detail, essences, souls(lore), List.of(), null, false, null, null);
        }

        List<EssenceEntry> essences = gearEssences(lore);
        List<Soul> souls = souls(lore);
        List<String> flavor = flavor(lore);
        AuctionItem auction = flavor.isEmpty() ? null : data.auctionItems().find(item.item(), flavor).orElse(null);
        boolean gearType = data.auctionItems().isGearType(item.item());
        boolean hasHeader = lore.contains(data.itemRules().gear().essenceHeader());
        if (auction == null && !hasHeader && !(gearType && (!flavor.isEmpty() || !souls.isEmpty()))) return ItemInfo.NONE;

        boolean renamed = !item.serverStyledName();
        NameStyle restore = null;
        if (renamed) {
            if (auction != null && auction.style() != null) restore = auction.style();
            else if (gearType && !flavor.isEmpty() && !isDuped(lore)) restore = data.auctionItems().fallbackStyle();
        }
        return new ItemInfo("gear", auction == null ? null : auction.name(), essences, souls, flavor, auction, renamed, restore, sharpness(item));
    }

    // null = not this type, otherwise a short detail (key name, block, aura...) or ""
    private String matchType(ItemType type, ItemSnapshot item) {
        for (MatchRule rule : type.rules()) {
            String detail = matchRule(rule, item);
            if (detail != null) return detail;
        }
        for (KnownItem known : type.items()) {
            if (!known.item().equals(item.item())) continue;
            if (known.vanilla()) return known.name();
            if (known.lore() != null && item.lore().stream().anyMatch(l -> l.contains(known.lore()))) return known.name();
            // no lore to go by: needs the enchant and a server styled name
            if (known.enchant() != null && item.enchants().containsKey(known.enchant()) && item.serverStyledName()) return known.name();
        }
        return null;
    }

    private String matchRule(MatchRule rule, ItemSnapshot item) {
        if (rule.item() != null && !rule.item().equals(item.item())) return null;
        List<String> lore = item.lore();
        boolean hasLoreCheck = false;
        Matcher hit = null;

        if (rule.loreFirstLine() != null) {
            hasLoreCheck = true;
            if (!lore.isEmpty()) hit = found(rule.loreFirstLine(), lore.get(0), hit);
        }
        for (Pattern p : new Pattern[]{rule.lore(), rule.loreKind()}) {
            if (p == null) continue;
            hasLoreCheck = true;
            for (String line : lore) hit = found(p, line, hit);
        }
        boolean contains = false;
        if (rule.loreContains() != null) {
            hasLoreCheck = true;
            contains = lore.stream().anyMatch(l -> l.contains(rule.loreContains()));
        }

        Matcher name = rule.name() == null ? null : rule.name().matcher(item.name());
        boolean nameOk = name != null && name.find();
        boolean matched;
        if (hasLoreCheck) {
            // lore decides, items can be renamed. the fallback name covers the ones without the lore line
            matched = hit != null || contains
                    || (rule.fallbackName() != null && rule.fallbackName().matcher(item.name()).find());
        } else {
            matched = nameOk;
        }
        if (!matched) return null;

        String group = firstGroup(nameOk ? name : null);
        if (group == null) group = firstGroup(hit);
        if (group == null) group = rule.what();
        return group == null ? "" : group;
    }

    private static Matcher found(Pattern p, String line, Matcher previous) {
        if (previous != null) return previous;
        Matcher m = p.matcher(line);
        return m.find() ? m : null;
    }

    private static String firstGroup(Matcher m) {
        if (m == null || m.groupCount() < 1) return null;
        return m.group(1);
    }

    // essence books: the essence is the 4th lore line, triggers and effects further down
    private List<EssenceEntry> bookEssence(List<String> lore) {
        GearRules g = data.itemRules().gear();
        if (lore.size() < 4) return List.of();
        Matcher m = g.essenceLine().matcher(lore.get(3).trim());
        if (!m.matches()) return List.of();
        String triggers = null, effects = null;
        for (String line : lore) {
            Matcher t = g.triggerLine().matcher(line);
            if (t.matches()) triggers = t.group("triggers");
            Matcher e = g.effectLine().matcher(line);
            if (e.matches()) effects = e.group("effects");
        }
        return List.of(entry(m, triggers, effects));
    }

    private List<EssenceEntry> gearEssences(List<String> lore) {
        GearRules g = data.itemRules().gear();
        int start = lore.indexOf(g.essenceHeader());
        if (start < 0) return List.of();
        List<EssenceEntry> out = new ArrayList<>();
        Matcher current = null;
        String triggers = null, effects = null;
        for (int i = start + 1; i < lore.size(); i++) {
            String line = lore.get(i);
            Matcher t = g.triggerLine().matcher(line);
            Matcher e = g.effectLine().matcher(line);
            if (t.matches()) {
                triggers = t.group("triggers");
            } else if (e.matches()) {
                effects = e.group("effects");
            } else {
                String trimmed = line.trim();
                // "-" or an empty line ends the list
                if (trimmed.isEmpty() || trimmed.equals("-")) break;
                Matcher m = g.essenceLine().matcher(trimmed);
                if (!m.matches()) break;
                if (current != null) out.add(entry(current, triggers, effects));
                current = m;
                triggers = null;
                effects = null;
            }
        }
        if (current != null) out.add(entry(current, triggers, effects));
        return List.copyOf(out);
    }

    private EssenceEntry entry(Matcher m, String triggers, String effects) {
        String name = m.group("essence").trim();
        int level = Roman.toInt(m.group("level"));
        return new EssenceEntry(name, level, data.essences().byName(name).orElse(null), triggers, effects);
    }

    private List<Soul> souls(List<String> lore) {
        GearRules g = data.itemRules().gear();
        List<Soul> out = new ArrayList<>();
        for (String line : lore) {
            Matcher a = g.accumulates().matcher(line.trim());
            if (a.matches()) out.add(new Soul(a.group("soul"), Integer.parseInt(a.group("count")), false));
            Matcher c = g.consumes().matcher(line.trim());
            if (c.matches()) out.add(new Soul(c.group("soul"), Integer.parseInt(c.group("count")), true));
        }
        return List.copyOf(out);
    }

    // the fixed description: lines before souls / Essence / Player Kills, minus enchant lines and blanks
    static List<String> flavor(List<String> lore) {
        List<String> out = new ArrayList<>();
        for (String line : lore) {
            if (line.startsWith("- ") || line.equals("Essence") || line.startsWith("Player Kills")) break;
            if (line.isBlank() || ENCHANT_LINE.matcher(line).matches()) continue;
            out.add(line);
        }
        return List.copyOf(out);
    }

    private static boolean isDuped(List<String> lore) {
        return lore.stream().anyMatch(l -> l.contains("sterilized") || l.contains("Dupe Police"));
    }

    private static Integer sharpness(ItemSnapshot item) {
        Integer level = item.enchants().get("minecraft:sharpness");
        if (level != null) return level;
        for (String line : item.lore()) {
            Matcher m = SHARPNESS_LINE.matcher(line);
            if (m.matches()) return Roman.toInt(m.group(1));
        }
        return null;
    }
}
