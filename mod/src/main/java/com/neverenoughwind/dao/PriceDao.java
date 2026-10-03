package com.neverenoughwind.dao;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neverenoughwind.model.BlockPrice;
import com.neverenoughwind.model.Essence;
import com.neverenoughwind.model.KeyPrice;
import com.neverenoughwind.model.PriceTier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PriceDao {
    private final Map<String, PriceTier> essenceTiers = new HashMap<>();
    private final Map<String, PriceTier> blockTiers = new HashMap<>();
    private final Map<String, KeyPrice> keys = new HashMap<>();
    private final Map<String, BlockPrice> blocks = new HashMap<>();
    private final Map<Integer, Integer> sharpenCost = new HashMap<>();
    private List<String> blockNotes = List.of();

    public static PriceDao load() {
        PriceDao dao = new PriceDao();
        JsonObject root = Json.load("prices.json");
        JsonObject tiers = root.getAsJsonObject("price_tiers");
        readTiers(tiers.getAsJsonObject("essence"), dao.essenceTiers);
        readTiers(tiers.getAsJsonObject("block"), dao.blockTiers);
        for (JsonObject o : Json.objects(root.get("keys"))) {
            Integer[] d = Json.deggs(o);
            String name = Json.str(o, "name");
            if (name != null) dao.keys.put(Json.key(name), new KeyPrice(name, Json.str(o, "price"), d[0], d[1]));
        }
        for (JsonObject o : Json.objects(root.get("blocks"))) {
            String item = Json.str(o, "item");
            if (item != null) dao.blocks.put(item, new BlockPrice(Json.str(o, "name"), item, Json.str(o, "category"), Json.str(o, "tier")));
        }
        JsonObject sharpen = root.getAsJsonObject("sharpen_cost");
        if (sharpen != null) {
            for (Map.Entry<String, JsonElement> e : sharpen.entrySet()) dao.sharpenCost.put(Integer.valueOf(e.getKey()), e.getValue().getAsInt());
        }
        dao.blockNotes = Json.strings(root, "block_notes");
        return dao;
    }

    private static void readTiers(JsonObject tiers, Map<String, PriceTier> into) {
        if (tiers == null) return;
        for (Map.Entry<String, JsonElement> e : tiers.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            Integer[] d = Json.deggs(o);
            into.put(e.getKey(), new PriceTier(e.getKey(), Json.str(o, "price"), d[0], d[1]));
        }
    }

    public Optional<PriceTier> essenceTier(String letter) {
        return Optional.ofNullable(essenceTiers.get(letter));
    }

    public Optional<PriceTier> blockTier(String letter) {
        return Optional.ofNullable(blockTiers.get(letter));
    }

    // empty when that level has no letter, or the text isnt a tier ("no ess form")
    public Optional<PriceTier> essencePrice(Essence essence, int level) {
        String letter = essence.price().get(String.valueOf(level));
        return letter == null ? Optional.empty() : essenceTier(letter);
    }

    // keyName = what comes before "Midas Key" on the item ("Wizard's", "Abyssal"), empty for the plain key.
    // the chart drops the 's ("Wizard Key") and uses one word ("Malphas Key" for "Malphas Building")
    public Optional<KeyPrice> key(String keyName) {
        String name = keyName == null || keyName.isBlank() ? "Midas" : keyName.trim();
        String first = name.split(" ")[0];
        for (String candidate : new String[]{name, name.replaceFirst("'s$", ""), first, first.replaceFirst("'s$", "")}) {
            KeyPrice k = keys.get(Json.key(candidate + " Key"));
            if (k != null) return Optional.of(k);
        }
        return Optional.empty();
    }

    public Optional<BlockPrice> block(String itemId) {
        return Optional.ofNullable(blocks.get(itemId));
    }

    public List<String> blockNotes() {
        return blockNotes;
    }

    // 960 -> "15s", 1000 -> "15s 40d", 1728 -> "1sh". same units as the price chart
    public static String text(int deggs) {
        if (deggs >= 1728 && deggs % 1728 == 0) return deggs / 1728 + "sh";
        if (deggs >= 1728) return String.format(java.util.Locale.ROOT, "%.1fsh", deggs / 1728.0);
        if (deggs < 64) return deggs + "d";
        return deggs / 64 + "s" + (deggs % 64 == 0 ? "" : " " + deggs % 64 + "d");
    }

    // deggs to go from one sharpness level to another, -1 if a level isnt in the table
    public int sharpenCost(int from, int to) {
        int total = 0;
        for (int level = from + 1; level <= to; level++) {
            Integer cost = sharpenCost.get(level);
            if (cost == null) return -1;
            total += cost;
        }
        return total;
    }
}
