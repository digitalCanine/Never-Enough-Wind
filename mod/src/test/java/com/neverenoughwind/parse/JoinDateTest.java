package com.neverenoughwind.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JoinDateTest {
    // 2026-10-04 is day 20730 since 1970
    private static final long TODAY = 20730;

    @Test
    void aDateCountedFrom1970IsLost() {
        assertTrue(JoinDate.lost("First joined: 20730 days ago", TODAY));
        assertTrue(JoinDate.lost("First joined: 20729 days ago", TODAY));
    }

    @Test
    void realDatesAndOtherLinesAreLeftAlone() {
        assertFalse(JoinDate.lost("First joined: 412 days ago", TODAY));
        assertFalse(JoinDate.lost("First joined: 5000 days ago", TODAY));
        assertFalse(JoinDate.lost("Likes: 20730", TODAY));
        assertFalse(JoinDate.lost(null, TODAY));
    }
}
