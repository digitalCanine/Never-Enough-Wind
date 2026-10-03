package com.neverenoughwind.dao;

import com.google.gson.JsonObject;
import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.model.Essence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EssenceDao {
    private final List<Essence> all = new ArrayList<>();
    private final Map<String, Essence> byId = new HashMap<>();
    private final Map<String, Essence> byName = new HashMap<>();

    public static EssenceDao load() {
        EssenceDao dao = new EssenceDao();
        for (JsonObject o : Json.objects(Json.load("essences.json").get("essences"))) {
            try {
                Essence e = new Essence(
                        Json.str(o, "id"), Json.str(o, "name"), Json.str(o, "key"), Json.str(o, "kind"),
                        Json.strings(o, "applies_to"), Json.intOrNull(o, "max_level"), Json.intOrNull(o, "global_cap"),
                        Json.str(o, "description"), Json.str(o, "details"),
                        Json.strings(o, "requirements"), Json.strings(o, "effects"),
                        Json.str(o, "item_type"), Json.str(o, "trigger"),
                        Json.stringMap(o, "price"), Json.stringMap(o, "level_text"), Json.str(o, "damage"), Json.strings(o, "also_called"), Json.str(o, "status"));
                if (e.id() == null || e.name() == null) continue;
                dao.all.add(e);
                dao.byId.putIfAbsent(e.id(), e);
                dao.byName.putIfAbsent(Json.key(e.name()), e);
                for (String alias : e.alsoCalled()) dao.byName.putIfAbsent(Json.key(alias), e);
            } catch (RuntimeException ex) {
                NeverEnoughWind.LOG.warn("skipped a bad essence entry: {}", ex.toString());
            }
        }
        return dao;
    }

    public List<Essence> all() {
        return List.copyOf(all);
    }

    public Optional<Essence> byId(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    // name as written on gear or books, spelling of spaces and hyphens doesnt matter
    public Optional<Essence> byName(String name) {
        return Optional.ofNullable(byName.get(Json.key(name)));
    }
}
