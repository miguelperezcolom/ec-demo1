package io.mateu.ecdemo1.journey;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where the journey is read from: Tempo's query API (inside the cluster; the browser never talks to
 * it), the services that know what the spans do not say, and Grafana for the link an engineer
 * follows to the technical trace.
 *
 * @param tempoUrl      Tempo's HTTP API, e.g. http://tempo.observability.svc.cluster.local:3200
 * @param grafanaUrl    the public Grafana, for "Ver traza técnica"
 * @param lookback      how far back a booking's traces are searched
 * @param bookingUrl    the CRS (booking)
 * @param mdmUrl        the customer MDM
 * @param mappingUrl    the mapping service
 * @param zone          the time zone times are shown in
 * @param cacheFor      how long a booking's journey is kept before Tempo is asked again
 */
@ConfigurationProperties(prefix = "journey")
public record JourneyProperties(String tempoUrl, String grafanaUrl, Duration lookback, String bookingUrl,
                                String mdmUrl, String mappingUrl, String zone, Duration cacheFor) {

    public JourneyProperties {
        lookback = lookback == null ? Duration.ofDays(7) : lookback;
        zone = zone == null || zone.isBlank() ? "Europe/Madrid" : zone;
        cacheFor = cacheFor == null ? Duration.ofSeconds(5) : cacheFor;
    }
}
