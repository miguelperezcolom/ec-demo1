package io.mateu.ecdemo1.pmsintegration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.mapping.Translation;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.integration.model.reservation.GuestType;
import io.mateu.ecdemo1.integration.model.reservation.Person;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.integration.model.reservation.ReservationStatus;
import io.mateu.ecdemo1.integration.model.reservation.Room;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaReservations;
import io.mateu.ecdemo1.pmsintegration.worker.ReservationLocks;
import io.mateu.ecdemo1.pmsintegration.worker.TaskHandlers;
import io.mateu.ecdemo1.pmsintegration.frontoffice.PmsEvents;
import io.mateu.ecdemo1.pmsintegration.write.ReservationPayload;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A version of a reservation Opera refused (no rooms left of its room type, say) waits on its refusal.
 * Once a newer version is in Opera there is nothing left to resolve: the refusal goes by itself, and the
 * process that waited on it resumes, finds Opera ahead and ends.
 */
class SupersededRefusalTest {

    static final String UPSERT_REFUSAL = "PMS_REJECTED:MRU01:ZMPBEY:upsert-reservation";
    static final String PROFILE_REFUSAL = "PMS_REJECTED:MRU01:ZMPBEY:ensure-guest-profile";

    IntegrationClients integration = mock(IntegrationClients.class);
    OperaReservations reservations = mock(OperaReservations.class);
    ReservationPayload payload = mock(ReservationPayload.class);
    PmsEvents events = mock(PmsEvents.class);
    TaskHandlers handlers;

    @BeforeEach
    void setUp() {
        var ohip = new OhipProperties("ECDEMO1", "UDFN01", "CASH", Duration.ofSeconds(2), "", false, false, null, null);
        handlers = new TaskHandlers(integration, null, reservations, payload, null, new ReservationLocks(), null, ohip, null, events);
        when(integration.resolve(eq("MRU01"), any())).thenReturn(new IntegrationClients.Resolved(
                List.of(new Translation(CodeType.HOTEL, "MRU01", "XMAR", Map.of())), List.of()));
        when(payload.build(any(), any(), anyString(), any(), any(), any(), any()))
                .thenReturn(new ObjectMapper().createObjectNode());
    }

    static Reservation version(long version) {
        var holder = new Person("Ana", "García", GuestType.ADULT, null, "ana@example.com", null, "ES", null, null, null);
        return new Reservation("MRU01", "ZMPBEY", version, ReservationStatus.CONFIRMED, "WEB", null, null,
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 16), "EUR", holder,
                List.of(new Room(1, "JS-STD", "DIRECTA", "DESAYUNO", 2, List.of(), List.of(), List.of())),
                List.of(), new BigDecimal("1090"), null, null);
    }

    static TaskExecutionRequested upsert() {
        return new TaskExecutionRequested("t", "p", "proyectar-reserva", "upsert-reservation", "pms-integration",
                List.of(new Variable(ProcessVariables.HOTEL_CODE, "MRU01"), new Variable(ProcessVariables.LOCATOR, "ZMPBEY"),
                        new Variable(ProcessVariables.GUEST_PROFILE_ID, "20600001")));
    }

    List<Variable> run() {
        return handlers.handlers().get("upsert-reservation").apply(upsert());
    }

    @Test
    void aNewerVersionWrittenResolvesTheRefusalsTheOlderOneWaitsOn() {
        var existing = new ObjectMapper().createObjectNode();
        when(integration.reservation("MRU01", "ZMPBEY")).thenReturn(version(3));
        when(reservations.byLocator("XMAR", "ZMPBEY")).thenReturn(Optional.of(existing));
        when(reservations.writtenVersion(existing)).thenReturn(1L);

        assertThat(run()).contains(new Variable(ProcessVariables.WRITE_OUTCOME, "DONE"));

        verify(reservations).update(eq("XMAR"), any(), any());
        verify(integration).resolveCauseIfOpen(UPSERT_REFUSAL, "pms-integration: v3 is in Opera");
        verify(integration).resolveCauseIfOpen(PROFILE_REFUSAL, "pms-integration: v3 is in Opera");
        // Whoever consumes the PMS — the front office's integration — is told, to read it from Opera.
        verify(events).written(eq("XMAR"), any(), eq("MRU01"), eq("ZMPBEY"), eq("proyectar-reserva"));
    }

    @Test
    void theOlderVersionResumedFindsOperaAheadAndEnds() {
        var existing = new ObjectMapper().createObjectNode();
        when(integration.reservation("MRU01", "ZMPBEY")).thenReturn(version(2));
        when(reservations.byLocator("XMAR", "ZMPBEY")).thenReturn(Optional.of(existing));
        when(reservations.writtenVersion(existing)).thenReturn(3L);

        assertThat(run()).contains(new Variable(ProcessVariables.WRITE_OUTCOME, "STALE"));

        verify(reservations, never()).update(any(), any(), any());
        verify(integration).resolveCauseIfOpen(UPSERT_REFUSAL, "pms-integration: v3 is in Opera");
        // Opera had it already: it is read again all the same (a merge in the MDM projects the same version).
        verify(events).written(eq("XMAR"), any(), eq("MRU01"), eq("ZMPBEY"), eq("proyectar-reserva"));
    }

    @Test
    void aMappingThatDoesNotAnswerLeavesTheRefusalForAPersonNotTheWriteUndone() {
        when(integration.reservation("MRU01", "ZMPBEY")).thenReturn(version(1));
        when(reservations.byLocator("XMAR", "ZMPBEY")).thenReturn(Optional.empty());
        when(reservations.create(eq("XMAR"), any())).thenReturn("39484599");
        doThrow(new IllegalStateException("mapping down")).when(integration).resolveCauseIfOpen(anyString(), anyString());

        assertThat(run()).contains(new Variable(ProcessVariables.WRITE_OUTCOME, "DONE"),
                new Variable(ProcessVariables.PMS_RESERVATION_ID, "39484599"));
    }
}
