package io.mateu.ecdemo1.frontoffice.infra.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Keeps Tempo for the traces worth reading. Without this every pass of a scheduled relay — the
 * outboxes run every second or two — and every probe and scrape of /actuator is a trace of its own,
 * and a booking's trace is a needle in them.
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
