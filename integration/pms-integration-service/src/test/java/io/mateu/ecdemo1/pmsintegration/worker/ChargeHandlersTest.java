package io.mateu.ecdemo1.pmsintegration.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.config.ChargeCodes;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import io.mateu.ecdemo1.pmsintegration.frontoffice.ReceptionOutcomes;
import io.mateu.ecdemo1.pmsintegration.ohip.OhipClient;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaFrontDesk;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaReservations;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import io.mateu.workflow.worker.api.Cancellations;
import io.mateu.workflow.worker.api.TaskDispatcher;
import io.mateu.workflow.worker.api.TaskRegistry;
import io.mateu.workflow.worker.api.TaskTracing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The desk's charges onto Opera's folio — against a fake of OHIP's cashiering that keeps the folio's
 * postings as XMAR does (a posting's reference is what it is found by): each step run through the
 * worker runtime as the contract it serves. What is checked: the posting written (transaction code,
 * amount, reference, cashier), that nothing is written twice for one folio line, the reversal, and
 * what a refusal or an early step becomes.
 */
class ChargeHandlersTest {

    record Call(String method, String path, String body) {
    }

    record Posted(String no, String code, BigDecimal amount, String reference) {
    }

    final List<Call> calls = new ArrayList<>();
    final List<Posted> folio = new ArrayList<>();
    String status = "InHouse";
    String refusal;
    HttpServer ohip;

    final IntegrationClients integration = mock(IntegrationClients.class);
    final ReceptionOutcomes outcomes = mock(ReceptionOutcomes.class);
    final PmsTasksTest.RecordingSink sink = new PmsTasksTest.RecordingSink();
    final ObjectMapper mapper = new ObjectMapper();
    TaskDispatcher dispatcher;

    @BeforeEach
    void start() throws IOException {
        ohip = HttpServer.create(new InetSocketAddress(0), 0);
        ohip.createContext("/oauth/v1/tokens", exchange -> reply(exchange, 200,
                "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":3600}"));
        ohip.createContext("/", exchange -> {
            var method = exchange.getRequestMethod();
            var uri = exchange.getRequestURI().toString();
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(new Call(method, uri, body));
            if (method.equals("GET") && uri.startsWith("/rsv/v1/hotels/XMAR/reservations/39486034")) {
                reply(exchange, 200, """
                        {"reservations": {"reservation": [{
                          "reservationIdList": [{"id": "39486034", "type": "Reservation"}],
                          "roomStay": {"currentRoomInfo": {"roomType": "SJMB", "roomId": "5142"},
                                       "arrivalDate": "2026-05-13", "departureDate": "2026-05-14"},
                          "reservationStatus": "%s"}]}}
                        """.formatted(status));
            } else if (method.equals("GET") && uri.startsWith("/csh/v1/hotels/XMAR/reservations/39486034/folios")) {
                // As XMAR: the postings only with summaryOnly=false; the windows' totals otherwise.
                reply(exchange, 200, uri.contains("summaryOnly=false") ? folioAnswer() : "{\"reservationFolioInformation\": {}}");
            } else if (method.equals("POST") && uri.equals("/csh/v1/hotels/XMAR/reservations/39486034/charges")) {
                if (refusal != null) {
                    reply(exchange, 400, refusal);
                    return;
                }
                var charge = mapper.readTree(body).path("criteria").path("charges").path(0);
                folio.add(new Posted(String.valueOf(88731245 + folio.size()), charge.path("transactionCode").asText(),
                        charge.path("price").path("amount").decimalValue(), charge.path("postingReference").asText()));
                reply(exchange, 201, "{}");
            } else {
                reply(exchange, 404, "{\"title\":\"Not found\"}");
            }
        });
        ohip.start();
        var connection = new OhipConnection("XMAR", "http://localhost:" + ohip.getAddress().getPort(), "app", "id", "secret", "RIUE");
        var properties = new OhipProperties("ECDEMO1-09280410", null, null, Duration.ofSeconds(2), null, null, false, null,
                null, "69721441");
        var client = new OhipClient(new Connections() {
            @Override
            public Optional<OhipConnection> of(String pmsHotelCode) {
                return Optional.of(connection);
            }

            @Override
            public List<IntegrationView> integrations() {
                return List.of();
            }
        }, properties, new TolerantReader(new ObjectMapper()), Clock.systemUTC());
        var codes = new ChargeCodes("1851", Map.of("LATE_CHECK_OUT", "1200", "CONSUMPTION:MB-02", "1402"));
        var handlers = new ChargeHandlers(new OperaFrontDesk(client, properties, mapper),
                new OperaReservations(client, properties, mapper), integration, outcomes, codes);
        var tasks = new PmsTasks();
        var watch = mock(RetryWatch.class);
        dispatcher = new TaskDispatcher(new TaskRegistry(List.of(tasks.postChargeTask(handlers, watch),
                tasks.reverseChargeTask(handlers, watch))), sink, Cancellations.NONE, false, TaskTracing.NOOP);
    }

    @AfterEach
    void stop() {
        ohip.stop(0);
    }

    /** The folio as OHIP gives it with its postings: nested in the window's folios. */
    String folioAnswer() {
        var postings = new StringBuilder();
        for (var p : folio) {
            if (!postings.isEmpty()) {
                postings.append(',');
            }
            postings.append("""
                    {"transactionNo": %s, "transactionCode": "%s", "postedAmount": {"amount": %s, "currencyCode": "MUR"},
                     "reference": "%s ", "remark": "x", "folioWindowNo": 1}""".formatted(p.no(), p.code(), p.amount(), p.reference()));
        }
        return """
                {"reservationFolioInformation": {"folioWindows": [
                  {"folioWindowNo": 1, "balance": {"amount": 0, "currencyCode": "MUR"},
                   "folios": [{"postings": [%s]}]},
                  {"folioWindowNo": 2, "emptyWindow": true, "balance": {"amount": 0, "currencyCode": "MUR"}}]}}
                """.formatted(postings);
    }

    static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    void run(String definition, String step, String taskId, String lineId, String kind, String code, String description,
             String amount) {
        var variables = new ArrayList<>(List.of(new Variable("definitionId", definition),
                new Variable("processKey", definition + ":MRU01/GSX4AK:" + lineId), new Variable("hotelCode", "MRU01"),
                new Variable("locator", "GSX4AK"), new Variable("pmsHotelCode", "XMAR"),
                new Variable("pmsReservationId", "39486034"), new Variable("stayId", "GSX4AK"),
                new Variable("eventId", "E-1"), new Variable("lineId", lineId), new Variable("chargeKind", kind),
                new Variable("description", description), new Variable("amount", amount), new Variable("currency", "MUR")));
        if (code != null) {
            variables.add(new Variable("chargeCode", code));
        }
        dispatcher.dispatch(new TaskExecutionRequested("TE-1", "PROC-1", definition, step, taskId, variables));
    }

    void postLateCheckOut() {
        run("registrar-cargo", "post-charge", "post-charge@1", "L-1", "LATE_CHECK_OUT", null,
                "Late check-out (salida 15:00)", "50.00");
    }

    List<Call> writes() {
        return calls.stream().filter(c -> !c.method().equals("GET")).toList();
    }

    @Test
    void aChargeOfTheDeskIsPostedToOperasFolioWithItsTransactionCodeAndTheLineAsReference() throws Exception {
        postLateCheckOut();

        assertThat(sink.replies).containsExactly("COMPLETED [chargeOutcome=DONE, pmsReservationId=39486034, pmsPostingId=88731245]");
        assertThat(writes()).singleElement().satisfies(w -> {
            assertThat(w.path()).isEqualTo("/csh/v1/hotels/XMAR/reservations/39486034/charges");
            var charge = mapper.readTree(w.body()).path("criteria").path("charges").path(0);
            assertThat(charge.path("transactionCode").asText()).isEqualTo("1200");
            assertThat(charge.path("price").path("amount").decimalValue()).isEqualByComparingTo("50.00");
            assertThat(charge.path("price").path("currencyCode").asText()).isEqualTo("MUR");
            assertThat(charge.path("postingReference").asText()).isEqualTo("FO:L-1");
            assertThat(charge.path("postingRemark").asText()).isEqualTo("Late check-out (salida 15:00)");
            // The cashier on the criteria, as Oracle's examples; no reservation nor window in the body.
            var criteria = mapper.readTree(w.body()).path("criteria");
            assertThat(criteria.path("cashierId").asLong()).isEqualTo(69721441L);
            assertThat(criteria.has("reservationId")).isFalse();
            assertThat(charge.has("folioWindowNo")).isFalse();
        });
        verify(outcomes).charge("XMAR", "39486034", "GSX4AK", "L-1", false, false, "En el folio de Opera", "88731245");
        // A void of it that waited for it to be there has it now.
        verify(integration).resolveCauseIfOpen(eq("PMS_REJECTED:MRU01:GSX4AK:reverse-charge"), anyString());
    }

    @Test
    void aConsumptionTakesItsOwnTransactionCodeAndAnythingElseTheDefault() throws Exception {
        run("registrar-cargo", "post-charge", "post-charge@1", "L-2", "CONSUMPTION", "MB-02", "Minibar", "12.50");
        run("registrar-cargo", "post-charge", "post-charge@1", "L-3", "CONSUMPTION", "SPA-03", "Masaje", "90.00");

        assertThat(writes()).extracting(w -> mapper.readTree(w.body()).path("criteria").path("charges").path(0)
                .path("transactionCode").asText()).containsExactly("1402", "1851");
    }

    @Test
    void theSameLineTwiceIsPostedOnce() {
        postLateCheckOut();
        postLateCheckOut();

        assertThat(sink.replies).containsExactly(
                "COMPLETED [chargeOutcome=DONE, pmsReservationId=39486034, pmsPostingId=88731245]",
                "COMPLETED [chargeOutcome=STALE, pmsReservationId=39486034, pmsPostingId=88731245]");
        assertThat(writes()).hasSize(1);
        assertThat(folio).hasSize(1);
    }

    @Test
    void aVoidIsTheChargeReversedWithTheSameTransactionCodeAndOnlyOnce() throws Exception {
        run("registrar-cargo", "post-charge", "post-charge@1", "L-2", "CONSUMPTION", "MB-02", "Minibar", "12.50");

        run("anular-cargo", "reverse-charge", "reverse-charge@1", "L-2", "CONSUMPTION", "MB-02", "Minibar", "12.50");
        run("anular-cargo", "reverse-charge", "reverse-charge@1", "L-2", "CONSUMPTION", "MB-02", "Minibar", "12.50");

        assertThat(sink.replies).containsExactly(
                "COMPLETED [chargeOutcome=DONE, pmsReservationId=39486034, pmsPostingId=88731245]",
                "COMPLETED [reversalOutcome=DONE, pmsReservationId=39486034, pmsReversalId=88731246]",
                "COMPLETED [reversalOutcome=STALE, pmsReservationId=39486034, pmsReversalId=88731246]");
        assertThat(writes()).hasSize(2);
        var reversal = mapper.readTree(writes().get(1).body()).path("criteria").path("charges").path(0);
        assertThat(reversal.path("transactionCode").asText()).isEqualTo("1402");
        assertThat(reversal.path("price").path("amount").decimalValue()).isEqualByComparingTo("-12.50");
        assertThat(reversal.path("postingReference").asText()).isEqualTo("FO:L-2:R");
        // What Opera's folio adds up to for the line: nothing.
        assertThat(folio.stream().map(Posted::amount).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("0");
        // Told each time: the front office writes it by state.
        verify(outcomes, org.mockito.Mockito.times(2)).charge("XMAR", "39486034", "GSX4AK", "L-2", true, false,
                "Anulado en el folio de Opera", "88731246");
    }

    @Test
    void aVoidOfAChargeOperaDoesNotHaveYetWaitsForIt() {
        run("anular-cargo", "reverse-charge", "reverse-charge@1", "L-2", "CONSUMPTION", "MB-02", "Minibar", "12.50");

        assertThat(sink.replies).containsExactly("COMPLETED [reversalOutcome=WAIT, pmsReservationId=39486034]");
        assertThat(writes()).isEmpty();
        var causes = ArgumentCaptor.forClass(List.class);
        var variables = ArgumentCaptor.forClass(List.class);
        verify(integration).await(eq("anular-cargo:MRU01/GSX4AK:L-2"), eq("anular-cargo"), eq("MRU01"), eq("GSX4AK"),
                variables.capture(), causes.capture());
        assertThat(((Cause) causes.getValue().getFirst()).key()).isEqualTo("PMS_REJECTED:MRU01:GSX4AK:reverse-charge");
        // Relaunched, it is the same void: the line and its figures go with it.
        assertThat(variables.getValue()).contains(new Variable("lineId", "L-2"), new Variable("amount", "12.50"),
                new Variable("chargeKind", "CONSUMPTION"), new Variable("chargeCode", "MB-02"));
    }

    @Test
    void aChargeForAReservationOperaHasNotCheckedInYetWaitsForTheCheckIn() {
        status = "Reserved";

        postLateCheckOut();

        assertThat(sink.replies).containsExactly("COMPLETED [chargeOutcome=WAIT, pmsReservationId=39486034]");
        assertThat(writes()).isEmpty();
        var causes = ArgumentCaptor.forClass(List.class);
        verify(integration).await(eq("registrar-cargo:MRU01/GSX4AK:L-1"), eq("registrar-cargo"), eq("MRU01"), eq("GSX4AK"),
                any(), causes.capture());
        var cause = (Cause) causes.getValue().getFirst();
        assertThat(cause.key()).isEqualTo("PMS_REJECTED:MRU01:GSX4AK:post-charge");
        assertThat(cause.description()).contains("does not have it in house").contains("NOT_IN_HOUSE");
        verify(outcomes).charge(eq("XMAR"), eq("39486034"), eq("GSX4AK"), eq("L-1"), eq(false), eq(true), anyString(), eq(null));
    }

    @Test
    void operaRefusingTheChargeIsACauseAndTheDeskIsToldWhy() {
        refusal = """
                {"type":"Bad Request","title":"Transaction code 1200 is not valid for posting.","o:errorCode":"FOF01234"}
                """;

        postLateCheckOut();

        assertThat(sink.replies).containsExactly("COMPLETED [chargeOutcome=WAIT, pmsReservationId=39486034]");
        verify(integration).await(eq("registrar-cargo:MRU01/GSX4AK:L-1"), eq("registrar-cargo"), eq("MRU01"), eq("GSX4AK"),
                any(), any());
        verify(outcomes).charge(eq("XMAR"), eq("39486034"), eq("GSX4AK"), eq("L-1"), eq(false), eq(true),
                org.mockito.ArgumentMatchers.contains("not valid for posting"), eq(null));
        verify(integration, never()).resolveCauseIfOpen(eq("PMS_REJECTED:MRU01:GSX4AK:reverse-charge"), anyString());
    }

    @Test
    void aChargeForAReservationOperaHasCancelledIsNotPostedNorWaitedFor() {
        status = "Cancelled";

        postLateCheckOut();

        assertThat(sink.replies).containsExactly("COMPLETED [chargeOutcome=STALE, pmsReservationId=39486034]");
        assertThat(writes()).isEmpty();
        verify(integration, never()).await(any(), any(), any(), any(), any(), any());
    }
}
