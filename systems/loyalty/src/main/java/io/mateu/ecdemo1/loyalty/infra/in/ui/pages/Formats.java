package io.mateu.ecdemo1.loyalty.infra.in.ui.pages;

import io.mateu.ecdemo1.loyalty.store.Accrual;
import io.mateu.ecdemo1.loyalty.store.Tier;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.data.StatusType;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** How the screens write a member's data: in Spanish, as the desk reads it; never null (it would print "null"). */
final class Formats {

    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Europe/Madrid"));

    private Formats() {
    }

    static String day(LocalDate d) {
        return d == null ? "" : DAY.format(d);
    }

    static String when(Instant i) {
        return i == null ? "" : WHEN.format(i);
    }

    static String points(long points) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES")).format(points);
    }

    static Status tier(Tier tier) {
        if (tier == null) {
            return new Status(StatusType.NONE, "—");
        }
        return switch (tier) {
            case SILVER -> new Status(StatusType.NONE, "Silver");
            case GOLD -> new Status(StatusType.WARNING, "Gold");
            case PLATINUM -> new Status(StatusType.INFO, "Platinum");
        };
    }

    static AccrualRow row(Accrual a) {
        return new AccrualRow(a.memberNumber, a.stayId, a.hotelCode == null ? "" : a.hotelCode, a.nights,
                points(a.points), when(a.at));
    }
}
