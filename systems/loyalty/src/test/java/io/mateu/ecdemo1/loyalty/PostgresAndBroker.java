package io.mateu.ecdemo1.loyalty;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.redpanda.RedpandaContainer;

import java.util.Map;

/**
 * The application whole, against a real Postgres and a real broker, started once for every test class
 * that extends this (singleton containers, one cached Spring context): the Kafka bindings, the Mateu UI
 * and the MCP server all come up as they do in the cluster.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PostgresAndBroker {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    // On tmpfs: Redpanda refuses to work on a nearly full disk, and its data is thrown away anyway.
    static final RedpandaContainer redpanda = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7")
            .withTmpFs(Map.of("/var/lib/redpanda/data", "rw,size=8g"));

    static {
        postgres.start();
        redpanda.start();
    }

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("KAFKA_BROKERS", redpanda::getBootstrapServers);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.update("delete from accrual");
        jdbc.update("delete from member");
        jdbc.update("delete from inbox_entry");
    }
}
