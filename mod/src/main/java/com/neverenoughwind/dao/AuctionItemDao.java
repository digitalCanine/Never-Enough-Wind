package com.neverenoughwind.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neverenoughwind.model.AuctionItem;
import com.neverenoughwind.model.NameStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class AuctionItemDao {
    private final List<AuctionItem> all = new ArrayList<>();
    // joined flavor text -> the items that have it, almost always one
    private final Map<String, List<AuctionItem>> byFlavor = new HashMap<>();
    private NameStyle fallbackStyle;
    private Set<String> gearTypes = Set.of();

    public static AuctionItemDao load() {
        AuctionItemDao dao = new AuctionItemDao();
        JsonObject root = Json.load("auction_items.json");
        for (JsonObject o : Json.objects(root.get("items"))) {
            List<String> flavor = Json.strings(o, "flavor");
            String name = Json.str(o, "name");
            if (name == null || flavor.isEmpty()) continue;
            AuctionItem item = new AuctionItem(name, Json.str(o, "item"), Json.str(o, "type"), flavor,
                    style(o.get("style")), Json.intOrNull(o, "sharpness"), Json.bool(o, "needs_item_id"), Json.str(o, "status"),
                    Json.str(o, "does"), Json.strings(o, "source"), "draft".equals(Json.str(o, "info")));
            dao.all.add(item);
            dao.byFlavor.computeIfAbsent(flavorKey(flavor), k -> new ArrayList<>()).add(item);
        }
        JsonObject fallback = root.getAsJsonObject("fallback");
        if (fallback != null) {
            dao.fallbackStyle = style(fallback.get("style"));
            dao.gearTypes = Set.copyOf(Json.strings(fallback, "gear_types"));
        }
        return dao;
    }

    private static NameStyle style(JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        String from = null, to = null;
        JsonElement g = o.get("gradient");
        if (g != null && g.isJsonArray() && ((JsonArray) g).size() == 2) {
            from = ((JsonArray) g).get(0).getAsString();
            to = ((JsonArray) g).get(1).getAsString();
        }
        return new NameStyle(Json.str(o, "color"), from, to, Json.bool(o, "bold"));
    }

    private static String flavorKey(List<String> lines) {
        return Json.key(String.join(" ", lines));
    }

    public List<AuctionItem> all() {
        return List.copyOf(all);
    }

    // flavorLines = the item's description lines in order, itemId only matters for the few shared texts
    public Optional<AuctionItem> find(String itemId, List<String> flavorLines) {
        List<AuctionItem> hits = byFlavor.get(flavorKey(flavorLines));
        if (hits == null) return Optional.empty();
        if (hits.size() == 1) return Optional.of(hits.get(0));
        String type = AuctionItem.toolTypeOf(itemId);
        return hits.stream().filter(i -> type != null && type.equals(i.toolType())).findFirst();
    }

    public NameStyle fallbackStyle() {
        return fallbackStyle;
    }

    public boolean isGearType(String itemId) {
        String type = AuctionItem.toolTypeOf(itemId);
        return type != null && gearTypes.contains(type);
    }
}
