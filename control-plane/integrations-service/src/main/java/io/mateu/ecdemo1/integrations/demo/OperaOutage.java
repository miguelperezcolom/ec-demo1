package io.mateu.ecdemo1.integrations.demo;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import io.mateu.ecdemo1.integrations.audit.Audited;
import io.mateu.ecdemo1.integrations.config.IntegrationsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The simulated Opera outage, a switch inside the connector (pms-integration-service, /demo/opera):
 * while it is on, every call to OHIP fails as a timeout would, and lifts by itself at its auto-off.
 * The connector's retry alert threshold too — changed at runtime, no restart. Over HTTP: the Demo page
 * shows the answer right away (a screen waiting on it), and the banner asks it every few seconds.
 * Every change is audited, by who.
 */
@Slf4j
@Component
public class OperaOutage {

    public record Outage(boolean active, Instant since, Instant until, String by) {
    }

    /** What the connector says: the outage, the retry alert threshold and the Opera context it writes under. */
    public record Status(Outage outage, Duration alertAfter, Duration defaultAlertAfter, String operaContext,
                         String operaCustomReference) {

        public boolean active() {
            return outage != null && outage.active() && (outage.until() == null || Instant.now().isBefore(outage.until()));
        }
    }

    final RestClient pms;

    public OperaOutage(IntegrationsProperties properties) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.pms = RestClient.builder().baseUrl(properties.pmsIntegrationUrl()).requestFactory(factory).build();
    }

    /** Empty when the connector does not answer. */
    public Optional<Status> status() {
        try {
            return Optional.ofNullable(pms.get().uri("/demo/opera").retrieve().body(Status.class));
        } catch (RuntimeException e) {
            log.debug("The connector did not say its demo status: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Audited("Demo: encender la caída de Opera simulada")
    public Status on(int autoOffMinutes, Integer alertAfterMinutes, String by) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("active", true);
        body.put("autoOffMinutes", autoOffMinutes);
        if (alertAfterMinutes != null) {
            body.put("alertAfterMinutes", alertAfterMinutes);
        }
        body.put("by", by);
        return pms.post().uri("/demo/opera/outage").body(body).retrieve().body(Status.class);
    }

    @Audited("Demo: apagar la caída de Opera simulada")
    public Status off(boolean restoreAlert, String by) {
        return pms.post().uri("/demo/opera/outage").body(Map.of("active", false, "restoreAlert", restoreAlert, "by", by))
                .retrieve().body(Status.class);
    }

    @Audited("Demo: cambiar el umbral de aviso de reintentos")
    public Status alertAfter(int minutes, String by) {
        return pms.put().uri("/demo/opera/alert-after").body(Map.of("minutes", minutes, "by", by))
                .retrieve().body(Status.class);
    }
}
