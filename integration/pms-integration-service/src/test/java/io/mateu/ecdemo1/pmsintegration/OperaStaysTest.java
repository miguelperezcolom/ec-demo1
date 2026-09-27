package io.mateu.ecdemo1.pmsintegration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.pms.PmsReservationStamp;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import io.mateu.ecdemo1.pmsintegration.frontoffice.OperaStays;
import io.mateu.ecdemo1.pmsintegration.ohip.OhipClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How a change made in Opera itself is found: the window's reservations, paged as OHIP pages them,
 * and those modified at or after the cursor. OHIP has no «modified since» filter — every page is read
 * and each reservation's {@code lastModifyDateTime} compared.
 */
class OperaStaysTest {

    HttpServer ohip;
    final List<String> calls = new ArrayList<>();
    OperaStays stays;

    /** Two pages, as XMAR answers them: 200 at most each, {@code hasMore} and the offset to ask next. */
    static String page(String uri) {
        var second = uri.contains("offset=2");
        var infos = second
                ? info("39484700", "2026-11-20", "2026-11-22", "Cancelled", "2026-09-28 09:15:00.0")
                : info("28843213", "2026-09-20", "2026-09-29", "Reserved", "2026-03-13 22:01:45.0") + ","
                  + info("39484601", "2026-11-10", "2026-11-12", "Reserved", "2026-09-27 21:57:59.0");
        return """
                {"reservations": {"reservationInfo": [%s], "offset": %d, "limit": 2, "hasMore": %s, "totalResults": 3}}
                """.formatted(infos, second ? 3 : 2, second ? "false" : "true");
    }

    static String info(String id, String arrival, String departure, String status, String modified) {
        return """
                {"reservationIdList": [{"id": "%s", "type": "Reservation"}, {"id": "C%s", "type": "Confirmation"}],
                 "roomStay": {"arrivalDate": "%s", "departureDate": "%s"},
                 "reservationStatus": "%s", "createDateTime": "2026-01-01 00:00:00.0", "lastModifyDateTime": "%s"}
                """.formatted(id, id, arrival, departure, status, modified);
    }

    @BeforeEach
    void start() throws Exception {
        ohip = HttpServer.create(new InetSocketAddress(0), 0);
        ohip.createContext("/oauth/v1/tokens", exchange -> reply(exchange,
                "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        ohip.createContext("/", exchange -> {
            var uri = exchange.getRequestURI().toString();
            calls.add(uri);
            reply(exchange, page(uri));
        });
        ohip.start();
        var connection = new OhipConnection("XMAR", "http://localhost:" + ohip.getAddress().getPort(), "app", "id", "secret", "RIUE");
        var client = new OhipClient(new Connections() {
            @Override
            public Optional<OhipConnection> of(String pmsHotelCode) {
                return Optional.of(connection);
            }

            @Override
            public List<IntegrationView> integrations() {
                return List.of();
            }
        }, new OhipProperties(null, null, null, Duration.ofSeconds(2), null, null, null, null, null),
                new TolerantReader(new ObjectMapper()), Clock.systemUTC());
        stays = new OperaStays(client, new OhipProperties(null, null, null, null, null, null, null, null, "EC-DEMO1"));
    }

    @AfterEach
    void stop() {
        ohip.stop(0);
    }

    static void reply(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void theBackfillReadsEveryPageOfTheWindowOldestModificationFirst() {
        var all = stays.window("XMAR", LocalDate.of(2026, 9, 27), LocalDate.of(2026, 11, 26), OperaStays.Scope.ALL, null);
        assertThat(all).extracting(PmsReservationStamp::pmsReservationId).containsExactly("28843213", "39484601", "39484700");
        assertThat(all.get(1)).isEqualTo(new PmsReservationStamp("39484601", "C39484601", LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 12), "Reserved", "2026-09-27T21:57:59"));
        assertThat(calls).hasSize(2);
        assertThat(calls.getFirst()).contains("departureStartDate=2026-09-27").contains("arrivalEndDate=2026-11-26")
                .contains("offset=0").doesNotContain("customReference");
        assertThat(calls.get(1)).contains("offset=2");
    }

    @Test
    void aPollFindsWhatWasModifiedAtOrAfterItsCursor_aChangeMadeInOperaIncluded() {
        var changed = stays.window("XMAR", LocalDate.of(2026, 9, 27), LocalDate.of(2026, 11, 26), OperaStays.Scope.ALL,
                "2026-09-27T21:57:59");
        // The one modified exactly at the cursor again — the engine takes its process once — and the
        // cancellation made in Opera after it.
        assertThat(changed).extracting(PmsReservationStamp::pmsReservationId).containsExactly("39484601", "39484700");
        assertThat(changed.get(1).status()).isEqualTo("Cancelled");
    }

    @Test
    void nothingModifiedSinceTheCursorIsNothing() {
        assertThat(stays.window("XMAR", LocalDate.of(2026, 9, 27), LocalDate.of(2026, 11, 26), OperaStays.Scope.ALL,
                "2026-09-28T09:15:01")).isEmpty();
    }

    @Test
    void theChainsScopeAsksOnlyForWhatTheIntegrationWrote() {
        stays.window("XMAR", LocalDate.of(2026, 9, 27), LocalDate.of(2026, 11, 26), OperaStays.Scope.CHAIN, null);
        assertThat(calls.getFirst()).contains("customReference=EC-DEMO1");
    }
}
