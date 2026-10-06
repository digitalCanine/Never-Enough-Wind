package com.neverenoughwind.config;

import com.neverenoughwind.NeverEnoughWind;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// every setting of the mod, one json file. the screens live next to it
public final class Config {
    public enum PanelMode {
        ALWAYS("Always"), SHIFT("Hold Shift"), OFF("Off");

        public final String label;

        PanelMode(String label) {
            this.label = label;
        }
    }

    // where a hud widget sits. until the player moves it, it stays where the mod puts it
    public static final class Widget {
        public boolean enabled = true;
        public boolean moved;
        public float anchorX, anchorY;
        public int offsetX, offsetY;
        // which part of the widget is held in place, -1 = same as the anchor (files from before this existed)
        public float pivotX = -1, pivotY = -1;
        public float scale = 1f;
    }

    // items
    public PanelMode sidePanel = PanelMode.ALWAYS;
    public boolean originalNames = true;
    public boolean borders = true;
    // rgb by item type
    public Map<String, Integer> borderColors = new LinkedHashMap<>();

    // hud
    public Map<String, Widget> widgets = new LinkedHashMap<>();
    public boolean subserverName = true;
    // 1 = as drawn, in line with the text. 2 and 3 are that many times bigger
    public int subserverIconSize = 1;
    // how long a notification stays, and which kinds show up
    public int reminderSeconds = 10;
    public boolean eventReminders = true;
    public boolean dailyReminders = true;

    // clans
    public boolean nametagTags = true;
    public boolean chatColors = true;
    // nametags only: the player's name takes the clan color too. chat names are never touched
    public boolean nameColors = false;
    public boolean glowColors = true;
    public List<String> own = new ArrayList<>(), ally = new ArrayList<>(), enemy = new ArrayList<>(), tradebanned = new ArrayList<>();
    public int ownColor = 0x55FF55, allyColor = 0x55AAFF, enemyColor = 0xFF5555, tradebannedColor = 0xFFAA00, neutralColor = 0xAAAAAA;

    // discord rich presence, off until the player turns it on. each line and picture is their pick
    public enum DiscordLine {
        NOTHING("Nothing"), SUBSERVER("Subserver"), CLAN("Clan and rank"), NAME_CLAN("Name, clan and rank"), NAME("Player name"),
        EVENT("Event"), PARTY("Party size"), HELD_ITEM("Held item");

        public final String label;

        DiscordLine(String label) {
            this.label = label;
        }
    }

    public enum DiscordPicture {
        NOTHING("Nothing"), SUBSERVER("Subserver icon"), HEAD("Your player head"), SERVER_ICON("Minewind server icon");

        public final String label;

        DiscordPicture(String label) {
            this.label = label;
        }
    }

    public boolean discord = false;
    public DiscordLine discordLine1 = DiscordLine.SUBSERVER, discordLine2 = DiscordLine.EVENT;
    public DiscordPicture discordBigPicture = DiscordPicture.SUBSERVER, discordSmallPicture = DiscordPicture.NOTHING;
    public boolean discordElapsed = true;

    // server oddities the mod tidies up
    public boolean fixJoinDate = true;
    public boolean inventoryView = true;

    // an outline on labyrinth chests the player already opened
    public boolean labyrinthMarks = true;
    public int labyrinthColor = 0xFF5555;

    // the debug rows in the side panel and unknown worlds on the hud
    public boolean debug = false;
    // for filming: our widgets stay while f1 hides the rest of the hud
    public boolean trailer = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config current = new Config().filled();
    private static Path file;

    public static Config get() {
        return current;
    }

    public static Map<String, Integer> defaultBorderColors() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("essence", 0xFF4FA3);
        m.put("magic", 0xA855FF);
        m.put("infinite", 0x55FFFF);
        m.put("keys", 0x55FF55);
        m.put("glamour", 0xFF9BEA);
        m.put("currency", 0xFFAA00);
        m.put("rarity", 0xFF5555);
        m.put("mechanics", 0xAAAAAA);
        return m;
    }

    public Widget widget(String id) {
        return widgets.computeIfAbsent(id, k -> new Widget());
    }

    // a file written by an older version can miss things
    private Config filled() {
        if (sidePanel == null) sidePanel = PanelMode.ALWAYS;
        if (discordLine1 == null) discordLine1 = DiscordLine.SUBSERVER;
        if (discordLine2 == null) discordLine2 = DiscordLine.EVENT;
        if (discordBigPicture == null) discordBigPicture = DiscordPicture.SUBSERVER;
        if (discordSmallPicture == null) discordSmallPicture = DiscordPicture.NOTHING;
        if (borderColors == null) borderColors = new LinkedHashMap<>();
        defaultBorderColors().forEach(borderColors::putIfAbsent);
        if (widgets == null) widgets = new LinkedHashMap<>();
        widgets.values().removeIf(w -> w == null);
        if (own == null) own = new ArrayList<>();
        if (ally == null) ally = new ArrayList<>();
        if (enemy == null) enemy = new ArrayList<>();
        if (tradebanned == null) tradebanned = new ArrayList<>();
        return this;
    }

    // dir = the mod's own config folder. a broken file is left alone and the defaults are used
    public static void load(Path dir) {
        file = dir.resolve("config.json");
        Config loaded = null;
        boolean firstRun = !Files.exists(file);
        if (!firstRun) {
            try {
                loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Config.class);
            } catch (Exception e) {
                NeverEnoughWind.LOG.warn("could not read {}: {}", file, e.toString());
            }
        }
        current = (loaded == null ? new Config() : loaded).filled();
        // before the menu existed the relations were a file of their own, take them over once
        if (firstRun && Files.exists(dir.resolve("relations.json"))) {
            current.importRelations(dir.resolve("relations.json"));
            save();
        }
    }

    public static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(current) + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not write {}: {}", file, e.toString());
        }
    }

    private void importRelations(Path old) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(old, StandardCharsets.UTF_8)).getAsJsonObject();
            own = strings(root, "own");
            ally = strings(root, "ally");
            enemy = strings(root, "enemy");
            tradebanned = strings(root, "tradebanned");
            if (root.has("colors") && root.get("colors").isJsonObject()) {
                JsonObject c = root.getAsJsonObject("colors");
                ownColor = rgb(c, "own", ownColor);
                allyColor = rgb(c, "ally", allyColor);
                enemyColor = rgb(c, "enemy", enemyColor);
                tradebannedColor = rgb(c, "tradebanned", tradebannedColor);
                neutralColor = rgb(c, "neutral", neutralColor);
            }
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not read {}: {}", old, e.toString());
        }
    }

    private static List<String> strings(JsonObject o, String field) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(field);
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive()) out.add(x.getAsString());
        }
        return out;
    }

    private static int rgb(JsonObject o, String field, int fallback) {
        try {
            return Integer.parseInt(o.get(field).getAsString().replace("#", ""), 16);
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
