package io.mateu.ecdemo1.gateway.infra.enrichment;

import reactor.core.publisher.Mono;

/**
 * Asks the users service what a user holds. An interface only so the filter can be tested without
 * a gRPC server; {@link GrpcAuthInfoLookup} is the one implementation.
 *
 * <p>Contract: a user the service does not know completes with {@link AuthInfo#NONE}; anything
 * else that goes wrong (the service down, a timeout) errors, and the caller decides what an
 * unanswered question means.
 */
public interface AuthInfoLookup {

    Mono<AuthInfo> forUser(String user);
}
