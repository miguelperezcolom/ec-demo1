package io.mateu.ecdemo1.gateway.infra.enrichment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.mateu.ecdemo1.grpc.AuthServiceGrpc;
import io.mateu.ecdemo1.grpc.GetAuthInfoReply;
import io.mateu.ecdemo1.grpc.GetAuthInfoRequest;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Against a real gRPC server, in-process, answering the way users' AuthService does. */
class GrpcAuthInfoLookupTest {

    private Server server;
    private ManagedChannel channel;
    private GrpcAuthInfoLookup lookup;

    @BeforeEach
    void start() throws Exception {
        var name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new AuthServiceGrpc.AuthServiceImplBase() {
                    @Override
                    public void getAuthInfo(GetAuthInfoRequest request, StreamObserver<GetAuthInfoReply> reply) {
                        switch (request.getUser()) {
                            case "demo" -> {
                                reply.onNext(GetAuthInfoReply.newBuilder()
                                        .setRoles("admin editor").setScopes("users:read content:write").build());
                                reply.onCompleted();
                            }
                            case "slow" -> { /* never answers: the deadline has to end it */ }
                            case "broken" -> reply.onError(Status.INTERNAL.asRuntimeException());
                            default -> reply.onError(Status.NOT_FOUND
                                    .withDescription("User with ID " + request.getUser() + " not found")
                                    .asRuntimeException());
                        }
                    }
                })
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        lookup = new GrpcAuthInfoLookup(AuthServiceGrpc.newStub(channel), Duration.ofMillis(200));
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void returnsTheUsersRolesAndScopes() {
        assertThat(lookup.forUser("demo").block())
                .isEqualTo(new AuthInfo("admin editor", "users:read content:write"));
    }

    @Test
    void anUnknownUserHoldsNothing() {
        assertThat(lookup.forUser("stranger").block()).isEqualTo(AuthInfo.NONE);
    }

    @Test
    void otherFailuresAreErrors() {
        assertThatThrownBy(() -> lookup.forUser("broken").block())
                .isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void aHungServiceIsCutOffByTheDeadline() {
        assertThatThrownBy(() -> lookup.forUser("slow").block(Duration.ofSeconds(5)))
                .isInstanceOfSatisfying(StatusRuntimeException.class, error ->
                        assertThat(error.getStatus().getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));
    }
}
