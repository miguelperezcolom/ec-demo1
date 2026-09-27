package io.mateu.ecdemo1.journey.ui;

import io.mateu.ecdemo1.journey.application.BookingJourney;
import io.mateu.ecdemo1.journey.business.BusinessData;
import io.mateu.ecdemo1.journey.model.CauseText;
import io.mateu.ecdemo1.journey.model.Durations;
import io.mateu.ecdemo1.journey.model.Hop;
import io.mateu.ecdemo1.journey.model.Journey;
import io.mateu.ecdemo1.journey.model.Lane;
import io.mateu.ecdemo1.journey.model.Tone;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.mateu.ecdemo1.uicommons.html.Html.escape;
import static io.mateu.ecdemo1.uicommons.html.Html.link;

/**
 * The journey drawn as lanes: one row per system, each hop a bar on a shared time axis from the
 * change in the CRS, with the moments Opera and the front office had it marked across all lanes.
 * Then the booking's changes, newest first, each a link to its own journey; the causes it waited
 * on; and the links an engineer follows — the trace in Grafana, the processes in the engine.
 *
 * <p>Markup, not components: it is the one Element of its screen (Mateu gives every Element the
 * same id), drawn the same by Vaadin and Redwood. Every piece of data is escaped. Colours are the
 * lanes' own, mid-tone, legible on a light page and a dark one; the rest inherits the theme.
 */
final class JourneyMarkup {

    static final String MUTED = "opacity: .7;";
    static final String RULE = "border-bottom: 1px solid rgba(128, 128, 128, .25);";
    static final String CELL = "text-align: left; padding: .35rem .5rem; " + RULE + " vertical-align: top;";
    static final String HEAD = CELL + " font-weight: 600; opacity: .75;";
    static final String HEADING = "margin: 1.25rem 0 .4rem; font-size: 1rem; font-weight: 600;";

    /** The width of a character of a bar's label, as a share of the track, to keep labels from overlapping. */
    static final double CHAR_SHARE = 0.62;

    private JourneyMarkup() {
    }

    static String of(BookingJourney booking, Journey change, String grafanaUrl, ZoneId zone) {
        var html = new StringBuilder("<div style=\"width: 100%; font-size: .875rem; line-height: 1.35;\">");
        if (change != null) {
            html.append(lanes(change, booking.business(), zone));
            html.append(links(change, grafanaUrl));
        }
        html.append(changes(booking, change, grafanaUrl, zone));
        html.append(causes(booking.business(), zone));
        return html.append("</div>").toString();
    }

    // ── the lanes ────────────────────────────────────────────────────────────────────────────────

    static String lanes(Journey change, BusinessData business, ZoneId zone) {
        var hops = change.hops().stream().filter(Hop::timed).toList();
        if (hops.isEmpty()) {
            return muted("Esta traza aún no tiene pasos que contar.");
        }
        var start = change.startNanos();
        var end = Math.max(change.endNanos(), hops.stream().mapToLong(Hop::endNanos).max().orElse(start + 1));
        var span = Math.max(1, end - start);
        var html = new StringBuilder();
        html.append("<div style=\"display: grid; grid-template-columns: minmax(8rem, 12rem) 1fr; column-gap: .75rem; ")
                .append("align-items: stretch; margin: .25rem 0 .5rem;\">");
        // The axis.
        html.append("<div></div><div style=\"position: relative; height: 1.4rem; ").append(RULE).append(MUTED).append(" font-size: .75rem;\">");
        for (int i = 0; i <= 4; i++) {
            var at = i * 25;
            var align = i == 0 ? "" : i == 4 ? " transform: translateX(-100%);" : " transform: translateX(-50%);";
            html.append("<span style=\"position: absolute; left: ").append(at).append("%;").append(align).append(" white-space: nowrap;\">")
                    .append(escape(i == 0 ? "0 s" : Durations.words(Duration.ofNanos(span * i / 4)))).append("</span>");
        }
        html.append("</div>");
        for (var lane : Lane.values()) {
            var laneHops = hops.stream().filter(h -> h.lane() == lane).toList();
            if (laneHops.isEmpty()) {
                continue;
            }
            var rows = pack(laneHops, start, span);
            var height = Math.max(1, rows.stream().mapToInt(r -> r).max().orElse(0) + 1);
            html.append("<div style=\"padding: .45rem 0; ").append(RULE).append("\">")
                    .append("<div style=\"display: flex; align-items: center; gap: .4rem; font-weight: 600;\">")
                    .append("<span style=\"width: .7rem; height: .7rem; border-radius: 50%; flex: 0 0 auto; background: ")
                    .append(lane.color()).append(";\"></span>").append(escape(lane.label())).append("</div>")
                    .append("<div style=\"").append(MUTED).append(" font-size: .75rem; margin-left: 1.1rem;\">")
                    .append(escape(lane.description())).append("</div></div>");
            html.append("<div style=\"position: relative; ").append(RULE).append(" height: ").append(height * 1.7 + 0.6)
                    .append("rem;\">");
            markers(html, change, start, span);
            for (int i = 0; i < laneHops.size(); i++) {
                var hop = laneHops.get(i);
                var left = share(hop.startNanos() - start, span);
                var width = Math.max(0.5, share(hop.endNanos() - hop.startNanos(), span));
                var top = rows.get(i) * 1.7 + 0.3;
                var color = hop.tone() == Tone.ERROR ? "#dc2626" : hop.tone() == Tone.WAIT ? "#d97706" : lane.color();
                var tip = hop.title() + " · +" + Durations.words(Duration.ofNanos(hop.startNanos() - start)) + " · "
                        + Durations.words(Duration.ofMillis(hop.durationMillis())) + (hop.detail() == null ? "" : " · " + hop.detail());
                html.append("<div title=\"").append(escape(tip)).append("\" style=\"position: absolute; top: ").append(top)
                        .append("rem; left: ").append(pct(left)).append("; width: ").append(pct(width))
                        .append("; height: .55rem; margin-top: .45rem; border-radius: .3rem; background: ").append(color)
                        .append(";").append(hop.tone() == Tone.WAIT ? " background-image: repeating-linear-gradient(45deg, rgba(255,255,255,.35) 0 4px, transparent 4px 8px);" : "")
                        .append("\"></div>");
                var label = hop.title() + " · " + Durations.words(Duration.ofMillis(hop.durationMillis()));
                var rightSide = left + width < 62;
                html.append("<div style=\"position: absolute; top: ").append(top).append("rem; ")
                        .append(rightSide ? "left: " + pct(left + width + 0.6) + ";" : "right: " + pct(100 - left + 0.6) + "; text-align: right;")
                        .append(" white-space: nowrap; font-size: .78rem;\">").append(escape(label)).append("</div>");
            }
            html.append("</div>");
        }
        html.append("</div>");
        html.append(legend(change));
        return html.toString();
    }

    /** The moments Opera and the front office had the change, across every lane. */
    static void markers(StringBuilder html, Journey change, long start, long span) {
        if (change.crsToOpera() != null) {
            marker(html, share(change.crsToOpera().toNanos(), span), Lane.OPERA.color());
        }
        if (change.crsToFrontOffice() != null) {
            marker(html, share(change.crsToFrontOffice().toNanos(), span), Lane.FRONT_OFFICE.color());
        }
    }

    static void marker(StringBuilder html, double at, String color) {
        html.append("<div style=\"position: absolute; top: 0; bottom: 0; left: ").append(pct(at))
                .append("; border-left: 2px dashed ").append(color).append("; opacity: .55;\"></div>");
    }

    static String legend(Journey change) {
        var parts = new ArrayList<String>();
        if (change.crsToOpera() != null) {
            parts.add(swatch(Lane.OPERA.color(), true) + "En Opera a los " + escape(Durations.words(change.crsToOpera())));
        }
        if (change.crsToFrontOffice() != null) {
            parts.add(swatch(Lane.FRONT_OFFICE.color(), true) + "En el front office a los " + escape(Durations.words(change.crsToFrontOffice())));
        }
        parts.add(swatch("#d97706", false) + "Esperó (un candado, una causa, un reintento)");
        parts.add(swatch("#dc2626", false) + "Falló");
        return "<div style=\"display: flex; flex-wrap: wrap; gap: .4rem 1.25rem; font-size: .78rem; " + MUTED + " margin: .2rem 0 .75rem;\">"
                + String.join("", parts.stream().map(p -> "<span style=\"display: inline-flex; align-items: center; gap: .35rem;\">" + p + "</span>").toList())
                + "</div>";
    }

    static String swatch(String color, boolean dashed) {
        return dashed
                ? "<span style=\"display: inline-block; width: 0; height: .9rem; border-left: 2px dashed " + color + ";\"></span>"
                : "<span style=\"display: inline-block; width: .9rem; height: .5rem; border-radius: .25rem; background: " + color + ";\"></span>";
    }

    /**
     * Which row of its lane each hop goes on: the first where neither its bar nor its label runs into
     * one already there. The label's width is guessed from its length.
     */
    static List<Integer> pack(List<Hop> hops, long start, long span) {
        var rows = new ArrayList<Integer>();
        var occupied = new ArrayList<List<double[]>>();
        for (var hop : hops) {
            var left = share(hop.startNanos() - start, span);
            var width = Math.max(0.5, share(hop.endNanos() - hop.startNanos(), span));
            var label = (hop.title().length() + 10) * CHAR_SHARE;
            var from = left + width < 62 ? left : left - label - 0.6;
            var to = left + width < 62 ? left + width + 0.6 + label : left + width;
            var row = 0;
            while (true) {
                if (row == occupied.size()) {
                    occupied.add(new ArrayList<>());
                }
                var clash = false;
                for (var taken : occupied.get(row)) {
                    if (from < taken[1] && to > taken[0]) {
                        clash = true;
                        break;
                    }
                }
                if (!clash) {
                    occupied.get(row).add(new double[]{from, to});
                    rows.add(row);
                    break;
                }
                row++;
            }
        }
        return rows;
    }

    static double share(long nanos, long span) {
        return Math.min(100, Math.max(0, nanos * 100.0 / span));
    }

    static String pct(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value);
    }

    // ── links ────────────────────────────────────────────────────────────────────────────────────

    static String links(Journey change, String grafanaUrl) {
        var items = new ArrayList<String>();
        items.add(link("Ver traza técnica (Grafana · Tempo)", grafana(grafanaUrl, change.traceId())));
        for (var process : change.processes()) {
            if (process.id() != null) {
                items.add(link("Proceso «" + process.name() + "» en el motor", "/workflow/processes/" + process.id()));
            }
        }
        return "<div style=\"display: flex; flex-wrap: wrap; gap: .4rem 1.25rem; margin: .25rem 0 .5rem;\">"
                + String.join("", items.stream().map(i -> "<span>" + i + "</span>").toList()) + "</div>";
    }

    /** The trace in Grafana's Explore, on the Tempo datasource (uid "tempo", deploy/observability). */
    static String grafana(String grafanaUrl, String traceId) {
        if (grafanaUrl == null || grafanaUrl.isBlank()) {
            return null;
        }
        var panes = "{\"t\":{\"datasource\":\"tempo\",\"queries\":[{\"refId\":\"A\",\"datasource\":{\"type\":\"tempo\",\"uid\":\"tempo\"},"
                + "\"queryType\":\"traceql\",\"limit\":20,\"tableType\":\"traces\",\"query\":\"" + traceId + "\"}],"
                + "\"range\":{\"from\":\"now-7d\",\"to\":\"now\"}}}";
        return grafanaUrl.replaceAll("/+$", "") + "/explore?schemaVersion=1&orgId=1&panes="
                + URLEncoder.encode(panes, StandardCharsets.UTF_8);
    }

    // ── the booking's changes ────────────────────────────────────────────────────────────────────

    static String changes(BookingJourney booking, Journey shown, String grafanaUrl, ZoneId zone) {
        if (booking.changes().isEmpty()) {
            return "";
        }
        var when = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(zone);
        var rows = new StringBuilder();
        for (var change : booking.changes()) {
            var current = change == shown;
            var number = booking.ordinal(change);
            var what = change.kind().label() + (change.version() == null ? "" : " · v" + change.version());
            rows.append("<tr").append(current ? " style=\"font-weight: 600;\"" : "").append(">")
                    .append(td(escape(String.valueOf(number)) + (current ? " ◂" : "")))
                    .append(td(escape(what)))
                    .append(td(escape(when.format(instant(change.startNanos())))))
                    .append(td(escape(Durations.words(change.crsToOpera()))))
                    .append(td(escape(Durations.words(change.crsToFrontOffice()))))
                    .append(td(escape(change.outcome().label())))
                    .append(td(current ? escape("Mostrado arriba")
                            : link("Ver este cambio", JourneyView.route(booking.locator(), change.traceId()))))
                    .append(td(link("Traza", grafana(grafanaUrl, change.traceId()))))
                    .append("</tr>");
        }
        return "<h4 style=\"" + HEADING + "\">" + escape("Cambios de la reserva (" + booking.changes().size()
                + "), el más reciente primero") + "</h4>"
                + "<table style=\"width: 100%; border-collapse: collapse;\"><thead><tr>"
                + th("#") + th("Qué") + th("Cuándo") + th("Hasta Opera") + th("Hasta el front office") + th("Estado") + th("") + th("")
                + "</tr></thead><tbody>" + rows + "</tbody></table>";
    }

    static String causes(BusinessData business, ZoneId zone) {
        if (business == null || business.causes().isEmpty()) {
            return "";
        }
        var rows = new StringBuilder();
        for (var cause : business.causes()) {
            rows.append("<tr>")
                    .append(td(escape(capitalize(CauseText.of(cause.key(), cause.description())))))
                    .append(td(escape(cause.open() ? "Abierta: la reserva espera" : "Resuelta")))
                    .append(td(escape(moment(cause.openedAt(), zone))))
                    .append(td(escape(cause.open() ? "" : moment(cause.resolvedAt(), zone)
                            + (cause.resolvedBy() == null ? "" : " · " + cause.resolvedBy()))))
                    .append("</tr>");
        }
        return "<h4 style=\"" + HEADING + "\">Causas de esta reserva</h4>"
                + "<table style=\"width: 100%; border-collapse: collapse;\"><thead><tr>"
                + th("Causa") + th("Estado") + th("Abierta") + th("Resuelta") + "</tr></thead><tbody>" + rows + "</tbody></table>";
    }

    // ── bits ─────────────────────────────────────────────────────────────────────────────────────

    static String moment(String iso, ZoneId zone) {
        if (iso == null || iso.isBlank() || "null".equals(iso)) {
            return "";
        }
        try {
            return DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(zone).format(Instant.parse(iso));
        } catch (RuntimeException e) {
            return iso;
        }
    }

    static Instant instant(long nanos) {
        return Instant.ofEpochSecond(nanos / 1_000_000_000L, nanos % 1_000_000_000L);
    }

    static String capitalize(String text) {
        return text == null || text.isEmpty() ? "" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    static String muted(String text) {
        return "<p style=\"" + MUTED + " margin: .25rem 0 1rem;\">" + escape(text) + "</p>";
    }

    static String th(String text) {
        return "<th style=\"" + HEAD + "\">" + escape(text) + "</th>";
    }

    static String td(String markup) {
        return "<td style=\"" + CELL + "\">" + markup + "</td>";
    }
}
