package com.neverenoughwind.parse;

import java.util.List;
import java.util.Map;

// an item as plain data, the parser never sees minecraft objects.
// serverStyledName = the custom name has a color, anvil renames never do
public record ItemSnapshot(String item, String name, boolean serverStyledName, List<String> lore, Map<String, Integer> enchants) {
}
