package com.neverenoughwind.parse;

import java.util.Map;

// a chat line that hit a pattern, with the pattern's named groups
public record ChatMatch(String id, String category, String event, Map<String, String> groups) {
    public String get(String group) {
        return groups.get(group);
    }
}
