package com.neverenoughwind.model;

// deggs are null when the tier has no number (bidding wars)
public record PriceTier(String letter, String text, Integer minDeggs, Integer maxDeggs) {
    public boolean hasNumber() {
        return minDeggs != null && maxDeggs != null;
    }
}
