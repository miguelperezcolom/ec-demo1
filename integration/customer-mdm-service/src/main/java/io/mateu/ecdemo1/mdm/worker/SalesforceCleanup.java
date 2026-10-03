package io.mateu.ecdemo1.mdm.worker;

import io.mateu.ecdemo1.mdm.salesforce.ConsolidationEvents;
import io.mateu.ecdemo1.mdm.salesforce.ConsolidationPoll;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Salesforce as deploy/demo/zero.sh leaves it: the change requests' Cases and every contact deleted,
 * two hundred a call, and the MDM reading Salesforce's events from now on — what it just deleted is
 * not the MDM's to replay. Idempotent: what is gone is not found again.
 */
@Component
@Slf4j
public class SalesforceCleanup {

    static final String CASES = "SELECT Id FROM Case WHERE MdmRequestId__c != null";
    static final String CONTACTS = "SELECT Id FROM Contact";

    final SalesforceClient salesforce;
    final ConsolidationEvents events;
    final ConsolidationPoll poll;
    final JdbcTemplate jdbc;

    public SalesforceCleanup(SalesforceClient salesforce, ConsolidationEvents events, ConsolidationPoll poll,
                             JdbcTemplate jdbc) {
        this.salesforce = salesforce;
        this.events = events;
        this.poll = poll;
        this.jdbc = jdbc;
    }

    /** What it deleted, in a line. */
    public String clean() {
        if (!salesforce.enabled()) {
            return "Salesforce no configurado: nada que borrar";
        }
        var subscribed = events.isRunning();
        if (subscribed) {
            events.stop();
        }
        try {
            // The poll for merged contacts reads the recycle bin since its cursor: held until the cursor
            // says now, or it would take every contact deleted here for a merge (it is synchronized).
            synchronized (poll) {
                var cases = salesforce.delete(SalesforceClient.Purpose.DEMO_RESET,
                        salesforce.ids(SalesforceClient.Purpose.DEMO_RESET, CASES));
                var contacts = salesforce.delete(SalesforceClient.Purpose.DEMO_RESET,
                        salesforce.ids(SalesforceClient.Purpose.DEMO_RESET, CONTACTS));
                jdbc.update("delete from salesforce_cursor where name like 'pubsub%'");
                jdbc.update("update salesforce_cursor set until = now() where name = 'poll'");
                var summary = cases + " Case(s), " + contacts + " contacto(s)";
                log.info("Demo reset — Salesforce: {} deleted; its events resume from now", summary);
                return summary;
            }
        } finally {
            if (subscribed) {
                events.start();
            }
        }
    }
}
