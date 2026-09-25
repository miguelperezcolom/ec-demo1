package io.mateu.ecdemo1.communication.store;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate froze {@link DeliveryStatus}'s values into a check constraint when the table was created,
 * and ddl-auto update never rewrites it: a database older than INBOX_ONLY refuses it. The enum checks
 * the value; the constraint goes — right after the schema is updated, before any consumer starts
 * writing notifications.
 */
@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
@Slf4j
public class StaleEnumChecks {

    final JdbcTemplate jdbc;

    @PostConstruct
    public void drop() {
        jdbc.execute("alter table if exists notification drop constraint if exists notification_status_check");
        log.info("notification.status is checked by its enum, not by a constraint frozen when the table was created");
    }
}
