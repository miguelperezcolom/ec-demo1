package io.mateu.ecdemo1.customerhistory;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;

import java.util.Map;

/**
 * One Postgres and one broker for every Spring test here, started once and shared (not {@code @Container},
 * which would stop them after the first class while the cached context still points at them): the
 * test classes then share one application context too. Redpanda's data on tmpfs — on a nearly full
 * disk it refuses to start the second time.
 */
public abstract class Containers {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final RedpandaContainer redpanda = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7")
            .withTmpFs(Map.of("/var/lib/redpanda/data", "rw,size=8g"));

    static {
        postgres.start();
        redpanda.start();
    }

    @DynamicPropertySource
    static void kafka(DynamicPropertyRegistry registry) {
        registry.add("KAFKA_BROKERS", redpanda::getBootstrapServers);
    }
}
