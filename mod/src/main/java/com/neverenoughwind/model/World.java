package com.neverenoughwind.model;

// spawn is null when it was never seen. seeds stay strings, they're too big for some json readers.
// icon = the icon file to use, the lowercase name unless the data says otherwise.
// server = the subserver this world belongs to, the name unless the data says otherwise (chillspawn is on spawn)
public record World(String name, String hashedSeed, String dimension, int[] spawn, String icon, String server, String status) {
}
