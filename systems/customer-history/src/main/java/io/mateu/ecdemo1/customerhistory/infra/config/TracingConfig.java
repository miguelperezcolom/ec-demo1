package io.mateu.ecdemo1.customerhistory.infra.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/** Keeps Tempo for the traces worth reading: no scheduled pollers, probes or scrapes. */
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
