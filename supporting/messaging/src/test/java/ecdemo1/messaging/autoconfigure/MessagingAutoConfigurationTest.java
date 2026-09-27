package ecdemo1.messaging.autoconfigure;

import io.mateu.ecdemo1.messaging.Inbox;
import io.mateu.ecdemo1.messaging.MessagingProperties;
import io.mateu.ecdemo1.messaging.MicrometerTraceContexts;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.messaging.OutboxTransport;
import io.mateu.ecdemo1.messaging.RelayScheduler;
import io.mateu.ecdemo1.messaging.TraceContexts;
import io.mateu.ecdemo1.messaging.engine.EngineOutbox;
import io.mateu.ecdemo1.messaging.transport.StreamBridgeTransport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingAutoConfigurationTest {

    static final DataSource H2 = new DriverManagerDataSource("jdbc:h2:mem:autoconfig;DB_CLOSE_DELAY=-1", "sa", "");

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessagingAutoConfiguration.class))
            .withBean(DataSource.class, () -> H2)
            .withBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(H2));

    @Test
    void aServiceWithADatabaseGetsTheOutboxTheRelayAndTheInboxOnItsOwnTables() {
        runner.withPropertyValues("messaging.outbox.table=orders_outbox", "messaging.inbox.table=orders_inbox",
                        "messaging.outbox.interval=2s", "messaging.outbox.max-attempts=10")
                .run(context -> {
                    assertThat(context).hasSingleBean(Outbox.class).hasSingleBean(Inbox.class)
                            .hasSingleBean(RelayScheduler.class).hasSingleBean(TraceContexts.class)
                            .hasSingleBean(EngineOutbox.class);
                    assertThat(context.getBean(TraceContexts.class)).isInstanceOf(MicrometerTraceContexts.class);
                    // Spring Cloud Stream is on this classpath: its StreamBridge is the transport.
                    assertThat(context.getBean(OutboxTransport.class)).isInstanceOf(StreamBridgeTransport.class);
                    var properties = context.getBean(MessagingProperties.class);
                    assertThat(properties.outbox().interval().toSeconds()).isEqualTo(2);
                    assertThat(properties.outbox().maxAttempts()).isEqualTo(10);
                    var jdbc = new JdbcTemplate(H2);
                    assertThat(jdbc.queryForObject("select count(*) from orders_outbox", Integer.class)).isZero();
                    assertThat(jdbc.queryForObject("select count(*) from orders_inbox", Integer.class)).isZero();
                });
    }

    @Test
    void aTransportOfTheServicesOwnReplacesStreamBridge() {
        OutboxTransport own = (message, headers) -> { };
        runner.withBean(OutboxTransport.class, () -> own)
                .run(context -> assertThat(context.getBean(OutboxTransport.class)).isSameAs(own));
    }

    @Test
    void itCanBeTurnedOff() {
        runner.withPropertyValues("messaging.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(Outbox.class));
    }
}
