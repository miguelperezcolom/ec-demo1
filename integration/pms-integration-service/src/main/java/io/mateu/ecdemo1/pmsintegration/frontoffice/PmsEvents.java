package io.mateu.ecdemo1.pmsintegration.frontoffice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.pms.PmsReservationChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.time.Clock;
import java.util.UUID;

/**
 * Tells whoever consumes the PMS that a reservation was written into it — the connector does not know
 * who that is: the pms-fo integration listens, and projects it to the hotel's front office from
 * Opera. Published after the write, on {@link PmsReservationChanged#TOPIC}; a broker that does not
 * take it fails the step, which the engine retries — the write is idempotent, and so is the event.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PmsEvents {

    public static final String BINDING = "pmsReservations";

    final StreamBridge streamBridge;
    final ObjectMapper objectMapper;
    final Clock clock;

    public void written(String pmsHotelCode, String reservationId, String crsHotelCode, String locator, String origin) {
        var event = new PmsReservationChanged(UUID.randomUUID().toString(), pmsHotelCode, reservationId, crsHotelCode, locator,
                origin, clock.instant());
        byte[] payload;
        try {
            payload = objectMapper.writeValueAsBytes(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event, e);
        }
        if (!streamBridge.send(BINDING, MessageBuilder.withPayload(payload).setHeader(KafkaHeaders.KEY, event.key())
                .setHeader("contentType", MimeTypeUtils.APPLICATION_JSON_VALUE).build())) {
            throw new IllegalStateException("The PMS reservations topic did not take " + event.key());
        }
        log.debug("{} in Opera {}/{}: told ({})", locator, pmsHotelCode, reservationId, origin);
    }
}
