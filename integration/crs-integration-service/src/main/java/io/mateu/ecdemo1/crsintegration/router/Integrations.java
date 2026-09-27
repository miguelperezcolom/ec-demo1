package io.mateu.ecdemo1.crsintegration.router;

import io.mateu.ecdemo1.crsintegration.config.CrsProperties;
import io.mateu.ecdemo1.crsintegration.config.TolerantReader;
import io.mateu.ecdemo1.integration.model.integration.IntegrationStatus;
import io.mateu.ecdemo1.integration.model.integration.IntegrationView;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Which CRS hotels have an integration, from the integrations service — kept for a short while, so a
 * burst of reservations asks once, and an integration registered in the console counts within it.
 *
 * <p>A hotel with no integration, or a decommissioned one, has nothing to project its reservations
 * to: a process started for them could only wait for an integration that may never exist. When one
 * is registered, the backfill brings the hotel's reservations in — that is what it is for.
 */
@Component
public class Integrations {

    static final Duration KEEP = Duration.ofSeconds(30);

    record Kept(List<IntegrationView> views, Instant at) {
    }

    final RestClient integrations;
    final Clock clock;
    volatile Kept kept;

    public Integrations(CrsProperties properties, TolerantReader reader, Clock clock) {
        this.clock = clock;
        this.integrations = RestClient.builder()
                .baseUrl(properties.integrationsUrl())
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.addFirst(new MappingJackson2HttpMessageConverter(reader.mapper()));
                })
                .build();
    }

    /** Whether the CRS hotel has an integration that has not been taken down. */
    public boolean integrated(String crsHotelCode) {
        return views().stream().anyMatch(i -> crsHotelCode.equals(i.crsHotelCode())
                && i.status() != IntegrationStatus.DECOMMISSIONED);
    }

    List<IntegrationView> views() {
        var current = kept;
        if (current != null && current.at().plus(KEEP).isAfter(clock.instant())) {
            return current.views();
        }
        var list = integrations.get().uri("/integrations/views").retrieve()
                .body(new ParameterizedTypeReference<List<IntegrationView>>() {
                });
        var fresh = list == null ? List.<IntegrationView>of() : list;
        kept = new Kept(fresh, clock.instant());
        return fresh;
    }
}
