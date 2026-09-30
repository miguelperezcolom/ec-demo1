package io.mateu.ecdemo1.notices.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeMoment;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.NoticeType;
import io.mateu.ecdemo1.integration.model.notice.NoticeChanged.SubjectType;
import io.mateu.ecdemo1.notices.InMemory;
import io.mateu.ecdemo1.notices.application.Notices;
import io.mateu.ecdemo1.notices.infra.in.async.CustomerNoticesConsumer;
import io.mateu.ecdemo1.notices.infra.out.NoticeOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.support.MessageBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The notices service's topics: what it publishes on notices is what the topic's schema says, as the
 * outbox really writes it with the application's mapper; and every example of customer-notices, the
 * MDM's, is read the way its consumer reads the topic and reaches notices published again.
 */
class NoticesContractsTest {

    /** The application's mapper, as Spring Boot's auto-configuration builds it. */
    static ObjectMapper bootMapper() {
        try (var context = new AnnotationConfigApplicationContext(JacksonAutoConfiguration.class)) {
            return context.getBean(ObjectMapper.class);
        }
    }

    final ObjectMapper boot = bootMapper();

    @Test
    void theNoticesTheOutboxWritesAreWhatTheSchemaSays() {
        var written = new ArrayList<String>();
        var keys = new ArrayList<String>();
        var outbox = new NoticeOutbox((binding, key, type, payload) -> {
            assertThat(binding).isEqualTo(NoticeOutbox.BINDING);
            keys.add(key);
            written.add(payload);
        }, boot);
        var memory = new InMemory();
        var notices = new Notices(memory.repository, outbox::write, code -> java.util.Optional.of("Nordic Travel"),
                (c, id) -> true, InMemory.CLOCK);

        notices.create(new Notices.Draft(SubjectType.RESERVATION, "12E45", "MRU01", "Cuna en la habitación",
                NoticeType.IMPORTANT, LocalDate.of(2026, 11, 1), null,
                EnumSet.of(NoticeMoment.PRE_ARRIVAL, NoticeMoment.CHECK_IN), true), "Ana");
        notices.create(new Notices.Draft(SubjectType.PARTNER, "NORDTRAVEL", null, "Bono obligatorio",
                NoticeType.BLOCKING, null, null, EnumSet.of(NoticeMoment.CHECK_IN), true), "Ana");
        Contracts.topic("customer-notices").examples().forEach(json ->
                new CustomerNoticesConsumer(notices, boot).consumeCustomerNotices()
                        .accept(MessageBuilder.withPayload(json.getBytes()).build()));

        var schema = Contracts.topic("notices");
        assertThat(written).hasSizeGreaterThanOrEqualTo(4);
        written.forEach(schema::assertValid);
        assertThat(keys).contains("RESERVATION:12E45", "PARTNER:NORDTRAVEL", "CUSTOMER:C-00042");
    }

    @Test
    void everyExampleOfCustomerNoticesIsTakenAndPublishedAgain() {
        var memory = new InMemory();
        var consumer = new CustomerNoticesConsumer(memory.notices, boot).consumeCustomerNotices();

        List<String> examples = Contracts.topic("customer-notices").examples();
        examples.forEach(json -> consumer.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        assertThat(memory.published).hasSize(examples.size());
        assertThat(memory.published).allSatisfy(e -> {
            assertThat(e.subjectType()).isEqualTo(SubjectType.CUSTOMER);
            assertThat(e.subjectId()).isNotBlank();
            assertThat(e.moments()).isNotEmpty();
            assertThat(e.source()).isEqualTo("SALESFORCE");
        });
    }
}
