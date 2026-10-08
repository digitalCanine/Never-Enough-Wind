package com.neverenoughwind.parse;

import java.util.Map;

// a chat line that hit a pattern, with the pattern's named groups. line = the text that was matched
public record ChatMatch(String id, String category, String event, Map<String, String> groups, String line) {
    public String get(String group) {
        return groups.get(group);
    }
}
