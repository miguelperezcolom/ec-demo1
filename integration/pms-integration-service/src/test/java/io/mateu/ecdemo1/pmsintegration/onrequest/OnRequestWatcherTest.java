package io.mateu.ecdemo1.pmsintegration.onrequest;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.mapping.Translation;
import io.mateu.ecdemo1.integration.model.reservation.Reservation;
import io.mateu.ecdemo1.integration.model.reservation.ReservationStatus;
import io.mateu.ecdemo1.integration.model.reservation.Room;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.ohip.OperaAvailability;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a person let in on request goes into Opera by itself once Opera has room for it: one reservation
 * a cause a round, the first that fits; one that does not fit keeps waiting.
 */
class OnRequestWatcherTest {

    static final String CAUSE = "PMS_REJECTED:MRU01:upsert-reservation:RSV00138";
    static final LocalDate NOV16 = LocalDate.of(2026, 11, 16);

    final IntegrationClients integration = mock(IntegrationClients.class);
    final OperaAvailability availability = mock(OperaAvailability.class);
    final OnRequestWatcher watcher = new OnRequestWatcher(integration, availability);

    static Reservation reservation(String locator, String roomType, LocalDate arrival) {
        return new Reservation("MRU01", locator, 1, ReservationStatus.CONFIRMED, "WEB", null, null, arrival, arrival.plusDays(3),
                "EUR", null, List.of(new Room(1, roomType, "DIRECTA", "BB", 2, List.of(), List.of(), null)), List.of(), null, null, null);
    }

    void waiting(String... locators) {
        when(integration.onRequestCauses()).thenReturn(List.of(new IntegrationClients.OnRequestCause(CAUSE, "MRU01", "2026-10-27T15:00:00Z")));
        when(integration.waitingOn(CAUSE)).thenReturn(java.util.Arrays.stream(locators)
                .map(l -> new IntegrationClients.WaitingProcess("proyectar-reserva:MRU01/" + l, "MRU01", l)).toList());
        when(integration.resolve(eq("MRU01"), anyList())).thenReturn(new IntegrationClients.Resolved(List.of(
                new Translation(CodeType.HOTEL, "MRU01", "XMAR", Map.of()),
                new Translation(CodeType.ROOM_TYPE, "DBL-POOL", "DBPB", Map.of()),
                new Translation(CodeType.ROOM_TYPE, "DBL-SIDESEA", "DBLB", Map.of())), List.of()));
    }

    @Test
    void theFirstReservationOperaHasRoomForIsLetIn() {
        waiting("6BNAY7", "3NMNYW");
        when(integration.reservation("MRU01", "6BNAY7")).thenReturn(reservation("6BNAY7", "DBL-POOL", NOV16));
        when(integration.reservation("MRU01", "3NMNYW")).thenReturn(reservation("3NMNYW", "DBL-SIDESEA", NOV16.plusDays(30)));
        when(availability.fits("XMAR", NOV16, NOV16.plusDays(3), List.of("DBPB"))).thenReturn(false);
        when(availability.fits("XMAR", NOV16.plusDays(30), NOV16.plusDays(33), List.of("DBLB"))).thenReturn(true);

        watcher.look();

        verify(integration).releaseRefusal("MRU01", "3NMNYW", "upsert-reservation", "on request: Opera has room now");
        verify(integration, never()).releaseRefusal(eq("MRU01"), eq("6BNAY7"), anyString(), anyString());
    }

    @Test
    void oneACauseARoundSoTwoForTheLastRoomDoNotBothGo() {
        waiting("6BNAY7", "75B5TG");
        when(integration.reservation(eq("MRU01"), anyString())).thenAnswer(c -> reservation(c.getArgument(1), "DBL-POOL", NOV16));
        when(availability.fits(any(), any(), any(), anyList())).thenReturn(true);

        watcher.look();

        verify(integration).releaseRefusal("MRU01", "6BNAY7", "upsert-reservation", "on request: Opera has room now");
        verify(integration, never()).releaseRefusal(eq("MRU01"), eq("75B5TG"), anyString(), anyString());
    }

    @Test
    void nothingOnRequestNothingAsked() {
        when(integration.onRequestCauses()).thenReturn(List.of());
        watcher.look();
        verify(availability, never()).fits(any(), any(), any(), anyList());
    }
}
