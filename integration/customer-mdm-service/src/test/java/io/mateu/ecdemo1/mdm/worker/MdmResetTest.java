package io.mateu.ecdemo1.mdm.worker;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.demoreset.ConsumerPause;
import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.StreamBindingsPause;
import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.ecdemo1.mdm.store.ChangeRequestRepository;
import io.mateu.ecdemo1.mdm.store.Consolidation;
import io.mateu.ecdemo1.mdm.store.ConsolidationRepository;
import io.mateu.ecdemo1.mdm.store.Cursor;
import io.mateu.ecdemo1.mdm.store.CursorRepository;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.Source;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import io.mateu.ecdemo1.mdm.store.Xref;
import io.mateu.ecdemo1.mdm.store.XrefRepository;
import io.mateu.workflow.worker.api.TaskContext;
import io.mateu.workflow.worker.api.TaskRegistration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The MDM's own reset, against its real schema, Salesforce unconfigured: the customers and all that
 * hangs from them go, the Salesforce cursors stay — and clean-salesforce, with no Salesforce, has
 * nothing to delete.
 */
@SpringBootTest(properties = {"spring.cloud.stream.function.autodetect=false", "spring.cloud.function.definition=",
        "demo-reset.settle=0s", "mdm.projection-tick=1h", "mdm.poll=1h", "mdm.propagation-tick=1h", "mdm.change-poll=1h",
        "mdm.refresh-tick=1h", "mdm.change-tick=1h", "mdm.notice-tick=1h", "mdm.notice-poll=1h",
        "mdm.salesforce-merge-tick=1h", "mdm.marking-tick=1h", "mdm.cleanup.enabled=false"})
@Testcontainers
class MdmResetTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    DemoReset reset;
    @Autowired
    List<ConsumerPause> consumers;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CustomerRepository customers;
    @Autowired
    SourceRepository sources;
    @Autowired
    XrefRepository xrefs;
    @Autowired
    ConsolidationRepository consolidations;
    @Autowired
    ChangeRequestRepository changes;
    @Autowired
    CursorRepository cursors;
    @Autowired
    @Qualifier("cleanSalesforceTask")
    TaskRegistration<MdmTasks.CleanSalesforce, MdmTasks.Cleaned> cleanSalesforce;

    int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    void theCustomersGoTheCursorsStay() {
        var c = new Customer();
        c.id = "C-1";
        c.status = CustomerStatus.PROVISIONAL;
        customers.save(c);
        var s = new Source();
        s.sourceKey = "MRU01/L1/1";
        s.customerId = "C-1";
        sources.save(s);
        var x = new Xref();
        x.id = "C-1|OPERA|123";
        x.customerId = "C-1";
        xrefs.save(x);
        var k = new Consolidation();
        k.absorbedId = "C-2";
        k.survivorId = "C-1";
        consolidations.save(k);
        var r = new ChangeRequest();
        r.id = "R-1";
        r.customerId = "C-1";
        changes.save(r);
        var cursor = new Cursor();
        cursor.name = Cursor.POLL;
        cursor.until = Instant.parse("2026-09-01T00:00:00Z");
        cursors.save(cursor);

        var outcome = reset.run();
        reset.run(); // idempotent

        for (var table : List.of("customer", "customer_source", "customer_xref", "consolidation", "change_request")) {
            assertThat(count(table)).as(table).isZero();
        }
        assertThat(outcome.tables()).contains("customer", "change_request");
        assertThat(cursors.findById(Cursor.POLL)).isPresent();
        assertThat(consumers).hasAtLeastOneElementOfType(StreamBindingsPause.class)
                .hasAtLeastOneElementOfType(SalesforceEventsPause.class);
    }

    @Test
    void withoutSalesforceThereIsNothingToDelete() throws Exception {
        assertThat(cleanSalesforce.ref()).isEqualTo("clean-salesforce@1");
        assertThat(cleanSalesforce.handler().handle(new MdmTasks.CleanSalesforce("reset-demo:1"), context()).salesforceDeleted())
                .isEqualTo("Salesforce no configurado: nada que borrar");
    }

    static TaskContext context() {
        return new TaskContext() {
            public String taskExecutionId() { return "t"; }
            public String processId() { return "p"; }
            public String workflowDefinitionId() { return "reset-demo"; }
            public String stepId() { return "clean-salesforce"; }
            public boolean isCancelled() { return false; }
            public void progress(String message) { }
        };
    }

}
