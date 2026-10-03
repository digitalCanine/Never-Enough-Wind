package com.neverenoughwind.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// small helpers so a missing or odd field never throws
final class Json {
    static final String FOLDER = "/neverenoughwind/data/";
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private Json() {}

    static JsonObject load(String file) {
        try (InputStream in = Json.class.getResourceAsStream(FOLDER + file)) {
            if (in == null) throw new IllegalStateException("missing data file " + file);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + file, e);
        }
    }

    // lowercase letters and digits only, so "Anti-Mage" and "Anti Mage" are the same key
    static String key(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static String str(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e == null || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    static boolean bool(JsonObject o, String field) {
        JsonElement e = o.get(field);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    // the sheets have things like "4?" and "3+", take the number
    static Integer intOrNull(JsonObject o, String field) {
        JsonElement e = o.get(field);
        if (e == null || !e.isJsonPrimitive()) return null;
        if (e.getAsJsonPrimitive().isNumber()) return e.getAsInt();
        Matcher m = DIGITS.matcher(e.getAsString());
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    static List<String> strings(JsonObject o, String field) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(field);
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive()) out.add(x.getAsString());
        }
        return List.copyOf(out);
    }

    static Map<String, String> stringMap(JsonObject o, String field) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonElement e = o.get(field);
        if (e != null && e.isJsonObject()) {
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
                if (x.getValue().isJsonPrimitive()) out.put(x.getKey(), x.getValue().getAsString());
            }
        }
        return Map.copyOf(out);
    }

    static List<JsonObject> objects(JsonElement e) {
        List<JsonObject> out = new ArrayList<>();
        if (e == null) return out;
        if (e.isJsonObject()) out.add(e.getAsJsonObject());
        if (e.isJsonArray()) {
            for (JsonElement x : (JsonArray) e) if (x.isJsonObject()) out.add(x.getAsJsonObject());
        }
        return out;
    }

    // null when the field is missing, a bad regex is skipped by the caller
    static Pattern pattern(JsonObject o, String field, boolean ignoreCase) {
        String regex = str(o, field);
        return regex == null ? null : Pattern.compile(regex, ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
    }

    // [min, max] in deggs, or nulls
    static Integer[] deggs(JsonObject o) {
        JsonElement e = o.get("deggs");
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() != 2) return new Integer[]{null, null};
        JsonArray a = e.getAsJsonArray();
        return new Integer[]{a.get(0).getAsInt(), a.get(1).getAsInt()};
    }
}
