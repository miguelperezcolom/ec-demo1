package io.mateu.ecdemo1.pmsintegration.frontoffice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.pmsintegration.config.PmsIntegrationProperties;
import io.mateu.ecdemo1.pmsintegration.worker.RetryWatch;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.Message;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the PMS adapter publishes, as it really sends it — a stay to the front office (mapped from an
 * Opera reservation), a reservation written in the PMS, the retry watch's alert and its resolution —
 * checked against each topic's schema. No Opera: the reservation is Opera's answer, as OHIP gives it.
 */
class PmsContractsTest {

    static final Instant AT = Instant.parse("2026-11-12T09:30:00Z");

    static ObjectMapper bootMapper() {
        try (var context = new AnnotationConfigApplicationContext(JacksonAutoConfiguration.class)) {
            return context.getBean(ObjectMapper.class);
        }
    }

    final ObjectMapper boot = bootMapper();
    final StreamBridge streamBridge = mock(StreamBridge.class);
    final Clock clock = Clock.fixed(AT, ZoneOffset.UTC);

    {
        when(streamBridge.send(anyString(), any())).thenReturn(true);
    }

    /** What went to a binding: the bytes of a message, or a POJO as Spring Cloud Stream writes it (the app's mapper). */
    String sentPayload(String binding) {
        var sent = ArgumentCaptor.forClass(Object.class);
        verify(streamBridge).send(eq(binding), sent.capture());
        var value = sent.getValue();
        if (value instanceof Message<?> message) {
            return new String((byte[]) message.getPayload());
        }
        try {
            return boot.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aStayMappedFromOperaIsWhatTheFrontOfficeCommandsSchemaSays() throws Exception {
        var reservation = boot.readTree("""
                {
                  "reservationIdList": [{"id": "39484601", "type": "Reservation"}, {"id": "268334379", "type": "Confirmation"}],
                  "externalReferences": [{"id": "3PJ492", "idContext": "ECDEMO1-09271718"}],
                  "roomStay": {
                    "roomRates": [{"total": {"amountBeforeTax": 186},
                                   "rates": {"rate": [{"base": {"amountBeforeTax": 186, "currencyCode": "MUR"}}]},
                                   "guestCounts": {"adults": 2, "children": 0},
                                   "roomType": "STDK", "ratePlanCode": "406484DIRXM"}],
                    "guestCounts": {"adults": 2, "children": 1},
                    "arrivalDate": "2026-11-10", "departureDate": "2026-11-12",
                    "total": {"amountBeforeTax": 372}
                  },
                  "reservationGuests": [{"profileInfo": {"profileIdList": [{"id": "20546095", "type": "Profile"}],
                      "profile": {"customer": {"personName": [{"givenName": "Nora", "surname": "Moreau", "nameType": "Primary"}]}}},
                    "primary": true}],
                  "reservationPackages": [{"packageCode": "BRKFST", "startDate": "2026-11-10", "endDate": "2026-11-12"}],
                  "reservationStatus": "Reserved",
                  "lastModifyDateTime": "2026-09-27 21:57:59.0"
                }""");
        var stay = StayMapper.toWriteStay("XMAR", reservation,
                new StayMapper.Context("ECDEMO1-09271718", Set.of("NOSHOW"), null, null));
        new StayProjection(null, null, null, null, streamBridge, boot).send(stay);

        var json = sentPayload(StayProjection.BINDING);
        Contracts.topic("front-office-commands").assertValid(json);
        assertThat(boot.readTree(json).get("type").asText()).isEqualTo("write-stay");
        assertThat(boot.readTree(json).get("crsLocator").asText()).isEqualTo("3PJ492");
    }

    @Test
    void aReservationWrittenInThePmsIsWhatThePmsReservationsSchemaSays() {
        new PmsEvents(streamBridge, boot, clock).written("XMAR", "39484601", "MRU01", "12E45", null);

        Contracts.topic("pms-reservations").assertValid(sentPayload(PmsEvents.BINDING));
    }

    @Test
    void theRetryWatchsAlertAndItsResolutionAreWhatTheirSchemasSay() {
        var properties = new PmsIntegrationProperties(null, null, null, null, List.of("NOSHOW"), Duration.ofMillis(-1));
        var watch = new RetryWatch(properties, streamBridge, clock);

        watch.failed("P-1", "upsert-reservation", "MRU01", "MRU01/12E45", "Opera does not answer");
        watch.succeeded("P-1", "upsert-reservation");

        Contracts.topic("notifications").assertValid(sentPayload("notifications"));
        Contracts.topic("notification-resolutions").assertValid(sentPayload("notificationResolutions"));
    }
}
