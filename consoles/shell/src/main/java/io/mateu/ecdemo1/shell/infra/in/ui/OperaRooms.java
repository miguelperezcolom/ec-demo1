package io.mateu.ecdemo1.shell.infra.in.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.uidl.data.MatrixCell;
import io.mateu.uidl.data.MatrixColumn;
import io.mateu.uidl.data.MatrixGrid;
import io.mateu.uidl.data.MatrixRow;
import io.mateu.uidl.data.MatrixSection;
import io.mateu.uidl.data.MetricCard;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Opera's free rooms for the next nights, as pms-integration-service keeps them read: how many rooms
 * of each type Opera would still sell, night by night — counted up to a cap, past which it reads
 * «that many or more». The data plane's home shows what runs out (a tile), and its page the whole grid.
 * Read when the page renders, from inside the cluster; never fails the page.
 */
public final class OperaRooms {

    /** Where pms-integration-service answers inside the cluster; PMS_INTEGRATION_URL overrides it. */
    static final String BASE_URL = env("PMS_INTEGRATION_URL", "http://pms-integration-service:8123");
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    static final ObjectMapper JSON = new ObjectMapper();
    static final ZoneId ZONE = ZoneId.of("Europe/Madrid");
    static final Locale ES = Locale.forLanguageTag("es");
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");
    static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZONE);

    private OperaRooms() {
    }

    /** A night and each room type's free rooms; a type Opera sells none of that night is not in it. */
    record Night(LocalDate date, Map<String, Integer> free) {
    }

    /** What pms-integration answered: the property, the cap, every room type it sells, the nights. */
    record Calendar(String hotelId, int cap, List<String> roomTypes, List<Night> nights, Instant readAt) {

        int free(Night night, String type) {
            return night.free().getOrDefault(type, 0);
        }

        /** The room types with no room some night, each with its first and last such night. */
        Map<String, LocalDate[]> soldOut() {
            var out = new LinkedHashMap<String, LocalDate[]>();
            for (var type : roomTypes) {
                for (var night : nights) {
                    if (free(night, type) == 0) {
                        out.compute(type, (t, span) -> span == null ? new LocalDate[] {night.date(), night.date()}
                                : new LocalDate[] {span[0], night.date()});
                    }
                }
            }
            return out;
        }
    }

    /** The calendar of the property in service, or empty when pms-integration does not answer it. */
    static Optional<Calendar> read() {
        try {
            var response = HTTP.send(java.net.http.HttpRequest.newBuilder(URI.create(BASE_URL + "/availability/calendar?days=14"))
                    .timeout(Duration.ofSeconds(4)).header("Accept", "application/json").GET().build(), BodyHandlers.ofString());
            return response.statusCode() == 200 ? Optional.of(calendar(JSON.readTree(response.body()))) : Optional.empty();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    static Calendar calendar(JsonNode json) {
        var types = new ArrayList<String>();
        json.path("roomTypes").forEach(t -> types.add(t.asText()));
        var nights = new ArrayList<Night>();
        for (var n : json.path("nights")) {
            var free = new LinkedHashMap<String, Integer>();
            n.path("free").properties().forEach(e -> free.put(e.getKey(), e.getValue().asInt()));
            nights.add(new Night(LocalDate.parse(n.path("date").asText()), free));
        }
        var readAt = json.hasNonNull("readAt") ? Instant.parse(json.path("readAt").asText()) : null;
        return new Calendar(json.path("hotelId").asText(), json.path("cap").asInt(5), types, nights, readAt);
    }

    /** The home's tile: which room types run out in the next nights, and when. */
    static MetricCard kpi(Optional<Calendar> read, String actionId) {
        var card = MetricCard.builder().id("opera-rooms").title("OPERA — CUPO PRÓXIMAS 2 SEMANAS").icon("vaadin:calendar")
                .actionId(actionId);
        if (read.isEmpty()) {
            return card.value("—").description("El cupo de Opera no está disponible ahora").build();
        }
        var soldOut = read.get().soldOut();
        if (soldOut.isEmpty()) {
            return card.value("Con cupo").description("Todos los tipos de " + read.get().hotelId() + " tienen sitio cada noche").build();
        }
        var spans = new ArrayList<String>();
        soldOut.forEach((type, span) -> spans.add(type + " " + span(span)));
        return card.value(String.valueOf(soldOut.size()))
                .unit(soldOut.size() == 1 ? "tipo sin cupo alguna noche" : "tipos sin cupo alguna noche")
                .description(String.join(" · ", spans.subList(0, Math.min(4, spans.size()))) + (spans.size() > 4 ? " …" : ""))
                .build();
    }

    static String span(LocalDate[] span) {
        return span[0].equals(span[1]) ? span[0].format(DAY) : span[0].format(DAY) + "–" + span[1].format(DAY);
    }

    /** The whole grid: a row per room type, a column per night; none left in red, few in amber. */
    static MatrixGrid grid(Calendar calendar) {
        var columns = calendar.nights().stream().map(n -> new MatrixColumn(n.date().toString(),
                n.date().getDayOfWeek().getDisplayName(TextStyle.SHORT, ES) + " " + n.date().format(DAY))).toList();
        var rows = calendar.roomTypes().stream().map(type -> new MatrixRow(type, type, calendar.nights().stream().map(n -> {
            var free = calendar.free(n, type);
            return new MatrixCell(free >= calendar.cap() ? calendar.cap() + "+" : String.valueOf(free),
                    free == 0 ? "danger" : free <= 2 ? "warning" : null, false);
        }).toList(), false, false)).toList();
        return MatrixGrid.builder().id("operaRooms").rowHeaderLabel("Tipo de habitación").columns(columns)
                .sections(List.of(new MatrixSection("rooms", "", false, rows))).style("width: 100%;").build();
    }

    static String readAt(Calendar calendar) {
        return calendar.readAt() == null ? "" : WHEN.format(calendar.readAt());
    }

    static String env(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
