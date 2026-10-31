package io.mateu.ecdemo1.contenthub.infra.in.ui.common;

import io.mateu.ecdemo1.uicommons.html.Html;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** What the screens share: the zone dates are shown in, and small bits of markup. */
public final class Ui {

    public static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZONE);
    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONE);

    /** The portal's routes, as the data plane's shells mount it (RemoteMenu "intranet"). */
    public static final String PORTAL = "/intranet";

    private Ui() {
    }

    public static String day(Instant instant) {
        return instant == null ? "" : DAY.format(instant);
    }

    public static String when(Instant instant) {
        return instant == null ? "" : WHEN.format(instant);
    }

    public static Instant instant(LocalDateTime local) {
        return local == null ? null : local.atZone(ZONE).toInstant();
    }

    public static LocalDateTime local(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZONE);
    }

    public static String itemLink(String itemId) {
        return PORTAL + "/noticias/" + itemId;
    }

    public static String esc(String s) {
        return Html.escape(s);
    }

    public static String card(String inner) {
        return "<div style=\"border:1px solid rgba(128,128,128,.25);border-radius:12px;padding:1rem 1.25rem;"
                + "margin:0 0 1rem\">" + inner + "</div>";
    }

    public static String chip(String text, String color) {
        return "<span style=\"display:inline-block;padding:.1rem .55rem;border-radius:999px;font-size:.75rem;"
                + "background:" + color + ";color:#fff;margin-right:.35rem\">" + esc(text) + "</span>";
    }
}
