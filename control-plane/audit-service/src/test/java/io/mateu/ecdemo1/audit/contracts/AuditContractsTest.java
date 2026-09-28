package io.mateu.ecdemo1.audit.contracts;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.audit.config.AuditConfig;
import io.mateu.ecdemo1.audit.store.AuditTrail;
import io.mateu.ecdemo1.contracts.testing.Contracts;
import io.mateu.ecdemo1.integration.model.audit.AuditedAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Every example of the audit topic is recorded by the consumer, read as the topic delivers it. */
class AuditContractsTest {

    static ObjectMapper bootMapper() {
        try (var context = new AnnotationConfigApplicationContext(JacksonAutoConfiguration.class)) {
            return context.getBean(ObjectMapper.class);
        }
    }

    @Test
    void everyExampleOfAuditIsRecorded() {
        var trail = mock(AuditTrail.class);
        var consumer = new AuditConfig().consumeAudit(trail, bootMapper());

        var examples = Contracts.topic("audit").examples();
        examples.forEach(json -> consumer.accept(MessageBuilder.withPayload(json.getBytes()).build()));

        var read = ArgumentCaptor.forClass(AuditedAction.class);
        verify(trail, times(examples.size())).record(read.capture());
        assertThat(read.getAllValues()).allSatisfy(a -> {
            assertThat(a.actionId()).isNotBlank();
            assertThat(a.at()).isNotNull();
            assertThat(a.service()).isNotBlank();
        });
    }
}
