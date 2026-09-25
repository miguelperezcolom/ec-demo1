package io.mateu.ecdemo1.audit.store;

import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Materialises the audited actions. The outbox delivers at least once: an action seen before is not recorded again. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditTrail {

    final AuditRecordRepository records;
    final Clock clock;

    @Transactional
    public boolean record(AuditedAction action) {
        if (action.actionId() == null || records.existsById(action.actionId())) {
            return false;
        }
        records.save(AuditRecord.of(action, clock.instant()));
        log.debug("Audited: {} {} by {} on {}", action.service(), action.action(), action.by(), action.hotelCode());
        return true;
    }
}
