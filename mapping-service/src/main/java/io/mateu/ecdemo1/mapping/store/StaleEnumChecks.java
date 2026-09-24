package io.mateu.ecdemo1.mapping.store;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hibernate writes a check constraint listing an enum's values when it creates a table, and
 * {@code ddl-auto: update} never rewrites it. A database created before {@code INTEGRATION_INACTIVE}
 * existed refuses every cause of that type: the preparation of a reservation for a hotel with no
 * integration failed and was retried forever, instead of waiting on its cause. The type is already
 * an enum in Java; the constraint adds nothing but that failure, so it goes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaleEnumChecks {

    final EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void drop() {
        em.createNativeQuery("alter table if exists cause drop constraint if exists cause_type_check").executeUpdate();
        // The same for an entry's status, frozen before WITHDRAWN existed.
        em.createNativeQuery("alter table if exists mapping_entry drop constraint if exists mapping_entry_status_check").executeUpdate();
        log.info("cause.type is checked by its enum, not by a constraint frozen when the table was created");
    }
}
