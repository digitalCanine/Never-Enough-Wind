package com.neverenoughwind.dao;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.model.GearRules;
import com.neverenoughwind.model.ItemType;
import com.neverenoughwind.model.KnownItem;
import com.neverenoughwind.model.MatchRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemRuleDao {
    // file order is kept, its the order types get checked in
    private final Map<String, ItemType> types = new LinkedHashMap<>();
    private GearRules gear;

    public static ItemRuleDao load() {
        ItemRuleDao dao = new ItemRuleDao();
        JsonObject root = Json.load("items.json");
        JsonObject types = root.getAsJsonObject("types");
        for (Map.Entry<String, JsonElement> e : types.entrySet()) {
            JsonObject t = e.getValue().getAsJsonObject();
            List<MatchRule> rules = new ArrayList<>();
            for (JsonObject m : Json.objects(t.get("match"))) {
                try {
                    boolean i = Json.strings(m, "lore_flags").contains("i");
                    rules.add(new MatchRule(Json.str(m, "item"),
                            Json.pattern(m, "name_regex", false), Json.pattern(m, "lore_regex", i),
                            Json.pattern(m, "lore_first_line_regex", i), Json.pattern(m, "lore_kind_regex", i),
                            Json.str(m, "lore_contains"), Json.pattern(m, "fallback_name_regex", false), Json.str(m, "what")));
                } catch (RuntimeException ex) {
                    NeverEnoughWind.LOG.warn("skipped a bad rule in {}: {}", e.getKey(), ex.toString());
                }
            }
            List<KnownItem> items = new ArrayList<>();
            for (JsonObject k : Json.objects(t.get("items"))) {
                items.add(new KnownItem(Json.str(k, "item"), Json.str(k, "name"), Json.str(k, "lore"),
                        Json.str(k, "enchant"), Json.bool(k, "vanilla"), Json.str(k, "use"), Json.bool(k, "event")));
            }
            dao.types.put(e.getKey(), new ItemType(e.getKey(), List.copyOf(rules), List.copyOf(items)));
        }
        JsonObject g = root.getAsJsonObject("gear");
        JsonObject ess = g.getAsJsonObject("essences");
        JsonObject souls = g.getAsJsonObject("souls");
        boolean soulsI = Json.strings(souls, "lore_flags").contains("i");
        dao.gear = new GearRules(Json.str(ess, "header_line"),
                Json.pattern(ess, "essence_line_regex", false), Json.pattern(ess, "trigger_line_regex", false),
                Json.pattern(ess, "effect_line_regex", false),
                Json.pattern(souls, "accumulates_regex", soulsI), Json.pattern(souls, "consumes_regex", soulsI),
                Json.pattern(g.getAsJsonObject("soulbound"), "lore_regex", false));
        return dao;
    }

    public List<ItemType> types() {
        return List.copyOf(types.values());
    }

    public Optional<ItemType> type(String id) {
        return Optional.ofNullable(types.get(id));
    }

    public GearRules gear() {
        return gear;
    }
}
