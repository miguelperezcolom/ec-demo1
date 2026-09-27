package io.mateu.ecdemo1.communication.store;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate freezes an enum's values into a check constraint when it creates a table, and ddl-auto
 * update never rewrites it: a database older than a value refuses it — INBOX_ONLY in
 * notification.status once, INTEGRATION_NEEDS_ATTENTION in notification.type later, which silently
 * dropped every onboarding notice. So none of them stays: every such constraint in this schema goes,
 * right after the schema is updated and before any consumer writes, and the enums check the values.
 */
@Component
@DependsOn("entityManagerFactory")
@RequiredArgsConstructor
@Slf4j
public class StaleEnumChecks {

    final JdbcTemplate jdbc;

    @PostConstruct
    public void drop() {
        // Hibernate's enum checks read "CHECK (((col)::text = ANY ((ARRAY['A'::character varying, ...])::text[])))".
        var frozen = jdbc.queryForList("""
                select c.conrelid::regclass::text as tbl, c.conname as name
                from pg_constraint c join pg_namespace n on n.oid = c.connamespace
                where c.contype = 'c' and n.nspname = current_schema()
                  and pg_get_constraintdef(c.oid) like '%= ANY%ARRAY[%'""");
        for (var row : frozen) {
            jdbc.execute("alter table %s drop constraint if exists %s".formatted(row.get("tbl"), row.get("name")));
            log.info("{}.{} dropped: the enum checks its values, not a constraint frozen when the table was created",
                    row.get("tbl"), row.get("name"));
        }
    }
}
