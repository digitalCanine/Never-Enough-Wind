package com.neverenoughwind.parse;

import com.neverenoughwind.model.Essence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DamageCalc {
    // an equipped item and where it sits ("this item", "off hand", "armor")
    public record Source(String label, ItemInfo info) {
    }

    private DamageCalc() {}

    // base = the weapon's attack damage with the player's own 1 included.
    // sources = the weapon first, then the rest of the loadout
    public static Damage compute(double base, int sharpness, int strength, int weakness, List<Source> sources) {
        List<String> parts = new ArrayList<>();
        // vanilla: strength +3 and weakness -4 per level go on the base
        double body = base + 3 * strength - 4 * weakness;
        double enchant = sharpness > 0 ? 0.5 * sharpness + 0.5 : 0;
        if (sharpness > 0) parts.add("Sharpness " + Roman.of(sharpness));
        if (strength > 0) parts.add("Strength " + Roman.of(strength));
        if (weakness > 0) parts.add("Weakness " + Roman.of(weakness));

        // same essence on several items adds up, but only to its cap across all gear
        Map<Essence, Integer> levels = new LinkedHashMap<>();
        Map<Essence, List<String>> where = new LinkedHashMap<>();
        for (Source s : sources) {
            for (EssenceEntry e : s.info().essences()) {
                if (e.essence() == null || e.essence().damage() == null) continue;
                levels.merge(e.essence(), Math.max(1, e.level()), Integer::sum);
                List<String> labels = where.computeIfAbsent(e.essence(), k -> new ArrayList<>());
                if (!labels.contains(s.label())) labels.add(s.label());
            }
        }
        List<String> notCounted = new ArrayList<>(), sometimes = new ArrayList<>();
        for (Map.Entry<Essence, Integer> en : levels.entrySet()) {
            Essence e = en.getKey();
            int level = en.getValue();
            if (e.globalCap() != null) level = Math.min(level, e.globalCap());
            else if (e.maxLevel() != null) level = Math.min(level, e.maxLevel());
            boolean leveled = e.maxLevel() != null && e.maxLevel() > 1;
            String text = (leveled ? e.name() + " " + Roman.of(level) : e.name()) + " (" + String.join(", ", where.get(e)) + ")";
            if (e.damage().equals("unknown")) notCounted.add(text);
            else sometimes.add(text);
        }

        // a crit multiplies the base, the enchant bonus is added after
        double hit = Math.max(0, body) + enchant;
        double crit = Math.max(0, body) * 1.5 + enchant;
        return new Damage(hit, crit, !notCounted.isEmpty(), List.copyOf(parts), List.copyOf(notCounted), List.copyOf(sometimes));
    }

    // 32.5 -> "32.5", 41.0 -> "41"
    public static String text(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}
