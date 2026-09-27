package io.mateu.ecdemo1.pmsintegration.frontoffice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand;
import io.mateu.ecdemo1.pmsintegration.clients.IntegrationClients;
import io.mateu.ecdemo1.pmsintegration.config.OhipProperties;
import io.mateu.ecdemo1.pmsintegration.config.PmsIntegrationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.HashSet;

/**
 * «Proyectar estancia» (pms-fo): an Opera reservation, as Opera holds it now, into the hotel's front
 * office. Read again from Opera — the process carries only its id — mapped to the front office's
 * model ({@link StayMapper}), and sent to the front office as a command on its topic. The front office
 * takes it once and orders it by Opera's last modification: projecting the same reservation twice,
 * or late, leaves the stay as the latest.
 *
 * <p>Who the holder is comes from the chain's MDM — which customer Opera's guest profile is (the
 * cross reference {@code ensure-guest-profile} recorded) and that customer's golden record over
 * Opera's data. One born in Opera the MDM does not know goes as Opera has it. The MDM not answering
 * does not stop the stay: it goes without the customer, and the next projection names it.
 *
 * <p>Transient failures — Opera not answering — propagate, and the engine retries the step.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StayProjection {

    /** The output binding of {@link FrontOfficeCommand#TOPIC}. */
    public static final String BINDING = "frontOfficeCommands";

    final OperaStays stays;
    final IntegrationClients integration;
    final OhipProperties ohip;
    final PmsIntegrationProperties properties;
    final StreamBridge streamBridge;
    final ObjectMapper objectMapper;

    public void project(String hotel, String reservationId) {
        var found = stays.byId(hotel, reservationId);
        if (found.isEmpty()) {
            log.info("{}/{}: Opera does not have it; nothing for the front office", hotel, reservationId);
            return;
        }
        var reservation = found.get();
        var profileId = StayMapper.person(firstGuest(reservation)).pmsProfileId();
        var customerId = profileId == null ? null : integration.customerByPmsProfile(profileId).orElse(null);
        var master = customerId == null ? null : integration.customer(customerId)
                .map(p -> new FrontOfficeCommand.Person(customerId, profileId,
                        ((p.firstName() == null ? "" : p.firstName()) + " " + (p.lastName() == null ? "" : p.lastName())).trim(),
                        p.documentNumber(), p.email(), p.phone()))
                .orElse(null);
        var command = StayMapper.toWriteStay(hotel, reservation, new StayMapper.Context(ohip.externalSystemCode(),
                new HashSet<>(properties.noShowCancellationCodes()), customerId, master));
        send(command);
        log.info("{}/{} ({}) to the front office: {} {}..{} {} {} v{}", hotel, reservationId,
                command.crsLocator() == null ? "born in Opera" : "CRS " + command.crsLocator(), command.status(),
                command.checkIn(), command.checkOut(), command.roomTypeCode(), command.boardCode() == null ? "room only" : command.boardCode(),
                command.pmsVersion());
        if (customerId != null) {
            integration.xref(customerId, "FRONT_OFFICE", customerId, hotel + "/" + reservationId);
        }
    }

    static com.fasterxml.jackson.databind.JsonNode firstGuest(com.fasterxml.jackson.databind.JsonNode reservation) {
        for (var guest : reservation.path("reservationGuests")) {
            if (guest.path("primary").asBoolean(false)) {
                return guest;
            }
        }
        return reservation.path("reservationGuests").path(0);
    }

    /** On the front office's topic, keyed by the reservation; a broker that does not take it fails the step, which is retried. */
    void send(FrontOfficeCommand command) {
        byte[] payload;
        try {
            payload = objectMapper.writerFor(FrontOfficeCommand.class).writeValueAsBytes(command);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + command, e);
        }
        var sent = streamBridge.send(BINDING, MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.KEY, command.key())
                .setHeader("contentType", MimeTypeUtils.APPLICATION_JSON_VALUE)
                .build());
        if (!sent) {
            throw new IllegalStateException("The front office's topic did not take " + command.key());
        }
    }
}
