package io.mateu.ecdemo1.mapping.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.mapping.tracing.Traces;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A process the mapping starts joins the trace it was asked for in; nothing traced, nothing added. */
class OutboxTraceTest {

    static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    final List<OutboxMessageEntity> saved = new ArrayList<>();
    final OutboxMessageRepository repository = (OutboxMessageRepository) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{OutboxMessageRepository.class}, (proxy, method, args) -> {
                if (method.getName().equals("save")) {
                    saved.add((OutboxMessageEntity) args[0]);
                    return args[0];
                }
                throw new UnsupportedOperationException(method.getName());
            });

    static Traces tracesWith(Map<String, String> current) {
        return new Traces(null, null) {
            @Override
            public Map<String, String> current() {
                return current;
            }
        };
    }

    static ProcessCreationRequested successor() {
        return new ProcessCreationRequested("proyectar-reserva", "K1>r", List.of(new Variable("processKey", "K1>r")));
    }

    @Test
    void aProcessStartedHereJoinsTheCurrentTrace() throws Exception {
        new Outbox(repository, new ObjectMapper(), Clock.systemUTC(),
                tracesWith(Map.of("traceparent", TRACEPARENT, "tracestate", "k=v")))
                .appendToEngine(successor());

        var message = saved.getFirst();
        assertThat(message.traceparent).isEqualTo(TRACEPARENT);
        assertThat(message.tracestate).isEqualTo("k=v");
        var payload = new ObjectMapper().readTree(message.payload);
        assertThat(payload.at("/traceContext/traceparent").asText()).isEqualTo(TRACEPARENT);
        assertThat(payload.at("/traceContext/tracestate").asText()).isEqualTo("k=v");
    }

    @Test
    void untracedItStartsAsBefore() throws Exception {
        new Outbox(repository, new ObjectMapper(), Clock.systemUTC(), tracesWith(Map.of())).appendToEngine(successor());

        var message = saved.getFirst();
        assertThat(message.traceparent).isNull();
        assertThat(new ObjectMapper().readTree(message.payload).has("traceContext")).isFalse();
    }
}
