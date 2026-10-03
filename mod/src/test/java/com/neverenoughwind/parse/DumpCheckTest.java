package com.neverenoughwind.parse;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neverenoughwind.dao.Data;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

// only runs where the debug tool's item dumps exist. writes build/dump-report.txt
class DumpCheckTest {
    @Test
    void parseEveryDump() throws Exception {
        Path dir = Path.of(System.getProperty("user.home"),
                ".local/share/PrismLauncher/instances/Minewind/minecraft/ned-debug/items");
        Assumptions.assumeTrue(Files.isDirectory(dir));
        ItemParser parser = new ItemParser(Data.load());
        List<String> report = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".jsonl")).sorted().toList();
        }
        for (Path file : files) {
            Map<String, Integer> counts = new TreeMap<>();
            List<String> notes = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank()) continue;
                JsonObject j = JsonParser.parseString(line).getAsJsonObject();
                JsonObject c = j.getAsJsonObject("compact");
                JsonObject comps = j.has("raw") && j.getAsJsonObject("raw").has("components")
                        ? j.getAsJsonObject("raw").getAsJsonObject("components") : new JsonObject();
                List<String> lore = new ArrayList<>();
                if (c.has("lore")) c.getAsJsonArray("lore").forEach(e -> lore.add(e.getAsString()));
                Map<String, Integer> ench = new HashMap<>();
                JsonElement en = comps.get("minecraft:enchantments");
                if (en != null && en.isJsonObject()) en.getAsJsonObject().entrySet().forEach(e -> {
                    if (e.getValue().isJsonPrimitive()) ench.put(e.getKey(), e.getValue().getAsInt());
                });
                JsonElement cn = comps.get("minecraft:custom_name");
                boolean styled = cn != null && cn.toString().contains("\"color\"");
                ItemInfo info = parser.parse(new ItemSnapshot(c.get("item").getAsString(), c.get("name").getAsString(), styled, lore, ench));
                counts.merge(String.valueOf(info.category()), 1, Integer::sum);
                for (EssenceEntry e : info.essences()) {
                    if (e.essence() == null) notes.add("  unknown essence '" + e.name() + "' on " + c.get("name").getAsString());
                }
                if (info.isEmpty() && !lore.isEmpty()) notes.add("  no category, has lore: " + c.get("item").getAsString() + " | " + c.get("name").getAsString() + " | " + lore.get(0));
            }
            report.add(file.getFileName() + " " + counts);
            report.addAll(notes);
        }
        Files.createDirectories(Path.of("build"));
        Files.write(Path.of("build/dump-report.txt"), report);
    }
}
