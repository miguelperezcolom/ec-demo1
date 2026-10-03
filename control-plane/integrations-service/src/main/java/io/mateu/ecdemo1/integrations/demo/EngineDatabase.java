package io.mateu.ecdemo1.integrations.demo;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The engine's database (workflow), on the same server as this service's, reached apart from this
 * service's own: two connections at most, and not a {@code DataSource} bean — JPA's stays the only one.
 * The engine has no API to read a process's steps nor to delete processes (EventConductor 2.23): the
 * Demo page reads its reset-demo runs here, and purge-engine empties it here.
 */
@Component
public class EngineDatabase {

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    @org.springframework.beans.factory.annotation.Autowired
    public EngineDatabase(DemoProperties properties) {
        if (properties.engineDbUrl() == null || properties.engineDbUrl().isBlank()) {
            dataSource = null;
            jdbc = null;
            transaction = null;
            return;
        }
        dataSource = new HikariDataSource();
        dataSource.setPoolName("engine");
        dataSource.setJdbcUrl(properties.engineDbUrl());
        dataSource.setUsername(properties.engineDbUsername());
        dataSource.setPassword(properties.engineDbPassword());
        dataSource.setMaximumPoolSize(2);
        dataSource.setMinimumIdle(0);
        dataSource.setInitializationFailTimeout(-1); // the service starts without the engine's database
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    /** For tests: an engine database already at hand. */
    EngineDatabase(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.dataSource = null;
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public Optional<JdbcTemplate> jdbc() {
        return Optional.ofNullable(jdbc);
    }

    public JdbcTemplate required() {
        if (jdbc == null) {
            throw new IllegalStateException("No engine database configured (integrations.demo.engine-db-url)");
        }
        return jdbc;
    }

    public TransactionTemplate transaction() {
        required();
        return transaction;
    }

    @PreDestroy
    void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
