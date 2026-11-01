package io.mateu.ecdemo1.contenthub.application.out;

import io.mateu.ecdemo1.contenthub.domain.shared.DomainEvent;

/** Where domain events leave the service: the outbox, in the caller's transaction. */
public interface EventPublisher {

    void publish(DomainEvent event);
}
