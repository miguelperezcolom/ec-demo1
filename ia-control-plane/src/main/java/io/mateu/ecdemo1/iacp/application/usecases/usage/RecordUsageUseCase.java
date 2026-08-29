package io.mateu.ecdemo1.iacp.application.usecases.usage;

import io.mateu.ecdemo1.iacp.application.out.usage.UsageEvent;
import io.mateu.ecdemo1.iacp.application.out.usage.UsageLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a reported prompt cost. Thin on purpose: the shape of a usage event is decided by the
 * agent that reports it, this only persists it. The value of a use case here is that the recording
 * has one transactional boundary and one place to grow — a validation, a derived metric — without
 * the controller having to.
 */
@Service
@RequiredArgsConstructor
public class RecordUsageUseCase {

    private final UsageLog usageLog;

    @Transactional
    public void handle(UsageEvent event) {
        usageLog.append(event);
    }
}
