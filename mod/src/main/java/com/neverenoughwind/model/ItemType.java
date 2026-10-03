package com.neverenoughwind.model;

import java.util.List;

// a highlighter type: rules to match by, or a fixed list of items
public record ItemType(String id, List<MatchRule> rules, List<KnownItem> items) {
}
