package io.mateu.ecdemo1.communication.alerts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.mateu.ecdemo1.communication.inbox.Inbox;
import io.mateu.ecdemo1.communication.send.Deliveries;
import io.mateu.ecdemo1.integration.model.notification.NotificationRequested;
import io.mateu.ecdemo1.integration.model.notification.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alertmanager's webhook, as it posts it: a firing alert is a notification of the platform's type for
 * its severity, a resolved one closes it, the same episode sent again is the same notification, and a
 * status that is neither is left alone.
 */
class PlatformAlertsTest {

    static final Instant NOW = Instant.parse("2026-09-29T06:00:00Z");

    final List<NotificationRequested> accepted = new ArrayList<>();
    final List<String> resolved = new ArrayList<>();

    final Deliveries deliveries = new Deliveries(null, null, null, null, null, null) {
        @Override
        public void accept(NotificationRequested request) {
            accepted.add(request);
        }
    };

    final Inbox inbox = new Inbox(null, null, null, null, null, null) {
        @Override
        public int resolve(String subject, String by, Instant at) {
            resolved.add(subject + " by " + by + " at " + at);
            return 1;
        }
    };

    final PlatformAlerts alerts = new PlatformAlerts(deliveries, inbox, Clock.fixed(NOW, ZoneOffset.UTC));

    static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    /** What Alertmanager posted for the OHIP outage of 2026-09-29, trimmed to one alert, extra fields and all. */
    static final String FIRING = """
            {"receiver":"communication","status":"firing","version":"4","groupKey":"{}/{notify=\\"ec-demo1\\"}:{alertname=\\"OperaNotAnswering\\"}",
             "truncatedAlerts":0,"groupLabels":{"alertname":"OperaNotAnswering"},"commonLabels":{},"commonAnnotations":{},
             "externalURL":"http://kps-kube-prometheus-stack-alertmanager.observability:9093",
             "alerts":[{"status":"firing",
               "labels":{"alertname":"OperaNotAnswering","service":"pms-integration","severity":"critical","notify":"ec-demo1"},
               "annotations":{"summary":"Opera no responde a pms-integration","description":"Every call failed.",
                              "link":"https://console.ec1.mateu.io/_api-usage/apis"},
               "startsAt":"2026-09-29T01:55:00Z","endsAt":"0001-01-01T00:00:00Z",
               "generatorURL":"http://prometheus/graph?g0.expr=...","fingerprint":"8f1d3c2a9b7e6f10"}]}
            """;

    static final String RESOLVED = FIRING.replace("\"status\":\"firing\"", "\"status\":\"resolved\"")
            .replace("0001-01-01T00:00:00Z", "2026-09-29T09:40:00Z");

    PlatformAlerts.Webhook read(String json) throws Exception {
        return JSON.readValue(json, PlatformAlerts.Webhook.class);
    }

    @Test
    void aFiringCriticalAlertIsACriticalPlatformNotification() throws Exception {
        var outcome = alerts.received(read(FIRING));

        assertThat(outcome).isEqualTo(new PlatformAlerts.Outcome(1, 0, 0));
        var n = accepted.getFirst();
        assertThat(n.type()).isEqualTo(NotificationType.PLATFORM_ALERT_CRITICAL);
        assertThat(n.subject()).isEqualTo("alert/8f1d3c2a9b7e6f10");
        assertThat(n.title()).isEqualTo("🔴 Opera no responde a pms-integration");
        assertThat(n.body()).isEqualTo("Every call failed.");
        assertThat(n.link()).isEqualTo("https://console.ec1.mateu.io/_api-usage/apis");
        assertThat(n.requestedAt()).isEqualTo(Instant.parse("2026-09-29T01:55:00Z"));
    }

    @Test
    void aWarningIsAPlatformAlertAndFallsBackToTheAlertNameAndPrometheusLink() throws Exception {
        var json = FIRING.replace("\"severity\":\"critical\"", "\"severity\":\"warning\"")
                .replace("\"summary\":\"Opera no responde a pms-integration\",", "")
                .replace("\"link\":\"https://console.ec1.mateu.io/_api-usage/apis\"", "\"runbook\":\"none\"");
        alerts.received(read(json));

        var n = accepted.getFirst();
        assertThat(n.type()).isEqualTo(NotificationType.PLATFORM_ALERT);
        assertThat(n.title()).isEqualTo("🟠 OperaNotAnswering");
        assertThat(n.link()).startsWith("http://prometheus/graph");
    }

    @Test
    void theSameEpisodeSentAgainIsTheSameNotificationAndANewOneIsAnother() throws Exception {
        alerts.received(read(FIRING));
        alerts.received(read(FIRING));
        alerts.received(read(FIRING.replace("2026-09-29T01:55:00Z", "2026-09-29T12:00:00Z")));

        assertThat(accepted).hasSize(3);
        // Deliveries keeps one notification per dedup key: the repeat has the first one's.
        assertThat(accepted.get(1).dedupKey()).isEqualTo(accepted.get(0).dedupKey());
        assertThat(accepted.get(1).notificationId()).isEqualTo(accepted.get(0).notificationId());
        assertThat(accepted.get(2).dedupKey()).isNotEqualTo(accepted.get(0).dedupKey());
        // …and all of them are the same alert, which one resolution closes.
        assertThat(accepted).extracting(NotificationRequested::subject).containsOnly("alert/8f1d3c2a9b7e6f10");
    }

    @Test
    void aResolvedAlertClosesItsNotificationWhenItEnded() throws Exception {
        var outcome = alerts.received(read(RESOLVED));

        assertThat(outcome).isEqualTo(new PlatformAlerts.Outcome(0, 1, 0));
        assertThat(accepted).isEmpty();
        assertThat(resolved).containsExactly("alert/8f1d3c2a9b7e6f10 by alertmanager at 2026-09-29T09:40:00Z");
    }

    @Test
    void anUnknownStatusIsIgnoredAndAnEmptyCallDoesNothing() throws Exception {
        var outcome = alerts.received(read(FIRING.replace("\"status\":\"firing\"", "\"status\":\"suppressed\"")));

        assertThat(outcome).isEqualTo(new PlatformAlerts.Outcome(0, 0, 1));
        assertThat(alerts.received(null)).isEqualTo(new PlatformAlerts.Outcome(0, 0, 0));
        assertThat(accepted).isEmpty();
        assertThat(resolved).isEmpty();
    }

    @Test
    void withoutAFingerprintTheAlertIsItsLabels() {
        var a = new PlatformAlerts.Alert("firing", java.util.Map.of("alertname", "X", "pod", "p"), null, NOW, null, null, null);
        var b = new PlatformAlerts.Alert("resolved", java.util.Map.of("pod", "p", "alertname", "X"), null, NOW, null, null, "");
        assertThat(PlatformAlerts.subject(a)).isEqualTo(PlatformAlerts.subject(b)).startsWith("alert/");
    }
}
