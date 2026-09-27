package io.mateu.ecdemo1.apimcp.catalogue;

import io.mateu.ecdemo1.apimcp.server.ApiMcpServers;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Ready means the catalogue has been read at least once.
 *
 * <p>Not "the control plane is up right now": once the catalogue has been read, this pod serves
 * every mounted endpoint from what it holds, and taking it out of the Service because the control
 * plane is restarting would break tool calls that would otherwise have worked. Before the first
 * read there is genuinely nothing to serve, and a pod saying so is better than one answering 404
 * for every endpoint an operator expects.
 *
 * <p>Deliberately the same arrangement as the agent's config health indicator, and stated in the
 * same terms: DOWN until the first successful read, UP but visibly stale afterwards.
 */
@Component
@RequiredArgsConstructor
public class CatalogueHealthIndicator implements HealthIndicator {

    private final CatalogueClient catalogue;
    private final ApiMcpServers servers;

    @Override
    public Health health() {
        var builder = catalogue.hasEverRead() ? Health.up() : Health.down();
        return builder
                .withDetail("lastReadAt", String.valueOf(catalogue.lastGoodAt()))
                .withDetail("endpoints", servers.endpointCount())
                .build();
    }
}
