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
    // the same keys and blocks in the chart's own order, for the browser
    private final List<KeyPrice> keyList = new java.util.ArrayList<>();
    private final List<BlockPrice> blockList = new java.util.ArrayList<>();
    private final Map<Integer, Integer> sharpenCost = new HashMap<>();
    private List<String> blockNotes = List.of();
    // unit -> deggs: ember 0.25, d 1, s 64, sh 1728, ch 46656
    private final Map<String, Double> units = new HashMap<>();
    // "slab" -> [20, 32]: blocks the chart only prices as a group
    private final Map<String, int[]> blockGroups = new HashMap<>();

    public static PriceDao load() {
        PriceDao dao = new PriceDao();
        JsonObject root = Json.load("prices.json");
        JsonObject tiers = root.getAsJsonObject("price_tiers");
        readTiers(tiers.getAsJsonObject("essence"), dao.essenceTiers);
        readTiers(tiers.getAsJsonObject("block"), dao.blockTiers);
        for (JsonObject o : Json.objects(root.get("keys"))) {
            Integer[] d = Json.deggs(o);
            String name = Json.str(o, "name");
            if (name == null) continue;
            KeyPrice key = new KeyPrice(name, Json.str(o, "price"), d[0], d[1]);
            dao.keys.put(Json.key(name), key);
            dao.keyList.add(key);
        }
        for (JsonObject o : Json.objects(root.get("blocks"))) {
            String item = Json.str(o, "item");
            if (item == null) continue;
            BlockPrice block = new BlockPrice(Json.str(o, "name"), item, Json.str(o, "category"), Json.str(o, "tier"));
            dao.blocks.put(item, block);
            dao.blockList.add(block);
        }
        JsonObject sharpen = root.getAsJsonObject("sharpen_cost");
        if (sharpen != null) {
            for (Map.Entry<String, JsonElement> e : sharpen.entrySet()) dao.sharpenCost.put(Integer.valueOf(e.getKey()), e.getValue().getAsInt());
        }
        dao.blockNotes = Json.strings(root, "block_notes");
        JsonObject notation = root.getAsJsonObject("notation");
        if (notation != null) {
            for (Map.Entry<String, JsonElement> e : notation.entrySet()) dao.units.put(e.getKey(), e.getValue().getAsDouble());
        }
        JsonObject groups = root.getAsJsonObject("block_groups");
        if (groups != null) {
            for (Map.Entry<String, JsonElement> e : groups.entrySet()) {
                if (e.getValue().isJsonArray() && e.getValue().getAsJsonArray().size() == 2) {
                    dao.blockGroups.put(e.getKey(), new int[]{e.getValue().getAsJsonArray().get(0).getAsInt(), e.getValue().getAsJsonArray().get(1).getAsInt()});
                }
            }
        }
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

    public List<KeyPrice> allKeys() {
        return List.copyOf(keyList);
    }

    public List<BlockPrice> allBlocks() {
        return List.copyOf(blockList);
    }

    public Optional<BlockPrice> block(String itemId) {
        return Optional.ofNullable(blocks.get(itemId));
    }

    // price text for an infinity block: its own tier, else its group's range, else empty
    public Optional<String> infinityPrice(String itemId) {
        Optional<PriceTier> tier = block(itemId).flatMap(b -> blockTier(b.tier()));
        if (tier.isPresent()) return Optional.of(tier.get().letter() + " " + tier.get().text());
        String end = itemId.substring(itemId.lastIndexOf('_') + 1);
        int[] range = blockGroups.get(end);
        return range == null ? Optional.empty() : Optional.of(range[0] + "d - " + range[1] + "d");
    }

    public List<String> blockNotes() {
        return blockNotes;
    }

    // "9sh" to "d" -> 15552. amount = a number with its unit stuck on. empty when a unit isnt known or the number is bad
    public Optional<Double> convert(String amount, String toUnit) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)([a-z]+)$").matcher(amount.toLowerCase(java.util.Locale.ROOT));
        if (!m.matches()) return Optional.empty();
        Double from = units.get(m.group(2)), to = units.get(toUnit.toLowerCase(java.util.Locale.ROOT));
        if (from == null || to == null) return Optional.empty();
        return Optional.of(Double.parseDouble(m.group(1)) * from / to);
    }

    public java.util.Set<String> unitNames() {
        return java.util.Set.copyOf(units.keySet());
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
