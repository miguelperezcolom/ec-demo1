package io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * The window a budget's limit applies over, and — because a limit is meaningless without one — how
 * to find where the current window began. A daily budget resets at midnight UTC, a monthly one on
 * the first. UTC on purpose: a window that moved with the server's timezone would reset at a
 * different clock time in a different region, which is the sort of thing that is fine until it is a
 * billing dispute.
 */
public enum BudgetPeriod {
    DAY,
    MONTH;

    /** The start of the window that {@code now} falls in. Spend is summed from here. */
    public Instant currentWindowStart(Instant now) {
        var date = now.atZone(ZoneOffset.UTC);
        return switch (this) {
            case DAY -> date.truncatedTo(ChronoUnit.DAYS).toInstant();
            case MONTH -> date.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        };
    }
}
