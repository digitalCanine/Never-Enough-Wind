package com.neverenoughwind.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.neverenoughwind.model.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class WorldDao {
    private static final String OVERWORLD = "minecraft:overworld";

    // seed + dimension -> the subservers that have it, almost always one
    private final Map<String, List<World>> byKey = new HashMap<>();
    private final List<World> all = new ArrayList<>();
    // worlds listed with seed "*": event worlds, known by their dimension whatever the seed is
    private final Map<String, World> byDimension = new HashMap<>();
    private final Map<String, String> fallback;

    private WorldDao(Map<String, String> fallback) {
        this.fallback = fallback;
    }

    public static WorldDao load() {
        JsonObject root = Json.load("worlds.json");
        WorldDao dao = new WorldDao(Json.stringMap(root, "fallback"));
        for (JsonObject o : Json.objects(root.get("worlds"))) {
            String name = Json.str(o, "name"), seed = Json.str(o, "hashed_seed"), dim = Json.str(o, "dimension");
            if (name == null || seed == null || dim == null) continue;
            int[] spawn = null;
            JsonElement sp = o.get("spawn_point");
            if (sp != null && sp.isJsonArray() && ((JsonArray) sp).size() == 3) {
                JsonArray a = (JsonArray) sp;
                spawn = new int[]{a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()};
            }
            String icon = Json.str(o, "icon");
            if (icon == null) icon = name.toLowerCase(Locale.ROOT);
            String server = Json.str(o, "server");
            World world = new World(name, seed, dim, spawn, icon, server == null ? name : server, Json.str(o, "status"));
            if (seed.equals("*")) {
                dao.byDimension.put(dim, world);
                continue;
            }
            dao.byKey.computeIfAbsent(seed + " " + dim, k -> new ArrayList<>()).add(world);
            dao.all.add(world);
        }
        return dao;
    }

    // best answer from seed and dimension alone. empty = unknown world
    public Optional<World> find(long hashedSeed, String dimension) {
        List<World> hits = byKey.get(hashedSeed + " " + dimension);
        if (hits == null) return Optional.ofNullable(byDimension.get(dimension));
        if (hits.size() == 1) return Optional.of(hits.get(0));
        String name = fallback.get(String.valueOf(hashedSeed));
        return Optional.of(hits.stream().filter(w -> w.name().equals(name)).findFirst().orElse(hits.get(0)));
    }

    // true when two subservers share this seed and dimension, so find() was a guess
    public boolean shared(long hashedSeed, String dimension) {
        List<World> hits = byKey.get(hashedSeed + " " + dimension);
        return hits != null && hits.size() > 1;
    }

    // the candidate that belongs to a subserver we already know we're on
    public Optional<World> onServer(long hashedSeed, String dimension, String server) {
        List<World> hits = byKey.get(hashedSeed + " " + dimension);
        if (hits == null || server == null) return Optional.empty();
        return hits.stream().filter(w -> w.server().equals(server)).findFirst();
    }

    // a spawn point the server just sent, in any dimension: matched against the overworld spawn points of that seed
    public Optional<World> bySpawn(long hashedSeed, int[] spawn) {
        if (spawn == null) return Optional.empty();
        String seed = String.valueOf(hashedSeed);
        return all.stream().filter(w -> w.hashedSeed().equals(seed) && OVERWORLD.equals(w.dimension())
                && w.spawn() != null && Arrays.equals(w.spawn(), spawn)).findFirst();
    }
}
