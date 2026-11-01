package io.mateu.ecdemo1.users.infra.out.outbox;

import io.mateu.ecdemo1.messaging.OutboxMessage;
import io.mateu.ecdemo1.messaging.OutboxTransport;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingOutboxTransportTest {

    final List<OutboxMessage> toIdentity = new ArrayList<>();
    final List<OutboxMessage> toDirectory = new ArrayList<>();
    final OutboxTransport identity = (m, h) -> toIdentity.add(m);
    final OutboxTransport directory = (m, h) -> toDirectory.add(m);
    final RoutingOutboxTransport router = new RoutingOutboxTransport(identity, directory);

    static OutboxMessage message(String destination, String key) {
        return new OutboxMessage(1, destination, key, "T", "{}", Map.of(), null, null, 0, Instant.now(), null);
    }

    @Test
    void identityGoesToKeycloakUnchanged() throws Exception {
        router.send(message("identity", "ana"), Map.of());

        assertThat(toIdentity).singleElement().satisfies(m -> assertThat(m.key()).isEqualTo("ana"));
        assertThat(toDirectory).isEmpty();
    }

    @Test
    void directoryGoesToKafkaKeyedByTheBareUserId() throws Exception {
        router.send(message("employee-directory", "employee-directory/ana"), Map.of());

        assertThat(toDirectory).singleElement().satisfies(m -> {
            assertThat(m.key()).isEqualTo("ana");
            assertThat(m.destination()).isEqualTo("employee-directory");
            assertThat(m.payload()).isEqualTo("{}");
        });
        assertThat(toIdentity).isEmpty();
    }

    @Test
    void anUnknownDestinationIsRefused() {
        assertThatThrownBy(() -> router.send(message("elsewhere", "ana"), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("elsewhere");
    }
}
