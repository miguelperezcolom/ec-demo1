package io.mateu.ecdemo1.gateway.infra.enrichment;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.mateu.ecdemo1.grpc.AuthServiceGrpc;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The wiring behind {@link UserAuthorityEnrichmentFilter}: one gRPC channel to the users service,
 * the lookup over it, and the per-token cache.
 *
 * <p>The address takes the {@code static://host:port} form the demo's api-gw was configured with
 * (net.devh's syntax) as well as any plain gRPC target ({@code users:9191},
 * {@code dns:///users:9191}), so a value copied from either side works. Plaintext because the port
 * is on the users Service and on no ingress — the hop never leaves the cluster network, like every
 * other service-to-service call here.
 */
@Configuration
public class EnrichmentConfig {

    static final String STATIC_PREFIX = "static://";

    @Bean(destroyMethod = "shutdownNow")
    public ManagedChannel authServiceChannel(@Value("${AUTH_GRPC_ADDRESS:static://users:9191}") String address) {
        // The channel connects lazily, on the first call: building it never waits for, or fails
        // on, the users pod, so this gateway's readiness does not depend on that service's.
        return ManagedChannelBuilder.forTarget(target(address)).usePlaintext().build();
    }

    @Bean
    public AuthInfoLookup authInfoLookup(ManagedChannel authServiceChannel,
                                         @Value("${AUTH_GRPC_DEADLINE:1s}") Duration deadline) {
        return new GrpcAuthInfoLookup(AuthServiceGrpc.newStub(authServiceChannel), deadline);
    }

    /**
     * Keyed by the raw token, like the demo's api-gw: a token is one user's, for its lifetime, so
     * the same token always asks the same question. Five minutes is how stale a role change may be
     * before it shows here — the same window the original accepted — and the cap is a memory bound,
     * not a tuning knob.
     *
     * <p>An async cache so concurrent requests carrying one new token — a console's first load
     * fires several at once — share a single gRPC call instead of racing to make one each. A
     * failed lookup is not kept: Caffeine drops a future that completes exceptionally, so the next
     * request asks again.
     */
    @Bean
    public AsyncCache<String, AuthInfo> authInfoCache(@Value("${AUTH_CACHE_TTL:5m}") Duration ttl) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(10_000)
                .buildAsync();
    }

    static String target(String address) {
        return address.startsWith(STATIC_PREFIX) ? address.substring(STATIC_PREFIX.length()) : address;
    }
}
