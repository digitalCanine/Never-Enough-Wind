package com.neverenoughwind.model;

import java.util.regex.Pattern;

// event is only set for event lines (which event it belongs to)
public record ChatPattern(String id, String category, String event, Pattern regex, String status) {
}
