package io.mateu.ecdemo1.pmsintegration.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.Invoice;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.ReceptionOperation;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import io.mateu.ecdemo1.integration.model.integration.OhipConnection;
import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.config.TolerantReader;
import io.mateu.ecdemo1.pmsintegration.connections.Connections;
import io.mateu.ecdemo1.pmsintegration.frontoffice.PmsEvents;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The reception, up to Opera — against a fake of OHIP that answers as XMAR does: each step run through
 * the worker runtime as the contract it serves, with the variables a process gives it. What is checked:
 * what is written to Opera (and that nothing is when Opera already has it), what a refusal becomes (a
 * cause the process waits on, and the front office told why), and what the front office is told.
 */
class ReceptionHandlersTest {

    record Call(String method, String path, String body) {
    }

    /** What the fake answers: by "METHOD path-prefix", the status and the body. */
    final Map<String, String[]> answers = new LinkedHashMap<>();
    final List<Call> calls = new ArrayList<>();
    HttpServer ohip;

    final IntegrationClients integration = mock(IntegrationClients.class);
    final PmsEvents events = mock(PmsEvents.class);
    final ReceptionOutcomes outcomes = mock(ReceptionOutcomes.class);
    final PmsTasksTest.RecordingSink sink = new PmsTasksTest.RecordingSink();
    TaskDispatcher dispatcher;

    @BeforeEach
    void start() throws IOException {
        ohip = HttpServer.create(new InetSocketAddress(0), 0);
        ohip.createContext("/oauth/v1/tokens", exchange -> reply(exchange, 200,
                "{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":3600}", "application/json"));
        ohip.createContext("/", exchange -> {
            var method = exchange.getRequestMethod();
            var uri = exchange.getRequestURI().toString();
            var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            calls.add(new Call(method, uri, body));
            String[] answer = null;
            for (var e : answers.entrySet()) {
                if ((method + " " + uri).startsWith(e.getKey())) {
                    answer = e.getValue();
                }
            }
            if (answer == null) {
                reply(exchange, 404, "{\"title\":\"Not found\"}", "application/json");
            } else {
                reply(exchange, Integer.parseInt(answer[0]), answer[1], answer.length > 2 ? answer[2] : "application/json");
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
        var mapper = new ObjectMapper();
        var handlers = new ReceptionHandlers(new OperaFrontDesk(client, properties, mapper),
                new OperaReservations(client, properties, mapper), integration, events, outcomes,
                Clock.fixed(Instant.parse("2026-09-29T10:15:00Z"), ZoneOffset.UTC), properties);
        var tasks = new PmsTasks();
        var watch = mock(RetryWatch.class);
        dispatcher = new TaskDispatcher(new TaskRegistry(List.of(tasks.assignRoomTask(handlers, watch),
                tasks.checkInReservationTask(handlers, watch), tasks.checkOutReservationTask(handlers, watch),
                tasks.fetchInvoiceTask(handlers, watch), tasks.recordNoShowTask(handlers, watch))),
                sink, Cancellations.NONE, false, TaskTracing.NOOP);
    }

    @AfterEach
    void stop() {
        ohip.stop(0);
    }

    static void reply(HttpExchange exchange, int status, String body, String type) throws IOException {
        var bytes = body.getBytes(StandardCharsets.ISO_8859_1);
        exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /** A reservation as OHIP answers a read by id. */
    static String reservation(String status, String room, String comments) {
        return """
                {"reservations": {"reservation": [{
                  "reservationIdList": [{"id": "39486034", "type": "Reservation"}, {"id": "268338062", "type": "Confirmation"}],
                  "roomStay": {"currentRoomInfo": {"roomType": "STDK"%s}, "roomRates": [{"roomType": "STDK"}],
                               "arrivalDate": "2026-05-13", "departureDate": "2026-05-14"},
                  "comments": [%s],
                  "reservationStatus": "%s"}]}}
                """.formatted(room == null ? "" : ", \"roomId\": \"" + room + "\"", comments, status);
    }

    void operaHas(String status, String room) {
        answers.put("GET /rsv/v1/hotels/XMAR/reservations/39486034", new String[]{"200", reservation(status, room, "")});
    }

    void run(String definition, String step, String taskId, Variable... extra) {
        var variables = new ArrayList<>(List.of(new Variable("definitionId", definition),
                new Variable("processKey", definition + ":MRU01/GSX4AK"), new Variable("hotelCode", "MRU01"),
                new Variable("locator", "GSX4AK"), new Variable("pmsHotelCode", "XMAR"),
                new Variable("stayId", "GSX4AK"), new Variable("eventId", "E-1")));
        variables.addAll(List.of(extra));
        dispatcher.dispatch(new TaskExecutionRequested("TE-1", "PROC-1", definition, step, taskId, variables));
    }

    List<Call> writes() {
        return calls.stream().filter(c -> !c.method().equals("GET")).toList();
    }

    // ── the room ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void theRoomTheDeskGaveIsAssignedInOpera() {
        operaHas("Reserved", null);
        answers.put("POST /fof/v1/hotels/XMAR/reservations/39486034/roomAssignments", new String[]{"201", "{}"});

        run("registrar-checkin", "assign-room", "assign-room@1", new Variable("pmsReservationId", "39486034"),
                new Variable("roomNumber", "205"));

        assertThat(sink.replies).containsExactly("COMPLETED [roomOutcome=DONE, pmsReservationId=39486034, roomNumber=205]");
        assertThat(writes()).singleElement().satisfies(w -> {
            assertThat(w.path()).isEqualTo("/fof/v1/hotels/XMAR/reservations/39486034/roomAssignments");
            assertThat(w.body()).contains("\"roomId\":\"205\"").contains("\"id\":\"39486034\"");
        });
    }

    @Test
    void theSameRoomTwiceIsNotWrittenAgain() {
        operaHas("Reserved", "205");

        run("registrar-checkin", "assign-room", "assign-room@1", new Variable("pmsReservationId", "39486034"),
                new Variable("roomNumber", "205"));

        assertThat(sink.replies).containsExactly("COMPLETED [roomOutcome=STALE, pmsReservationId=39486034, roomNumber=205]");
        assertThat(writes()).isEmpty();
    }

    @Test
    void withNoRoomChosenTheFirstOperaSuggestsIsAssigned() {
        operaHas("Reserved", null);
        answers.put("GET /fof/v1/hotels/XMAR/reservations/39486034/verifyCheckIns", new String[]{"200", """
                {"reservation": [{"roomStay": {"currentRoomInfo": {"roomType": "STDK", "suggestedRoomNumbers": ["207", "208"]}}}]}
                """});
        answers.put("POST /fof/v1/hotels/XMAR/reservations/39486034/roomAssignments", new String[]{"201", "{}"});

        run("registrar-checkin", "assign-room", "assign-room@1", new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [roomOutcome=DONE, pmsReservationId=39486034, roomNumber=207]");
        assertThat(writes().getFirst().body()).contains("\"roomId\":\"207\"");
    }

    @Test
    void aReservationNotInOperaYetIsWaitedFor() {
        answers.put("GET /rsv/v1/hotels/XMAR/reservations?", new String[]{"200", "{\"reservations\": {\"reservationInfo\": []}}"});

        run("registrar-checkin", "assign-room", "assign-room@1", new Variable("roomNumber", "205"));

        assertThat(sink.replies).containsExactly("COMPLETED [roomOutcome=WAIT]");
        var causes = ArgumentCaptor.forClass(List.class);
        verify(integration).await(eq("registrar-checkin:MRU01/GSX4AK"), eq("registrar-checkin"), eq("MRU01"), eq("GSX4AK"),
                any(), causes.capture());
        assertThat(causes.getValue()).containsExactly(Cause.notYetProjected("MRU01", "GSX4AK"));
        assertThat(writes()).isEmpty();
    }

    // ── the check-in ──────────────────────────────────────────────────────────────────────────────

    @Test
    void theCheckInIsMadeInOperaAndTheFrontOfficeHearsOfIt() {
        operaHas("Reserved", "205");
        answers.put("POST /fof/v1/hotels/XMAR/reservations/39486034/checkIns", new String[]{"201", "{}"});

        run("registrar-checkin", "check-in-reservation", "check-in-reservation@1",
                new Variable("pmsReservationId", "39486034"), new Variable("roomNumber", "205"));

        assertThat(sink.replies).containsExactly("COMPLETED [checkInOutcome=DONE, pmsReservationId=39486034]");
        assertThat(writes()).singleElement().satisfies(w -> {
            assertThat(w.path()).isEqualTo("/fof/v1/hotels/XMAR/reservations/39486034/checkIns");
            assertThat(w.body()).contains("\"roomId\":\"205\"");
        });
        verify(outcomes).done("XMAR", "39486034", "GSX4AK", ReceptionOperation.CHECK_IN, "En casa en Opera", "205", null);
        // Whoever consumes the PMS reads it again: the stay comes back «en casa».
        verify(events).written("XMAR", "39486034", "MRU01", "GSX4AK", "registrar-checkin");
        // An earlier attempt Opera refused (another room) has nothing left to wait for.
        verify(integration).resolveCauseIfOpen(eq("PMS_REJECTED:MRU01:GSX4AK:assign-room"), anyString());
        verify(integration).resolveCauseIfOpen(eq("PMS_REJECTED:MRU01:GSX4AK:check-in-reservation"), anyString());
    }

    @Test
    void aReservationAlreadyInHouseIsNotCheckedInTwice() {
        operaHas("InHouse", "205");

        run("registrar-checkin", "check-in-reservation", "check-in-reservation@1",
                new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [checkInOutcome=STALE, pmsReservationId=39486034]");
        assertThat(writes()).isEmpty();
        verify(events).written("XMAR", "39486034", "MRU01", "GSX4AK", "registrar-checkin");
    }

    @Test
    void operaRefusingTheCheckInIsACauseAndTheDeskIsToldWhy() {
        operaHas("Reserved", "205");
        answers.put("POST /fof/v1/hotels/XMAR/reservations/39486034/checkIns", new String[]{"400", """
                {"type":"Bad Request","title":"The guest's arrival is not scheduled for today. Check-in not possible.",
                 "detail":"The guest's arrival is not scheduled for today. Check-in not possible.","o:errorCode":"FOF00067"}
                """});

        run("registrar-checkin", "check-in-reservation", "check-in-reservation@1",
                new Variable("pmsReservationId", "39486034"), new Variable("roomNumber", "205"));

        assertThat(sink.replies).containsExactly("COMPLETED [checkInOutcome=WAIT, pmsReservationId=39486034]");
        var causes = ArgumentCaptor.forClass(List.class);
        var variables = ArgumentCaptor.forClass(List.class);
        verify(integration).await(eq("registrar-checkin:MRU01/GSX4AK"), eq("registrar-checkin"), eq("MRU01"), eq("GSX4AK"),
                variables.capture(), causes.capture());
        var cause = (Cause) causes.getValue().getFirst();
        assertThat(cause.key()).isEqualTo("PMS_REJECTED:MRU01:GSX4AK:check-in-reservation");
        assertThat(cause.description()).contains("not scheduled for today").contains("FOF00067");
        // The successor is started as this one was: the stay, the room and the Opera reservation too.
        assertThat(variables.getValue()).contains(new Variable("pmsHotelCode", "XMAR"), new Variable("stayId", "GSX4AK"),
                new Variable("roomNumber", "205"), new Variable("pmsReservationId", "39486034"));
        verify(outcomes).refused("XMAR", "39486034", "GSX4AK", ReceptionOperation.CHECK_IN,
                "The guest's arrival is not scheduled for today. Check-in not possible.");
        verifyNoInteractions(events);
    }

    @Test
    void aReservationOperaHasCancelledHasNoCheckInToRecordNorAnythingToWaitFor() {
        operaHas("Cancelled", null);

        run("registrar-checkin", "assign-room", "assign-room@1", new Variable("pmsReservationId", "39486034"),
                new Variable("roomNumber", "205"));
        run("registrar-checkin", "check-in-reservation", "check-in-reservation@1", new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [roomOutcome=STALE, pmsReservationId=39486034]",
                "COMPLETED [checkInOutcome=STALE, pmsReservationId=39486034]");
        assertThat(writes()).isEmpty();
        verify(integration, never()).await(any(), any(), any(), any(), any(), any());
        verify(integration).resolveCauseIfOpen(eq("PMS_REJECTED:MRU01:GSX4AK:check-in-reservation"), anyString());
    }

    // ── the check-out and its invoice ─────────────────────────────────────────────────────────────

    void operasDateIs(String date) {
        answers.put("GET /ent/config/v1/hotels/XMAR/operaContext", new String[]{"200",
                "{\"hotelContext\": {\"hotelId\": \"XMAR\", \"businessDate\": \"" + date + "\"}}"});
    }

    @Test
    void aCheckOutBeforeTheDepartureIsAnEarlyDeparture() {
        operaHas("InHouse", "205");
        operasDateIs("2026-05-13");
        answers.put("PUT /csh/v1/hotels/XMAR/reservations/39486034/earlyDeparture", new String[]{"200", "{}"});
        answers.put("POST /csh/v1/hotels/XMAR/reservations/39486034/checkOuts", new String[]{"201", "{}"});

        run("registrar-checkout", "check-out-reservation", "check-out-reservation@1",
                new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [checkOutOutcome=DONE, pmsReservationId=39486034]");
        // Made an early departure first (its departure moves to Opera's date), then checked out.
        assertThat(writes()).extracting(Call::method, Call::path).containsExactly(
                org.assertj.core.groups.Tuple.tuple("PUT", "/csh/v1/hotels/XMAR/reservations/39486034/earlyDeparture"),
                org.assertj.core.groups.Tuple.tuple("POST", "/csh/v1/hotels/XMAR/reservations/39486034/checkOuts"));
    }

    @Test
    void aFolioWithABalanceIsSettledWithWhatTheDeskCollectedBeforeTheCheckOut() {
        operaHas("InHouse", "205");
        operasDateIs("2026-05-14");
        answers.put("GET /csh/v1/hotels/XMAR/reservations/39486034/folios", new String[]{"200", """
                {"reservationFolioInformation": {"folioWindows": [
                  {"folioWindowNo": 1, "balance": {"amount": 306, "currencyCode": "MUR"}, "emptyWindow": false},
                  {"folioWindowNo": 2, "balance": {"amount": 0, "currencyCode": "MUR"}, "emptyWindow": true}]}}
                """});
        answers.put("POST /csh/v1/hotels/XMAR/reservations/39486034/payments", new String[]{"201", "{}"});
        answers.put("POST /csh/v1/hotels/XMAR/reservations/39486034/folios", new String[]{"201", """
                {"folioWindows": [{"folioWindowNo": 1, "storedFolioId": {"id": "8812", "type": "StoredFolio"}}]}
                """});
        answers.put("POST /csh/v1/hotels/XMAR/reservations/39486034/checkOuts", new String[]{"201", "{}"});

        run("registrar-checkout", "check-out-reservation", "check-out-reservation@1",
                new Variable("pmsReservationId", "39486034"));

        // Settled, its folio generated (the invoice: Opera checks out nothing before, FOF00125), checked out.
        assertThat(sink.replies).containsExactly(
                "COMPLETED [checkOutOutcome=DONE, pmsReservationId=39486034, storedFolioId=8812]");
        assertThat(writes()).extracting(Call::path).containsExactly("/csh/v1/hotels/XMAR/reservations/39486034/payments",
                "/csh/v1/hotels/XMAR/reservations/39486034/folios", "/csh/v1/hotels/XMAR/reservations/39486034/checkOuts");
        assertThat(writes().getFirst().body()).contains("\"amount\":306").contains("\"folioWindowNo\":1")
                .contains("\"cashierId\":69721441").contains("\"action\":\"Settlefolio\"")
                .contains("\"postingReference\":\"Front office MRU01 · GSX4AK\"");
        assertThat(writes().getFirst().body()).contains("\"cashierId\":69721441").contains("\"id\":\"39486034\"");
    }

    @Test
    void theCheckOutGoesWithTheIntegrationsCashier() {
        operaHas("InHouse", "205");
        operasDateIs("2026-05-14");
        answers.put("POST /csh/v1/hotels/XMAR/reservations/39486034/checkOuts", new String[]{"201", "{}"});

        run("registrar-checkout", "check-out-reservation", "check-out-reservation@1",
                new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [checkOutOutcome=DONE, pmsReservationId=39486034]");
        assertThat(writes()).singleElement().satisfies(w -> {
            assertThat(w.path()).isEqualTo("/csh/v1/hotels/XMAR/reservations/39486034/checkOuts");
            assertThat(w.body()).contains("\"cashierId\":69721441").contains("\"eventType\":\"CheckOut\"");
        });
        verify(events).written("XMAR", "39486034", "MRU01", "GSX4AK", "registrar-checkout");
    }

    @Test
    void aReservationAlreadyCheckedOutIsNotWritten() {
        operaHas("CheckedOut", "205");

        run("registrar-checkout", "check-out-reservation", "check-out-reservation@1",
                new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [checkOutOutcome=STALE, pmsReservationId=39486034]");
        assertThat(writes()).isEmpty();
    }

    @Test
    void anOpenBalanceIsACause() {
        operaHas("InHouse", "205");
        operasDateIs("2026-05-14");
        answers.put("POST /csh/v1/hotels/XMAR/reservations/39486034/checkOuts", new String[]{"400", """
                {"title":"Balance must be zero to check out","o:errorCode":"FOF00123"}
                """});

        run("registrar-checkout", "check-out-reservation", "check-out-reservation@1",
                new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [checkOutOutcome=WAIT, pmsReservationId=39486034]");
        verify(outcomes).refused("XMAR", "39486034", "GSX4AK", ReceptionOperation.CHECK_OUT, "Balance must be zero to check out");
    }

    @Test
    void theInvoiceIsOperasFolioWithItsDocument() {
        answers.put("GET /csh/v1/hotels/XMAR/folioHistory", new String[]{"200", """
                {"folioHistory": [
                  {"reservationInfo": {"reservationId": 39486034}, "folioNo": 376, "folioNoWithPrefix": "XMAR376", "folioWindowNo": 1,
                   "folioStatus": "Deposit", "start": "2026-05-13", "folioAmount": {"amount": 10, "currencyCode": "MUR"}},
                  {"reservationInfo": {"reservationId": 39486034}, "folioNo": 377, "folioNoWithPrefix": "XMAR377", "folioWindowNo": 1,
                   "folioStatus": "Ok", "start": "2026-05-13", "folioAmount": {"amount": 150, "currencyCode": "MUR"}}]}
                """});
        answers.put("GET /csh/v1/hotels/XMAR/storedFolios/8812", new String[]{"200", """
                {"storedFolioDetails": {"folioReportURL": "/reports/folio-8812.pdf", "hotelId": "XMAR"}}
                """});
        answers.put("GET /reports/folio-8812.pdf", new String[]{"200", "%PDF-1.4 fake", "application/pdf"});

        run("registrar-checkout", "fetch-invoice", "fetch-invoice@1", new Variable("pmsReservationId", "39486034"),
                new Variable("storedFolioId", "8812"));

        assertThat(sink.replies).containsExactly("COMPLETED [invoiceOutcome=DONE]");
        verify(outcomes).done("XMAR", "39486034", "GSX4AK", ReceptionOperation.CHECK_OUT, "Salida registrada en Opera", null,
                new Invoice("OPERA", "XMAR377", LocalDate.of(2026, 5, 13), new BigDecimal("150"), "MUR",
                        Base64.getEncoder().encodeToString("%PDF-1.4 fake".getBytes(StandardCharsets.ISO_8859_1))));
        assertThat(writes()).isEmpty();
    }

    @Test
    void aFolioAlreadySettledIsGeneratedAllTheSameBeforeTheCheckOut() {
        operaHas("InHouse", "205");
        operasDateIs("2026-05-14");
        answers.put("GET /csh/v1/hotels/XMAR/reservations/39486034/folios", new String[]{"200", """
                {"reservationFolioInformation": {"folioWindows": [
                  {"folioWindowNo": 1, "balance": {"amount": 0, "currencyCode": "MUR"}, "emptyWindow": false},
                  {"folioWindowNo": 2, "balance": {"amount": 0, "currencyCode": "MUR"}, "emptyWindow": true}]}}
                """});
        answers.put("POST /csh/v1/hotels/XMAR/reservations/39486034/folios", new String[]{"201", "{\"folioWindows\": []}"});
        answers.put("POST /csh/v1/hotels/XMAR/reservations/39486034/checkOuts", new String[]{"201", "{}"});

        run("registrar-checkout", "check-out-reservation", "check-out-reservation@1",
                new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [checkOutOutcome=DONE, pmsReservationId=39486034]");
        assertThat(writes()).extracting(Call::path).containsExactly("/csh/v1/hotels/XMAR/reservations/39486034/folios",
                "/csh/v1/hotels/XMAR/reservations/39486034/checkOuts");
    }

    @Test
    void theInvoiceIsTheFolioTheCheckOutGeneratedWithoutGeneratingAnother() {
        answers.put("GET /csh/v1/hotels/XMAR/folioHistory", new String[]{"200", """
                {"folioHistory": [{"reservationInfo": {"reservationId": 39486034}, "folioNo": 380, "folioNoWithPrefix": "XMAR380",
                   "folioWindowNo": 1, "folioStatus": "Ok", "start": "2026-05-13", "folioAmount": {"amount": 306, "currencyCode": "MUR"}}]}
                """});
        answers.put("GET /csh/v1/hotels/XMAR/storedFolios/8812", new String[]{"200", """
                {"storedFolioDetails": {"folioReportURL": "/reports/folio-8812.pdf"}}
                """});
        answers.put("GET /reports/folio-8812.pdf", new String[]{"200", "%PDF-1.4 folio", "application/pdf"});

        run("registrar-checkout", "fetch-invoice", "fetch-invoice@1", new Variable("pmsReservationId", "39486034"),
                new Variable("storedFolioId", "8812"));

        assertThat(sink.replies).containsExactly("COMPLETED [invoiceOutcome=DONE]");
        assertThat(writes()).isEmpty();
        verify(outcomes).done(eq("XMAR"), eq("39486034"), eq("GSX4AK"), eq(ReceptionOperation.CHECK_OUT), anyString(), isNull(),
                eq(new Invoice("OPERA", "XMAR380", LocalDate.of(2026, 5, 13), new BigDecimal("306"), "MUR",
                        Base64.getEncoder().encodeToString("%PDF-1.4 folio".getBytes(StandardCharsets.ISO_8859_1)))));
    }

    @Test
    void anInvoiceOperaGivesNoDocumentForGoesAsItsFigures() {
        answers.put("GET /csh/v1/hotels/XMAR/folioHistory", new String[]{"200", """
                {"folioHistory": [{"reservationInfo": {"reservationId": 39486034}, "folioNo": 377, "folioNoWithPrefix": "XMAR377",
                   "folioWindowNo": 1, "folioStatus": "Ok", "start": "2026-05-13", "folioAmount": {"amount": 0, "currencyCode": "MUR"}}]}
                """});

        run("registrar-checkout", "fetch-invoice", "fetch-invoice@1", new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [invoiceOutcome=FIGURES]");
        verify(outcomes).done(eq("XMAR"), eq("39486034"), eq("GSX4AK"), eq(ReceptionOperation.CHECK_OUT), anyString(), isNull(),
                eq(new Invoice("OPERA", "XMAR377", LocalDate.of(2026, 5, 13), new BigDecimal("0"), "MUR", null)));
        verify(integration, never()).await(any(), any(), any(), any(), any(), any());
    }

    // ── the no-show ───────────────────────────────────────────────────────────────────────────────

    @Test
    void theNoShowIsACommentOnOperasReservation() {
        operaHas("Reserved", null);
        answers.put("PUT /rsv/v1/hotels/XMAR/reservations/39486034", new String[]{"200", "{}"});

        run("registrar-no-show-pms", "record-no-show", "record-no-show@1", new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [noShowOutcome=DONE, pmsReservationId=39486034]");
        assertThat(writes()).singleElement().satisfies(w -> {
            assertThat(w.method()).isEqualTo("PUT");
            assertThat(w.body()).contains("No show — reported by the front office of MRU01 (stay GSX4AK) at 2026-09-29 10:15 UTC")
                    .doesNotContain("roomStay");
        });
    }

    @Test
    void aNoShowAlreadyRecordedIsNotWrittenAgain() {
        answers.put("GET /rsv/v1/hotels/XMAR/reservations/39486034", new String[]{"200", reservation("Reserved", null,
                "{\"comment\": {\"text\": {\"value\": \"No show — reported by the front office of MRU01\"}}}")});

        run("registrar-no-show-pms", "record-no-show", "record-no-show@1", new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [noShowOutcome=STALE, pmsReservationId=39486034]");
        assertThat(writes()).isEmpty();
    }

    @Test
    void guestsOperaHasInHouseAreNoNoShow() {
        operaHas("InHouse", "205");

        run("registrar-no-show-pms", "record-no-show", "record-no-show@1", new Variable("pmsReservationId", "39486034"));

        assertThat(sink.replies).containsExactly("COMPLETED [noShowOutcome=WAIT, pmsReservationId=39486034]");
        verify(outcomes).refused(eq("XMAR"), eq("39486034"), eq("GSX4AK"), eq(ReceptionOperation.NO_SHOW), anyString());
        assertThat(writes()).isEmpty();
    }
}
