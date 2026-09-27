package io.mateu.ecdemo1.erp.infra.out.persistence;

import io.mateu.ecdemo1.erp.application.out.Inbox;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JpaInbox implements Inbox {

    final EntityManager entityManager;
    final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String consumer, String messageId) {
        return entityManager.createNativeQuery("""
                        insert into inbox_entry (consumer, event_id, received_at) values (?, ?, ?)
                        on conflict do nothing""")
                .setParameter(1, consumer)
                .setParameter(2, messageId)
                .setParameter(3, Instant.now(clock))
                .executeUpdate() == 1;
    }
}
