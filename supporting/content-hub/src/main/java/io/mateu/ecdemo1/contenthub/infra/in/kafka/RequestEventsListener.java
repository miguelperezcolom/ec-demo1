package io.mateu.ecdemo1.contenthub.infra.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import io.mateu.ecdemo1.contenthub.application.usecases.RequestService;
import io.mateu.ecdemo1.contenthub.domain.request.RequestStatus;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * What the solicitud-empleado process announces on «solicitudes» (its PUBLISH_EVENT steps, the payload
 * {requestId, status, by, comment}): each one moves the request along. Applying the same announcement
 * twice changes nothing, so a redelivery needs no inbox.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RequestEventsListener {

    final RequestService requests;

    @KafkaListener(topics = "solicitudes", groupId = "ec-demo1-content-hub-solicitudes",
            autoStartup = "${content-hub.kafka.enabled:true}")
    public void on(byte[] payload) {
        var text = new String(payload, StandardCharsets.UTF_8);
        try {
            JsonNode event = Json.read(text, JsonNode.class);
            requests.apply(event.path("requestId").asText(), RequestStatus.valueOf(event.path("status").asText()),
                    event.path("by").asText(null), event.path("comment").asText(null));
        } catch (RuntimeException e) {
            log.error("Unreadable solicitudes event, dropped: {}", text, e);
        }
    }
}
