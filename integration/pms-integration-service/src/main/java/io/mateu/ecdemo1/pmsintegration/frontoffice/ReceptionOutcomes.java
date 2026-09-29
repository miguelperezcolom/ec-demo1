package io.mateu.ecdemo1.pmsintegration.frontoffice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.Invoice;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.ReceptionOperation;
import io.mateu.ecdemo1.integration.model.frontoffice.FrontOfficeCommand.RecordReception;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.UUID;

/**
 * Tells the hotel's front office how the PMS took what its reception did — refused, and why; the room
 * Opera put the guests in; the check-out's invoice ({@link RecordReception}). On the front office's
 * topic, keyed by the reservation like the stays, so it stays in order with them. A broker that does
 * not take it fails the step, which the engine retries — the front office writes it by state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReceptionOutcomes {

    final StreamBridge streamBridge;
    final ObjectMapper objectMapper;

    public void refused(String pmsHotelCode, String pmsReservationId, String stayId, ReceptionOperation operation,
                        String reason) {
        send(new RecordReception(UUID.randomUUID().toString(), pmsHotelCode, pmsReservationId, stayId, operation, true,
                reason, null, null));
    }

    public void done(String pmsHotelCode, String pmsReservationId, String stayId, ReceptionOperation operation,
                     String detail, String roomNumber, Invoice invoice) {
        send(new RecordReception(UUID.randomUUID().toString(), pmsHotelCode, pmsReservationId, stayId, operation, false,
                detail, roomNumber, invoice));
    }

    void send(RecordReception command) {
        byte[] payload;
        try {
            payload = objectMapper.writerFor(FrontOfficeCommand.class).writeValueAsBytes(command);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + command.commandId(), e);
        }
        var sent = streamBridge.send(StayProjection.BINDING, MessageBuilder.withPayload(payload)
                .setHeader(KafkaHeaders.KEY, command.key())
                .setHeader("contentType", MimeTypeUtils.APPLICATION_JSON_VALUE)
                .build());
        if (!sent) {
            throw new IllegalStateException("The front office's topic did not take " + command.key());
        }
        log.info("{} {} of stay {}: told the front office ({}{})", command.operation(), command.key(), command.stayId(),
                command.refused() ? "refused: " + command.detail() : "done",
                command.invoice() == null ? "" : ", invoice " + command.invoice().number());
    }
}
