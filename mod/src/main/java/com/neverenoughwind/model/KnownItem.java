package com.neverenoughwind.model;

// vanilla = every item with that id counts
public record KnownItem(String item, String name, String lore, String enchant, boolean vanilla) {
}
