package io.mateu.ecdemo1.pmsintegration.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
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
class PaymentHandlersTest {

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
            } else if (method.equals("POST") && uri.equals("/csh/v1/hotels/XMAR/reservations/39486034/payments")) {
                if (refusal != null) {
                    reply(exchange, 400, refusal);
                    return;
                }
                var criteria = mapper.readTree(body).path("criteria");
                folio.add(new Posted(String.valueOf(88731245 + folio.size()), "9000",
                        criteria.path("postingAmount").path("amount").decimalValue(), criteria.path("postingReference").asText()));
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
        var methods = new io.mateu.ecdemo1.pmsintegration.config.PaymentMethods("CASH",
                Map.of("CASH", "CASH", "TRANSFER", "BT", "CARD_PINPAD", "VI"));
        var handlers = new PaymentHandlers(new OperaFrontDesk(client, properties, mapper),
                new OperaReservations(client, properties, mapper), integration, outcomes, methods);
        var tasks = new PmsTasks();
        var watch = mock(RetryWatch.class);
        dispatcher = new TaskDispatcher(new TaskRegistry(List.of(tasks.postPaymentTask(handlers, watch),
                tasks.refundPaymentTask(handlers, watch))), sink, Cancellations.NONE, false, TaskTracing.NOOP);
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

    void run(String definition, String step, String taskId, String paymentId, String method, String amount) {
        dispatcher.dispatch(new TaskExecutionRequested("TE-1", "PROC-1", definition, step, taskId, List.of(
                new Variable("definitionId", definition), new Variable("processKey", definition + ":MRU01/GSX4AK:" + paymentId),
                new Variable("hotelCode", "MRU01"), new Variable("locator", "GSX4AK"), new Variable("pmsHotelCode", "XMAR"),
                new Variable("pmsReservationId", "39486034"), new Variable("stayId", "GSX4AK"), new Variable("eventId", "E-1"),
                new Variable("paymentId", paymentId), new Variable("paymentKind", "DEPOSIT"), new Variable("paymentMethod", method),
                new Variable("paymentReference", "Aut. 123456"), new Variable("amount", amount), new Variable("currency", "MUR"))));
    }

    List<Call> writes() {
        return calls.stream().filter(c -> !c.method().equals("GET")).toList();
    }

    @Test
    void aPaymentOfTheTillIsPostedToOperasFolioWithItsMethodAndThePaymentAsReference() throws Exception {
        run("registrar-cobro", "post-payment", "post-payment@1", "P-1", "TRANSFER", "100.00");

        assertThat(sink.replies).containsExactly("COMPLETED [paymentOutcome=DONE, pmsReservationId=39486034, pmsPostingId=88731245]");
        assertThat(writes()).singleElement().satisfies(w -> {
            var criteria = mapper.readTree(w.body()).path("criteria");
            assertThat(criteria.path("paymentMethod").path("paymentMethod").asText()).isEqualTo("BT");
            assertThat(criteria.path("postingAmount").path("amount").decimalValue()).isEqualByComparingTo("100.00");
            assertThat(criteria.path("postingReference").asText()).isEqualTo("FO:PAY:P-1");
            assertThat(criteria.path("action").asText()).isEqualTo("Billing");
            assertThat(criteria.path("folioWindowNo").asInt()).isEqualTo(1);
            assertThat(criteria.path("cashierId").asLong()).isEqualTo(69721441L);
        });
        verify(outcomes).charge("XMAR", "39486034", "GSX4AK", "PAY:P-1", false, false, "En el folio de Opera", "88731245");
        verify(integration).resolveCauseIfOpen(eq("PMS_REJECTED:MRU01:GSX4AK:refund-payment"), anyString());
    }

    @Test
    void theSamePaymentTwiceIsPostedOnce_andItsRefundIsTheSameNegativeAgainstTheOriginal() throws Exception {
        run("registrar-cobro", "post-payment", "post-payment@1", "P-2", "CASH", "80.00");
        run("registrar-cobro", "post-payment", "post-payment@1", "P-2", "CASH", "80.00");
        run("devolver-cobro", "refund-payment", "refund-payment@1", "P-2", "CASH", "80.00");
        run("devolver-cobro", "refund-payment", "refund-payment@1", "P-2", "CASH", "80.00");

        assertThat(sink.replies).containsExactly(
                "COMPLETED [paymentOutcome=DONE, pmsReservationId=39486034, pmsPostingId=88731245]",
                "COMPLETED [paymentOutcome=STALE, pmsReservationId=39486034, pmsPostingId=88731245]",
                "COMPLETED [refundOutcome=DONE, pmsReservationId=39486034, pmsRefundId=88731246]",
                "COMPLETED [refundOutcome=STALE, pmsReservationId=39486034, pmsRefundId=88731246]");
        assertThat(writes()).hasSize(2);
        var refund = mapper.readTree(writes().get(1).body()).path("criteria");
        assertThat(refund.path("postingAmount").path("amount").decimalValue()).isEqualByComparingTo("-80.00");
        assertThat(refund.path("postingReference").asText()).isEqualTo("FO:PAY:P-2:R");
        assertThat(refund.path("originalTransactionNo").asLong()).isEqualTo(88731245L);
    }

    @Test
    void aPaymentForAGuestNotInTheHouseYetWaits_onACauseTheCheckInResolves() {
        status = "Reserved";
        run("registrar-cobro", "post-payment", "post-payment@1", "P-3", "CARD_PINPAD", "40.00");

        assertThat(sink.replies).containsExactly("COMPLETED [paymentOutcome=WAIT, pmsReservationId=39486034]");
        assertThat(writes()).isEmpty();
        verify(integration).await(eq("registrar-cobro:MRU01/GSX4AK:P-3"), eq("registrar-cobro"), eq("MRU01"), eq("GSX4AK"),
                anyList(), argThat(causes -> causes.size() == 1
                        && causes.get(0).key().equals("PMS_REJECTED:MRU01:GSX4AK:post-payment")));
        verify(outcomes).charge(eq("XMAR"), eq("39486034"), eq("GSX4AK"), eq("PAY:P-3"), eq(false), eq(true), anyString(), isNull());
    }

    @Test
    void aRefundOfAPaymentOperaDoesNotHaveYetWaitsForIt() {
        run("devolver-cobro", "refund-payment", "refund-payment@1", "P-4", "CASH", "20.00");

        assertThat(sink.replies).containsExactly("COMPLETED [refundOutcome=WAIT, pmsReservationId=39486034]");
        assertThat(writes()).isEmpty();
    }
}
