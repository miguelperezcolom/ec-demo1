package io.mateu.ecdemo1.integrations.outbox;

import io.mateu.ecdemo1.integrations.tracing.Traces;
import io.mateu.workflow.dtos.TraceContext;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.MessageReceived;
import io.mateu.workflow.dtos.events.integration.ProcessCreationRequested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** A process started from here joins the trace it was asked for in. */
class OutboxTraceContextTest {

    static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    static class FixedTraces extends Traces {
        final Map<String, String> current;

        FixedTraces(Map<String, String> current) {
            super(null, null);
            this.current = current;
        }

        @Override
        public Map<String, String> current() {
            return current;
        }
    }

    static Outbox outbox(Map<String, String> current) {
        return new Outbox(null, null, null, new FixedTraces(current));
    }

    static ProcessCreationRequested start() {
        return new ProcessCreationRequested("alta-integracion", "INT-7", List.of(new Variable("integrationId", "INT-7")));
    }

    @Test
    void aProcessStartedInsideATraceCarriesItsContext() {
        var stamped = outbox(Map.of(Traces.TRACEPARENT, TRACEPARENT, Traces.TRACESTATE, "k=v"))
                .withTraceContext(start());

        assertThat(((ProcessCreationRequested) stamped).traceContext()).isEqualTo(new TraceContext(TRACEPARENT, "k=v", null));
    }

    @Test
    void untracedItGoesAsBefore() {
        assertThat(outbox(Map.of()).withTraceContext(start())).isEqualTo(start());
    }

    @Test
    void aContextAlreadyGivenIsKeptAndOtherRequestsAreLeftAlone() {
        var given = start().withTraceContext(TraceContext.of("00-11111111111111111111111111111111-2222222222222222-01"));
        var message = new MessageReceived("causes-resolved", "INT-7", List.of());
        var outbox = outbox(Map.of(Traces.TRACEPARENT, TRACEPARENT));

        assertThat(outbox.withTraceContext(given)).isEqualTo(given);
        assertThat(outbox.withTraceContext(message)).isEqualTo(message);
    }
}
