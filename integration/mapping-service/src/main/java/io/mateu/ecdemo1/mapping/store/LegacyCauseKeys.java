package io.mateu.ecdemo1.mapping.store;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cause keys were {@code type/scope/subject}; they are {@code type:scope:subject} now, so that a key
 * is one segment of a route. Rewrites the keys stored the old way — otherwise the services that
 * resolve a cause by its key would no longer find it, and what waits on it would wait forever.
 * Idempotent: once nothing has a slash, it changes nothing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyCauseKeys {

    final EntityManager em;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void rewrite() {
        var links = em.createNativeQuery("update waiter_cause set cause_key = replace(cause_key, '/', ':') where cause_key like '%/%'")
                .executeUpdate();
        var causes = em.createNativeQuery("update cause set cause_key = replace(cause_key, '/', ':') where cause_key like '%/%'")
                .executeUpdate();
        if (causes + links > 0) {
            log.info("Rewrote {} cause key(s) and {} wait(s) from type/scope/subject to type:scope:subject", causes, links);
        }
    }
}
