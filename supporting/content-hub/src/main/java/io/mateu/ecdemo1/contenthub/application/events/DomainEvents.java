package io.mateu.ecdemo1.contenthub.application.events;

import io.mateu.ecdemo1.contenthub.application.out.EventPublisher;
import io.mateu.ecdemo1.contenthub.domain.shared.AggregateRoot;
import io.mateu.ecdemo1.contenthub.domain.shared.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Hands a saved aggregate's events on, inside the use case's transaction: to the outbox, for other
 * systems, and to this service's own listeners ({@link ProjectionListener}), which act once the
 * transaction has committed.
 */
@Service
@RequiredArgsConstructor
public class DomainEvents {

    final EventPublisher outbox;
    final ApplicationEventPublisher local;

    public void drain(AggregateRoot aggregate) {
        aggregate.popEvents().forEach(this::publish);
    }

    public void publish(DomainEvent event) {
        outbox.publish(event);
        local.publishEvent(event);
    }
}
