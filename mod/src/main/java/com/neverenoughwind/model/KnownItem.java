package com.neverenoughwind.model;

// vanilla = every item with that id counts. use = what its for, shown in the panel (can be null)
public record KnownItem(String item, String name, String lore, String enchant, boolean vanilla, String use) {
}
