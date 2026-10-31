package io.mateu.ecdemo1.gateway.infra.enrichment;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.mateu.ecdemo1.grpc.AuthServiceGrpc;
import io.mateu.ecdemo1.grpc.GetAuthInfoReply;
import io.mateu.ecdemo1.grpc.GetAuthInfoRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import reactor.core.publisher.Mono;

/**
 * {@link AuthInfoLookup} over the users service's gRPC {@code AuthService.GetAuthInfo}.
 *
 * <p>The async stub rather than the blocking one the demo's api-gw used: this gateway is reactive,
 * and a blocking call would need a hop to another scheduler to keep it off the event loop. The
 * async stub completes on gRPC's own executor and the reply is handed straight to the Mono.
 *
 * <p>Every call has a deadline. Without one a users pod that accepts the connection and then hangs
 * would hold every authenticated request through this gateway for as long as it hung — the
 * enrichment is best-effort, so it must never be what makes a page slow.
 *
 * <p>{@code NOT_FOUND} is the service saying "no such user", which is a real answer — a Keycloak
 * account the users service has no record of holds nothing there — so it maps to
 * {@link AuthInfo#NONE} instead of an error, and is cached like any other answer.
 */
public class GrpcAuthInfoLookup implements AuthInfoLookup {

    private final AuthServiceGrpc.AuthServiceStub stub;
    private final Duration deadline;

    public GrpcAuthInfoLookup(AuthServiceGrpc.AuthServiceStub stub, Duration deadline) {
        this.stub = stub;
        this.deadline = deadline;
    }

    @Override
    public Mono<AuthInfo> forUser(String user) {
        return Mono.create(sink -> stub
                .withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                .getAuthInfo(GetAuthInfoRequest.newBuilder().setUser(user).build(),
                        new StreamObserver<>() {
                            @Override
                            public void onNext(GetAuthInfoReply reply) {
                                sink.success(new AuthInfo(reply.getRoles(), reply.getScopes()));
                            }

                            @Override
                            public void onError(Throwable error) {
                                if (error instanceof StatusRuntimeException status
                                        && status.getStatus().getCode() == Status.Code.NOT_FOUND) {
                                    sink.success(AuthInfo.NONE);
                                } else {
                                    sink.error(error);
                                }
                            }

                            @Override
                            public void onCompleted() {
                                // A unary call's reply has already completed the sink in onNext.
                                sink.success();
                            }
                        }));
    }
}
