package io.mateu.ecdemo1.integrations.lifecycle;

import io.mateu.ecdemo1.integrations.audit.AuditScope;
import io.mateu.ecdemo1.integrations.outbox.Commands;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * The one way the integrations are written: a short transaction that holds only database work.
 * Whatever was needed from other services is asked before it; whatever is to be asked of them after
 * it is written to the outbox inside it ({@link Commands}), and published to Kafka once it commits.
 * The audited action on this thread, if any, has its record written in it.
 */
@Component
@RequiredArgsConstructor
public class Writes {

    final PlatformTransactionManager transactions;
    final AuditScope audit;

    public <T> T write(Supplier<T> body) {
        return new TransactionTemplate(transactions).execute(status -> {
            var r = body.get();
            audit.recordWithin(r);
            return r;
        });
    }
}
