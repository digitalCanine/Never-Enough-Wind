package com.neverenoughwind.model;

import java.util.regex.Pattern;

public record GearRules(
        String essenceHeader,
        Pattern essenceLine,
        Pattern triggerLine,
        Pattern effectLine,
        Pattern accumulates,
        Pattern consumes,
        Pattern soulbound) {
}
