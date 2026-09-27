package io.mateu.ecdemo1.erp.tracing;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Keeps Tempo for the traces worth reading. Without this every pass of a scheduled poller — the
 * outbox relay runs twice a second — and every probe and scrape of /actuator is a trace of its own,
 * and a booking's trace is a needle in them. The relay still traces what it sends: it opens a span
 * of its own under the context stored with each message.
 */
@Configuration
public class TracingConfig {

    @Bean
    public ObservationPredicate withoutPollersAndProbes() {
        return (name, context) -> {
            if (name.startsWith("tasks.scheduled")) {
                return false;
            }
            if (context instanceof ServerRequestObservationContext request && request.getCarrier() != null) {
                return !request.getCarrier().getRequestURI().startsWith("/actuator");
            }
            return true;
        };
    }
}
