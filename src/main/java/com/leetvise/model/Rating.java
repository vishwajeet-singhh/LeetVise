package com.leetvise.model;

import java.util.Locale;

/**
 * How a revision went. Drives the next review date (simple spaced repetition):
 * Again → tomorrow, Hard → ×1.2 (min 3d), Good → ×2 (min 7d), Easy → ×3 (min 14d). Capped at 180 days.
 */
public enum Rating {
    AGAIN(1), HARD(2), GOOD(3), EASY(4);

    public static final int MAX_INTERVAL_DAYS = 180;

    private final int confidence;

    Rating(int confidence) {
        this.confidence = confidence;
    }

    public int confidence() {
        return confidence;
    }

    public int nextInterval(int previousDays) {
        int prev = Math.max(previousDays, 1);
        int next = switch (this) {
            case AGAIN -> 1;
            case HARD -> Math.max(3, (int) Math.round(prev * 1.2));
            case GOOD -> Math.max(7, prev * 2);
            case EASY -> Math.max(14, prev * 3);
        };
        return Math.min(next, MAX_INTERVAL_DAYS);
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Rating parse(String s) {
        try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Rating must be one of again, hard, good, easy");
        }
    }
}
