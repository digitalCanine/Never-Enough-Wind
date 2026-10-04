package com.neverenoughwind.dao;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.state.Relations;
import com.neverenoughwind.state.Roster;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// reads the shipped clan list and the player's own files. the player's files only hold what they looked up or changed
public final class ClanDao {
    private static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private ClanDao() {}

    // the list that comes with the mod. every clan gets the day it was collected as its lookup time
    public static List<Roster.Clan> shipped() {
        JsonObject root = Json.load("clans.json");
        long when = 0;
        try {
            when = LocalDate.parse(Json.str(root, "collected")).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (RuntimeException ignored) {
            // no date, treat it as oldest
        }
        return clans(root, when);
    }

    private static List<Roster.Clan> clans(JsonObject root, long fallbackTime) {
        List<Roster.Clan> out = new ArrayList<>();
        for (JsonObject o : Json.objects(root.get("clans"))) {
            String tag = Json.str(o, "tag");
            if (tag == null) continue;
            JsonElement checked = o.get("checked");
            long when = checked != null && checked.isJsonPrimitive() ? checked.getAsLong() : fallbackTime;
            out.add(new Roster.Clan(tag, Json.str(o, "name"), Json.str(o, "leader"), Json.strings(o, "officers"),
                    Json.strings(o, "members"), Json.strings(o, "recruits"), when));
        }
        return out;
    }

    // the player's own lookups and settled names, an empty roster file when there is none yet
    public static void loadPlayer(Path file, Roster roster) {
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            roster.putAll(clans(root, 0));
            roster.settleAll(Json.stringMap(root, "settled"));
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not read {}: {}", file, e.toString());
        }
    }

    // only the clans newer than the shipped list get written
    public static void savePlayer(Path file, Roster roster, long shippedTime) {
        JsonObject root = new JsonObject();
        root.addProperty("schema_version", 1);
        JsonArray arr = new JsonArray();
        for (Roster.Clan c : roster.clans()) {
            if (c.checked() <= shippedTime) continue;
            JsonObject o = new JsonObject();
            o.addProperty("tag", c.tag());
            o.addProperty("name", c.name());
            o.addProperty("leader", c.leader());
            o.add("officers", array(c.officers()));
            o.add("members", array(c.members()));
            o.add("recruits", array(c.recruits()));
            o.addProperty("checked", c.checked());
            arr.add(o);
        }
        root.add("clans", arr);
        JsonObject settled = new JsonObject();
        roster.settled().forEach(settled::addProperty);
        root.add("settled", settled);
        write(file, root);
    }

    // own / ally / enemy lists. a missing file means no relations, only the detected own clan
    public static void loadRelations(Path file, Relations relations) {
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            relations.set(Json.strings(root, "own"), Json.strings(root, "ally"), Json.strings(root, "enemy"));
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not read {}: {}", file, e.toString());
        }
    }

    // colors by relation from the same file, hex strings. missing ones keep the default
    public static Map<String, Integer> loadColors(Path file) {
        Map<String, Integer> out = new HashMap<>();
        if (!Files.exists(file)) return out;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            Json.stringMap(root, "colors").forEach((k, v) -> {
                try {
                    out.put(k, Integer.parseInt(v.replace("#", ""), 16));
                } catch (NumberFormatException ignored) {
                    // bad color, keep the default
                }
            });
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not read {}: {}", file, e.toString());
        }
        return out;
    }

    private static JsonArray array(List<String> list) {
        JsonArray a = new JsonArray();
        list.forEach(a::add);
        return a;
    }

    private static void write(Path file, JsonObject root) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, (PRETTY.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not write {}: {}", file, e.toString());
        }
    }
}
