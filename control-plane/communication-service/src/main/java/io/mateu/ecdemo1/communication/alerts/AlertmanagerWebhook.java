package io.mateu.ecdemo1.communication.alerts;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the cluster's Alertmanager sends the platform's alerts (deploy/observability/
 * kube-prometheus-stack.yaml). Cluster-internal: the gateway routes only /_inbox and /_communication
 * here, so /alerts is not reachable from outside — Alertmanager calls the service directly.
 */
@RestController
@RequiredArgsConstructor
public class AlertmanagerWebhook {

    final PlatformAlerts alerts;

    @PostMapping("/alerts/alertmanager")
    public PlatformAlerts.Outcome receive(@RequestBody PlatformAlerts.Webhook webhook) {
        return alerts.received(webhook);
    }
}
