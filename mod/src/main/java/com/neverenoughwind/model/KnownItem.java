package com.neverenoughwind.model;

// vanilla = every item with that id counts. use = what its for, shown in the panel (can be null).
// event = came from an event, the date in its lore says which
public record KnownItem(String item, String name, String lore, String enchant, boolean vanilla, String use, boolean event) {
}
