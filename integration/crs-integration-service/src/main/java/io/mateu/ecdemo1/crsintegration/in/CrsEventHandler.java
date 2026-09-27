package io.mateu.ecdemo1.crsintegration.in;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.crsintegration.inbox.Inbox;
import io.mateu.ecdemo1.crsintegration.outbox.Outbox;
import io.mateu.ecdemo1.crsintegration.source.CrsSource;
import io.mateu.ecdemo1.integration.model.events.IntegrationEvent;
import io.mateu.ecdemo1.integration.model.events.PartnerChanged;
import io.mateu.ecdemo1.integration.model.events.ReservationCancelled;
import io.mateu.ecdemo1.integration.model.events.ReservationCreated;
import io.mateu.ecdemo1.integration.model.events.ReservationModified;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Turns what happened in the CRS into the integration's business events.
 *
 * <p>The source event only says that something changed. The booking is read again — the HLA's "the
 * notice only warns; the data is read again" — so a booking deleted since is dropped here rather
 * than sent down the integration, and the hotel comes from the source of truth. The version, on
 * the other hand, is the event's: it is what orders the change, and a later read may already be
 * further on.
 *
 * <p>Inbox and outbox in one transaction: a repeated event does nothing, and a handled one always
 * has its business event on the way.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CrsEventHandler {

    static final String CONSUMER = "crs-events";

    final Inbox inbox;
    final Outbox outbox;
    final CrsSource crs;

    @Transactional
    public void handle(JsonNode event) {
        var type = event.path("type").asText();
        var eventId = event.path("eventId").asText();
        if (eventId.isBlank()) {
            log.warn("Ignoring a CRS event with no id: {}", type);
            return;
        }
        if (!inbox.firstTime(CONSUMER, eventId)) {
            log.debug("Already handled {} {}", type, eventId);
            return;
        }
        translate(type, event).ifPresent(outbox::append);
    }

    Optional<IntegrationEvent> translate(String type, JsonNode e) {
        var eventId = e.path("eventId").asText();
        var occurredAt = Instant.parse(e.path("occurredAt").asText());
        var version = e.path("version").asLong();
        return switch (type) {
            case "booking-created", "booking-modified", "booking-cancelled" -> {
                var bookingId = e.path("bookingId").asText();
                var booking = crs.booking(bookingId);
                if (booking.isEmpty()) {
                    log.warn("Booking {} of event {} no longer exists in the CRS: nothing to integrate", bookingId, eventId);
                    yield Optional.empty();
                }
                var hotel = booking.get().hotelCode();
                yield Optional.of(switch (type) {
                    case "booking-created" -> new ReservationCreated(eventId, occurredAt, hotel, bookingId, version);
                    case "booking-modified" -> new ReservationModified(eventId, occurredAt, hotel, bookingId, version);
                    default -> new ReservationCancelled(eventId, occurredAt, hotel, bookingId, version);
                });
            }
            case "partner-changed" -> Optional.of(
                    new PartnerChanged(eventId, occurredAt, e.path("partnerCode").asText(), version));
            default -> {
                log.debug("No business event for CRS event type {}", type);
                yield Optional.empty();
            }
        };
    }
}
