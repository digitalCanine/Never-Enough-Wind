package com.neverenoughwind.model;

import java.util.regex.Pattern;

// any field can be null, null = dont check it
public record MatchRule(
        String item,
        Pattern name,
        Pattern lore,
        Pattern loreFirstLine,
        Pattern loreKind,
        String loreContains,
        Pattern fallbackName,
        String what) {
}
