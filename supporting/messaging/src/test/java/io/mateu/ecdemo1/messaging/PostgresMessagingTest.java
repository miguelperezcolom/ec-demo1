package io.mateu.ecdemo1.messaging;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The contract on PostgreSQL, the database the services run on: {@code skip locked} and
 * {@code on conflict do nothing} are PostgreSQL's. The database on tmpfs — no disk wanted — and the
 * whole class skipped where there is no Docker.
 */
class PostgresMessagingTest extends MessagingContract {

    static PostgreSQLContainer<?> postgres;
    static DataSource dataSource;

    @BeforeAll
    static void start() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "No Docker: the PostgreSQL contract is skipped");
        postgres = new PostgreSQLContainer<>("postgres:16-alpine").withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));
        postgres.start();
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    @AfterAll
    static void stop() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Override
    DataSource dataSource() {
        return dataSource;
    }

    @Test
    void twoRelaysAtOnceNeverSendAMessageTwice() throws Exception {
        var f = fixture(0, false);
        for (int i = 0; i < 300; i++) {
            f.append("K" + (i % 7), "m" + i);
        }
        var sent = Collections.synchronizedList(new ArrayList<String>());
        OutboxTransport recording = (message, headers) -> sent.add(message.payload());
        var a = new OutboxRelay(f.jdbc, f.transactions, f.properties, recording, TraceContexts.NONE, f.clock, true);
        var b = new OutboxRelay(f.jdbc, f.transactions, f.properties, recording, TraceContexts.NONE, f.clock, true);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        for (var relay : List.of(a, b)) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < 10; i++) {
                    relay.relayPending();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(sent).hasSize(300).doesNotHaveDuplicates();
    }
}
