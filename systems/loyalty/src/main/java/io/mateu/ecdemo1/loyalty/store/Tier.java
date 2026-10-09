package io.mateu.ecdemo1.loyalty.store;

/**
 * A member's tier in Riu Class, from the lowest. The points a tier starts at: SILVER below 10 000, GOLD
 * from 10 000, PLATINUM from 40 000.
 */
public enum Tier {
    SILVER(0), GOLD(10_000), PLATINUM(40_000);

    public final long from;

    Tier(long from) {
        this.from = from;
    }

    /** The tier the points alone are worth. */
    public static Tier byPoints(long points) {
        var tier = SILVER;
        for (var t : values()) {
            if (points >= t.from) {
                tier = t;
            }
        }
        return tier;
    }

    /** The higher of the two; a null counts as nothing. */
    public static Tier max(Tier a, Tier b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** The tier by its name, case and blanks aside; null for a blank one. */
    public static Tier parse(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown tier: " + name + " (SILVER, GOLD or PLATINUM)");
        }
    }
}
