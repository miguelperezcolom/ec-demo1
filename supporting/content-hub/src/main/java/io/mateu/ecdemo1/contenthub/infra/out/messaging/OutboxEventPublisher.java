package io.mateu.ecdemo1.contenthub.infra.out.messaging;

import io.mateu.ecdemo1.contenthub.application.out.EventPublisher;
import io.mateu.ecdemo1.contenthub.domain.shared.DomainEvent;
import io.mateu.ecdemo1.contenthub.infra.out.persistence.Json;
import io.mateu.ecdemo1.messaging.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The domain events, out on {@code content-events} through the shared outbox: in the use case's
 * transaction, keyed by their subject so one item's events keep their order.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher implements EventPublisher {

    public static final String TOPIC = "content-events";

    final Outbox outbox;

    @Override
    public void publish(DomainEvent event) {
        var type = event.getClass().getSimpleName();
        outbox.append(TOPIC, event.subject(), type, Json.write(Map.of("type", type, "event", event)), Map.of());
    }
}
