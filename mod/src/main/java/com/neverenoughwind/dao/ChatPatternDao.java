package com.neverenoughwind.dao;

import com.google.gson.JsonObject;
import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.model.ChatPattern;
import com.neverenoughwind.parse.ChatMatch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatPatternDao {
    // sorted by category_order, then file order: specific lines before the catch-alls
    private final List<ChatPattern> patterns = new ArrayList<>();
    // chat tab -> what an empty chat box starts with on that tab
    private final Map<String, String> prefill = new HashMap<>();

    public static ChatPatternDao load() {
        ChatPatternDao dao = new ChatPatternDao();
        JsonObject root = Json.load("chat_patterns.json");
        List<String> order = Json.strings(root, "category_order");
        for (JsonObject o : Json.objects(root.get("patterns"))) {
            try {
                boolean i = Json.strings(o, "flags").contains("i");
                Pattern regex = Json.pattern(o, "regex", i);
                if (regex == null || Json.str(o, "id") == null) continue;
                dao.patterns.add(new ChatPattern(Json.str(o, "id"), Json.str(o, "category"), Json.str(o, "event"), regex, Json.str(o, "status")));
            } catch (RuntimeException e) {
                NeverEnoughWind.LOG.warn("skipped a bad chat pattern {}: {}", Json.str(o, "id"), e.toString());
            }
        }
        // deaths are a plain list of wordings, nothing to pull out of them
        JsonObject deaths = root.getAsJsonObject("death_patterns");
        if (deaths != null) {
            int n = 0;
            for (String regex : Json.strings(deaths, "regexes")) {
                try {
                    dao.patterns.add(new ChatPattern("death_" + n++, "death", null, Pattern.compile(regex), Json.str(deaths, "status")));
                } catch (RuntimeException e) {
                    NeverEnoughWind.LOG.warn("skipped a bad death pattern {}: {}", regex, e.toString());
                }
            }
        }
        JsonObject prefill = root.getAsJsonObject("tab_prefill");
        if (prefill != null) prefill.entrySet().forEach(e -> dao.prefill.put(e.getKey(), e.getValue().getAsString()));
        // stable sort, so file order holds inside a category
        dao.patterns.sort(Comparator.comparingInt(p -> {
            int at = order.indexOf(p.category());
            return at < 0 ? Integer.MAX_VALUE : at;
        }));
        return dao;
    }

    public String prefill(String tab) {
        return prefill.get(tab);
    }

    public int size() {
        return patterns.size();
    }

    // line = the message as plain text. color codes and outer spaces are removed here
    public Optional<ChatMatch> match(String line) {
        if (line == null) return Optional.empty();
        String clean = line.replaceAll("§.", "").trim();
        for (ChatPattern p : patterns) {
            Matcher m = p.regex().matcher(clean);
            if (!m.find()) continue;
            Map<String, String> groups = new HashMap<>();
            for (String name : m.namedGroups().keySet()) {
                String value = m.group(name);
                if (value != null) groups.put(name, value);
            }
            return Optional.of(new ChatMatch(p.id(), p.category(), p.event(), Map.copyOf(groups)));
        }
        return Optional.empty();
    }
}
