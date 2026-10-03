package com.neverenoughwind.parse;

import java.util.List;

// before armor. atLeast = some essence adds damage we have no number for
public record Damage(double hit, double crit, boolean atLeast, List<String> parts, List<String> notCounted, List<String> sometimes) {
}
