package com.neverenoughwind.model;

import java.util.List;
import java.util.Map;

// maxLevel = one book, globalCap = all gear together. either can be unknown (null).
// levelText = what a level does where thats known, key "0" for essences without levels.
// damage = "unknown" or "situational" for melee damage essences, else null
public record Essence(
        String id,
        String name,
        String key,
        String kind,
        List<String> appliesTo,
        Integer maxLevel,
        Integer globalCap,
        String description,
        String details,
        List<String> requirements,
        List<String> effects,
        String itemType,
        String trigger,
        Map<String, String> price,
        Map<String, String> levelText,
        String damage,
        List<String> alsoCalled,
        String status) {
}
