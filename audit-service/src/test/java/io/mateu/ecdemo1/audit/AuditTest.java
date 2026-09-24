package io.mateu.ecdemo1.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.audit.ui.pages.AuditFilters;
import io.mateu.ecdemo1.audit.ui.pages.AuditPage;
import io.mateu.ecdemo1.audit.ui.pages.AuditRow;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import io.mateu.uidl.data.DateRange;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.data.SearchRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The audit trail against a real Postgres. Kafka is left out: the topic's work is done by handing
 * the consumer the message the topic would.
 */
@SpringBootTest(properties = {"spring.cloud.stream.function.autodetect=false", "spring.cloud.function.definition=",
        "audit.zone=UTC"})
@Testcontainers
class AuditTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    Consumer<Message<byte[]>> consumeAudit;
    @Autowired
    ObjectProvider<AuditPage> page;
    @Autowired
    ObjectMapper objectMapper;

    static boolean loaded;

    void load() throws Exception {
        if (loaded) {
            return;
        }
        loaded = true;
        deliver(new AuditedAction("a1", Instant.parse("2026-09-20T10:00:00Z"), "integrations", "Activate integration",
                "MRU01", "Ana García", "{\"id\":\"i-1\"}", true, "Activation requested — status ACTIVATING"));
        deliver(new AuditedAction("a2", Instant.parse("2026-09-22T11:00:00Z"), "mapping", "Approve mapping",
                null, "agent", "{\"entryId\":\"e-7\"}", true, "ROOM_TYPE DBL → XDBL: APPROVED (v1)"));
        deliver(new AuditedAction("a3", Instant.parse("2026-09-23T12:00:00Z"), "integrations", "Pause integration",
                "MRU01", "Luis Pons", "{\"id\":\"i-1\"}", false, "Only an active integration can be paused"));
        // at least once: the same action delivered again is not a second record
        deliver(new AuditedAction("a1", Instant.parse("2026-09-20T10:00:00Z"), "integrations", "Activate integration",
                "MRU01", "Ana García", "{\"id\":\"i-1\"}", true, "Activation requested — status ACTIVATING"));
    }

    void deliver(AuditedAction action) throws Exception {
        consumeAudit.accept(MessageBuilder.withPayload(objectMapper.writeValueAsBytes(action)).build());
    }

    List<AuditRow> search(String text, AuditFilters filters) throws Exception {
        load();
        return page.getObject().search(new SearchRequest(text, filters, null, new Pageable(0, 50, List.of())), null)
                .page().content();
    }

    static AuditFilters filters(String field, Object value) throws Exception {
        var filters = new AuditFilters();
        Field f = AuditFilters.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(filters, value);
        return filters;
    }

    @Test
    void theTrailIsNewestFirstAndAnActionDeliveredTwiceIsOneRecord() throws Exception {
        assertThat(search("", null)).extracting(AuditRow::action)
                .containsExactly("Pause integration", "Approve mapping", "Activate integration");
    }

    @Test
    void theFreeTextLooksInEveryColumnParametersAndResponsesIncluded() throws Exception {
        assertThat(search("xdbl", null)).extracting(AuditRow::action).containsExactly("Approve mapping");
        assertThat(search("i-1", null)).hasSize(2);
        assertThat(search("garcía", null)).extracting(AuditRow::user).containsExactly("Ana García");
    }

    @Test
    void theFiltersNarrowByHotelUserDateActionAndOutcome() throws Exception {
        assertThat(search("", filters("hotel", "mru01"))).hasSize(2);
        assertThat(search("", filters("user", "luis"))).extracting(AuditRow::action).containsExactly("Pause integration");
        assertThat(search("", filters("when", new DateRange(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22)))))
                .extracting(AuditRow::action).containsExactly("Approve mapping");
        assertThat(search("", filters("outcome", Set.of(AuditFilters.Outcome.REFUSED))))
                .extracting(AuditRow::response).containsExactly("Only an active integration can be paused");
        assertThat(search("", filters("service", "mapping"))).hasSize(1);
    }
}
