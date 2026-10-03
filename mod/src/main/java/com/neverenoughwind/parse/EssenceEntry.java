package com.neverenoughwind.parse;

import com.neverenoughwind.model.Essence;

// level 0 = the essence has no levels. essence is null when the name isnt in the data
public record EssenceEntry(String name, int level, Essence essence, String triggers, String effects) {
}
