package io.mateu.ecdemo1.mapping.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.messaging.MessagingProperties;
import io.mateu.ecdemo1.messaging.TraceContexts;
import io.mateu.ecdemo1.messaging.engine.EngineOutbox;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A process the mapping starts joins the trace it was asked for in; nothing traced, nothing added. */
class OutboxTraceTest {

    static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    record Written(String destination, String key, String type, String payload) {
    }

    final List<Written> written = new ArrayList<>();

    static TraceContexts tracesWith(Map<String, String> current) {
        return new TraceContexts() {
            @Override
            public Map<String, String> current() {
                return current;
            }

            @Override
            public void tag(String key, String value) {
            }

            @Override
            public void continuing(String traceparent, String tracestate, String name, Send send) throws Exception {
                send.accept(Map.of());
            }
        };
    }

    Outbox outbox(TraceContexts traces) {
        var shared = new io.mateu.ecdemo1.messaging.Outbox(null, MessagingProperties.defaults(), traces, Clock.systemUTC()) {
            @Override
            public void append(String destination, String key, String type, String payload, Map<String, String> headers) {
                written.add(new Written(destination, key, type, payload));
            }
        };
        var mapper = new ObjectMapper();
        var engine = new EngineOutbox(shared, traces,
                new StaticListableBeanFactory(Map.of("objectMapper", mapper)).getBeanProvider(ObjectMapper.class),
                Outbox.ENGINE);
        return new Outbox(shared, engine, mapper, Clock.systemUTC());
    }

    static ProcessCreationRequested successor() {
        return new ProcessCreationRequested("proyectar-reserva", "K1>r", List.of(new Variable("processKey", "K1>r")));
    }

    @Test
    void aProcessStartedHereJoinsTheCurrentTrace() throws Exception {
        outbox(tracesWith(Map.of("traceparent", TRACEPARENT, "tracestate", "k=v"))).appendToEngine(successor());

        var message = written.getFirst();
        assertThat(message.destination()).isEqualTo("outboxUpstream");
        assertThat(message.key()).isEqualTo(successor().partitionKey());
        var payload = new ObjectMapper().readTree(message.payload());
        assertThat(payload.at("/traceContext/traceparent").asText()).isEqualTo(TRACEPARENT);
        assertThat(payload.at("/traceContext/tracestate").asText()).isEqualTo("k=v");
    }

    @Test
    void untracedItStartsAsBefore() throws Exception {
        outbox(tracesWith(Map.of())).appendToEngine(successor());

        assertThat(new ObjectMapper().readTree(written.getFirst().payload()).has("traceContext")).isFalse();
    }
}
