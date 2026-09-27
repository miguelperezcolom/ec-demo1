package io.mateu.ecdemo1.messaging.engine;

import io.mateu.ecdemo1.messaging.TraceContexts;
import io.mateu.workflow.dtos.TraceContext;
import io.mateu.workflow.dtos.events.integration.MessageReceived;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import io.mateu.workflow.dtos.Variable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A process started from a service joins the trace it was asked for in. */
class EngineOutboxTest {

    static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    static EngineOutbox engine(Map<String, String> current) {
        TraceContexts traces = new TraceContexts() {
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
        return new EngineOutbox(null, traces, null, "outboxUpstream");
    }

    static ProcessCreationRequested start() {
        return new ProcessCreationRequested("alta-integracion", "INT-7", List.of(new Variable("integrationId", "INT-7")));
    }

    @Test
    void aProcessStartedInsideATraceCarriesItsContext() {
        var stamped = engine(Map.of(TraceContexts.TRACEPARENT, TRACEPARENT, TraceContexts.TRACESTATE, "k=v"))
                .withTraceContext(start());

        assertThat(((ProcessCreationRequested) stamped).traceContext()).isEqualTo(new TraceContext(TRACEPARENT, "k=v", null));
    }

    @Test
    void untracedItGoesAsBefore() {
        assertThat(engine(Map.of()).withTraceContext(start())).isEqualTo(start());
    }

    @Test
    void aContextAlreadyGivenIsKeptAndOtherRequestsAreLeftAlone() {
        var given = start().withTraceContext(TraceContext.of("00-11111111111111111111111111111111-2222222222222222-01"));
        var message = new MessageReceived("causes-resolved", "INT-7", List.of());
        var engine = engine(Map.of(TraceContexts.TRACEPARENT, TRACEPARENT));

        assertThat(engine.withTraceContext(given)).isEqualTo(given);
        assertThat(engine.withTraceContext(message)).isEqualTo(message);
    }
}
