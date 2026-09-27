package io.mateu.ecdemo1.journey;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.journey.model.Hop;
import io.mateu.ecdemo1.journey.model.Journey;
import io.mateu.ecdemo1.journey.model.JourneyMapper;
import io.mateu.ecdemo1.journey.model.Kind;
import io.mateu.ecdemo1.journey.model.Lane;
import io.mateu.ecdemo1.journey.model.Outcome;
import io.mateu.ecdemo1.journey.model.Tone;
import io.mateu.ecdemo1.journey.tempo.TraceParser;
import io.mateu.ecdemo1.journey.tempo.TraceSpan;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real traces, exported from the cluster's Tempo ({@code /api/traces/{id}}), told as journeys: the
 * creation of 36K69K and a modification of it, the no-show of 65K4JB and a creation of it that the
 * integration failed to read.
 */
class JourneyMapperTest {

    /** Long after the traces: nothing is "still arriving". */
    static final long LATER = Long.MAX_VALUE / 2;

    static List<TraceSpan> spans(String fixture) throws IOException {
        try (var in = JourneyMapperTest.class.getResourceAsStream("/traces/" + fixture + ".json")) {
            return TraceParser.parse(new ObjectMapper().readTree(in));
        }
    }

    static List<String> titles(Journey journey) {
        return journey.hops().stream().map(Hop::title).toList();
    }

    static Hop hop(Journey journey, String title) {
        return journey.hops().stream().filter(h -> h.title().equals(title)).findFirst().orElseThrow(
                () -> new AssertionError("no hop «" + title + "» in " + titles(journey)));
    }

    @Test
    void theTraceIsReadWithHexIdsAndShortServiceNames() throws IOException {
        var spans = spans("36K69K-created");
        assertThat(spans).hasSizeGreaterThan(150);
        var root = spans.stream().filter(s -> s.parentId() == null).findFirst().orElseThrow();
        assertThat(root.service()).isEqualTo("booking");
        assertThat(root.name()).isEqualTo("http post /bookings");
        assertThat(root.spanId()).matches("[0-9a-f]{16}");
        assertThat(root.kind()).isEqualTo("SERVER");
        assertThat(spans).extracting(TraceSpan::service).contains("orchestrator", "crs-integration", "mapping",
                "pms-integration", "customer-mdm", "front-office");
    }

    @Test
    void aCreationGoesFromTheCrsToOperaAndTheFrontOffice() throws IOException {
        var journey = JourneyMapper.map("e7e095e1a40c496329e6b8a974b9f877", "36K69K", spans("36K69K-created"), LATER);

        assertThat(journey.kind()).isEqualTo(Kind.CREATED);
        assertThat(journey.hotel()).isEqualTo("MRU01");
        assertThat(journey.outcome()).isEqualTo(Outcome.DONE);
        assertThat(journey.operaHotel()).isEqualTo("XMAR");
        assertThat(journey.operaReservationId()).isEqualTo("39484612");
        assertThat(journey.operaAction()).isEqualTo("created");
        // The Opera POST ends at ~7.6 s from the CRS's; the front office has the stay at ~8.5 s.
        assertThat(journey.crsToOpera()).isBetween(Duration.ofMillis(7_000), Duration.ofMillis(8_000));
        assertThat(journey.crsToFrontOffice()).isBetween(Duration.ofMillis(8_000), Duration.ofMillis(9_000));
        assertThat(journey.crsToFrontOffice()).isGreaterThan(journey.crsToOpera());

        assertThat(journey.processes()).extracting(p -> p.workflowId() + "=" + p.status())
                .containsExactly("proyectar-reserva=COMPLETED", "proyectar-estancia=COMPLETED");
        assertThat(journey.lanes()).containsExactly(Lane.CRS, Lane.CRS_INTEGRATION, Lane.ENGINE, Lane.MAPPING,
                Lane.MDM, Lane.OPERA, Lane.FRONT_OFFICE);

        // In business words, in order, and none of the plumbing.
        assertThat(titles(journey)).containsSubsequence(
                "Reserva creada en el CRS",
                "Evento del CRS recibido",
                "Evento → proceso",
                "Proceso «Proyectar reserva»",
                "Preparar (resolver todas las traducciones)",
                "Asegurar el perfil del huésped",
                "Identidad del cliente resuelta",
                "Candado de la reserva tomado",
                "Grabar la reserva en Opera",
                "Candado de la reserva soltado",
                "Anotar la referencia del PMS en el CRS",
                "Proceso «Proyectar estancia»",
                "Leer la reserva de Opera y grabarla como estancia",
                "Estancia grabada en el front office");
        assertThat(titles(journey)).noneMatch(t -> t.contains("outbox") || t.contains("send") || t.contains("receive")
                || t.contains("step-over") || t.contains("http ") || t.startsWith("¿") || t.equals("Start"));

        assertThat(hop(journey, "Grabar la reserva en Opera").detail())
                .contains("Reserva 39484612 creada en Opera (hotel XMAR)").contains("versión 1 del CRS")
                .contains("llamadas a Opera");
        assertThat(hop(journey, "Grabar la reserva en Opera").lane()).isEqualTo(Lane.OPERA);
        assertThat(hop(journey, "Asegurar el perfil del huésped").detail())
                .contains("Perfil creado en Opera: 20546187").contains("cliente C-CD72D6F4D0B7");
        assertThat(hop(journey, "Preparar (resolver todas las traducciones)").lane()).isEqualTo(Lane.MAPPING);
        assertThat(hop(journey, "Proceso «Proyectar reserva»").link())
                .isEqualTo("/workflow/processes/ed7d3016-00ea-4892-9494-970df70a6db7");
        assertThat(hop(journey, "Candado de la reserva tomado").tone()).isEqualTo(Tone.OK);
        assertThat(journey.hops()).allMatch(h -> h.tone() != Tone.ERROR);
    }

    @Test
    void aModificationThatLostTheRaceWaitsForTheLockAndFindsOperaAlreadyUpToDate() throws IOException {
        // Two modifications of 36K69K, a second apart: this one waited 724 ms for the other's lock,
        // and by then Opera held version 3 already — so it wrote nothing.
        var journey = JourneyMapper.map("962a1b5cb2c5247cd5500d7dfbda0933", "36K69K", spans("36K69K-modified"), LATER);

        assertThat(journey.kind()).isEqualTo(Kind.MODIFIED);
        assertThat(journey.version()).isEqualTo(3L);
        assertThat(journey.operaReservationId()).isEqualTo("39484612");
        assertThat(journey.operaAction()).isEqualTo("stale");
        assertThat(journey.outcome()).isEqualTo(Outcome.DONE);
        assertThat(titles(journey)).startsWith("Reserva modificada en el CRS");
        // Another change of the same booking held the lock: 724 ms waiting for it.
        var lock = hop(journey, "Candado de la reserva tomado");
        assertThat(lock.tone()).isEqualTo(Tone.WAIT);
        assertThat(lock.detail()).startsWith("Esperó 724 ms");
        assertThat(hop(journey, "Grabar la reserva en Opera").detail())
                .startsWith("Opera ya tenía la reserva 39484612 en esta versión o una más nueva");
        assertThat(hop(journey, "Asegurar el perfil del huésped").detail()).contains("Perfil actualizado en Opera: 20546187");
        assertThat(journey.crsToOpera()).isLessThan(Duration.ofSeconds(4));
        assertThat(journey.crsToFrontOffice()).isNotNull();
    }

    @Test
    void aNoShowIsTheHotelsWordTheCrsRuleAndTheCancellationInOpera() throws IOException {
        var journey = JourneyMapper.map("3a153c782f40b975f11d4264581a7902", "65K4JB", spans("65K4JB-no-show"), LATER);

        assertThat(journey.kind()).isEqualTo(Kind.NO_SHOW);
        assertThat(journey.processes()).extracting(p -> p.workflowId())
                .containsExactly("registrar-no-show", "proyectar-cancelacion");
        assertThat(titles(journey)).containsSubsequence("El hotel comunica el no-show", "Proceso «Registrar no-show»",
                "Registrar el no-show en el CRS", "Proceso «Proyectar cancelación»", "Cancelar en Opera");
        assertThat(hop(journey, "Registrar el no-show en el CRS").lane()).isEqualTo(Lane.CRS);
        assertThat(hop(journey, "Cancelar en Opera").lane()).isEqualTo(Lane.OPERA);
        assertThat(journey.crsToOpera()).isNotNull();
        assertThat(journey.outcome()).isEqualTo(Outcome.DONE);
    }

    @Test
    void aTraceThatBrokeSaysWhereAndWhy() throws IOException {
        var journey = JourneyMapper.map("cc413f8c6cf08e391bb01a6100eba7f7", "65K4JB", spans("65K4JB-created-failed"), LATER);

        assertThat(journey.kind()).isEqualTo(Kind.CREATED);
        assertThat(journey.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(journey.outcomeDetail()).startsWith("Integración CRS: ").contains("Type definition error");
        assertThat(hop(journey, "Evento del CRS recibido").tone()).isEqualTo(Tone.ERROR);
        assertThat(journey.crsToOpera()).isNull();
    }

    @Test
    void aTraceStillArrivingIsInProgressNotBroken() throws IOException {
        var all = spans("36K69K-created");
        // Only the CRS's part has arrived yet.
        var early = all.stream().filter(s -> "booking".equals(s.service())).toList();
        var end = early.stream().mapToLong(TraceSpan::endNanos).max().orElseThrow();
        var journey = JourneyMapper.map("e7e0", "36K69K", early, end + 1_000_000_000L);
        assertThat(journey.outcome()).isEqualTo(Outcome.IN_PROGRESS);
        assertThat(journey.crsToOpera()).isNull();
    }

    @Test
    void anApprovalThatResumesSeveralBookingsKeepsOnlyThisOnesPart() throws IOException {
        // Two bookings' traces as if one approval had resumed both: each journey keeps its own.
        var mine = spans("36K69K-created");
        var theirs = spans("65K4JB-no-show");
        var both = new java.util.ArrayList<TraceSpan>(mine);
        both.addAll(theirs);
        var journey = JourneyMapper.map("x", "36K69K", both, LATER);
        assertThat(titles(journey)).doesNotContain("El hotel comunica el no-show", "Cancelar en Opera");
        assertThat(journey.operaReservationId()).isEqualTo("39484612");
    }

    @Test
    void theNewAttributesAreReadWhenTheSpansCarryThem() {
        var t0 = 1_000_000_000_000L;
        var spans = List.of(
                span("a", null, "booking", "http post /bookings", "SERVER", t0, t0 + 100, java.util.Map.of("booking.locator", "ABC123", "hotel.code", "MRU01")),
                span("b", "a", "crs-integration", "route-integration-events process", "INTERNAL", t0 + 200, t0 + 210,
                        java.util.Map.of("booking.locator", "ABC123", "booking.event", "reservation-created", "booking.version", "1",
                                "eventconductor.business-key", "proyectar-reserva:MRU01/ABC123:e1")),
                span("c", "b", "orchestrator", "Proyectar reserva", "INTERNAL", t0 + 300, t0 + 900,
                        java.util.Map.of("eventconductor.process.id", "p1", "eventconductor.workflow.id", "proyectar-reserva",
                                "eventconductor.process.status", "RUNNING", "eventconductor.process.businessKey", "proyectar-reserva:MRU01/ABC123:e1")),
                span("d", "c", "orchestrator", "Preparar (resolver todas las traducciones)", "INTERNAL", t0 + 310, t0 + 400,
                        java.util.Map.of("eventconductor.step.type", "ACTION", "eventconductor.step.id", "prepare", "eventconductor.step.topic", "mapping",
                                "eventconductor.step.status", "COMPLETED", "eventconductor.step.executionId", "x1")),
                span("e", "d", "mapping", "eventconductor.task prepare", "CONSUMER", t0 + 320, t0 + 390,
                        java.util.Map.of("eventconductor.step.executionId", "x1",
                                "mapping.translations", "HOTEL MRU01=XMAR; ROOM_TYPE DBL=DBLX",
                                "mapping.causes", "MISSING_MAPPING:MRU01:RATE_PLAN:EMPLEADOS-27=RATE_PLAN EMPLEADOS-27 of hotel MRU01 has no approved equivalent in the PMS")),
                span("f", "c", "orchestrator", "Esperar a que se resuelvan las causas", "INTERNAL", t0 + 410, t0 + 900,
                        java.util.Map.of("eventconductor.step.type", "WAIT_FOR_MESSAGE", "eventconductor.step.id", "wait-prepare",
                                "eventconductor.step.status", "RUNNING")),
                span("g", "c", "customer-mdm", "http post /identities/resolve", "SERVER", t0 + 420, t0 + 430,
                        java.util.Map.of("mdm.identities", "0:C-AAA:NEW,1:C-BBB:EMAIL")));
        var journey = JourneyMapper.map("t", "ABC123", spans, LATER);

        assertThat(journey.kind()).isEqualTo(Kind.CREATED);
        assertThat(journey.version()).isEqualTo(1L);
        assertThat(journey.outcome()).isEqualTo(Outcome.WAITING);
        assertThat(journey.causes()).containsExactly("falta la equivalencia en Opera de tarifa EMPLEADOS-27 (MRU01)");
        assertThat(journey.translations()).containsExactly("Hotel MRU01 → XMAR", "Tipo de habitación DBL → DBLX");
        assertThat(hop(journey, "Preparar (resolver todas las traducciones)").tone()).isEqualTo(Tone.WAIT);
        assertThat(hop(journey, "Esperando a que se resuelvan sus causas").detail())
                .isEqualTo("Espera a: falta la equivalencia en Opera de tarifa EMPLEADOS-27 (MRU01)");
        assertThat(hop(journey, "Identidad del cliente resuelta").detail())
                .isEqualTo("Titular C-AAA: cliente nuevo · Pasajero 2 C-BBB: reconocido por su email");
        assertThat(hop(journey, "Evento → proceso").detail()).isEqualTo("Reserva creada (versión 1) · arranca «Proyectar reserva»");
    }

    static TraceSpan span(String id, String parent, String service, String name, String kind, long start, long end,
                          java.util.Map<String, String> attributes) {
        return new TraceSpan(id, parent, service, name, kind, start, end, attributes, false, null);
    }
}
