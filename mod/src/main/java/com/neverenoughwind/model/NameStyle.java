package com.neverenoughwind.model;

// solid color, or a gradient from the first letter to the last
public record NameStyle(String color, String gradientFrom, String gradientTo, boolean bold) {
    public boolean isGradient() {
        return gradientFrom != null && gradientTo != null;
    }
}
