package ecdemo1.messaging.autoconfigure;

import io.mateu.ecdemo1.messaging.Inbox;
import io.mateu.ecdemo1.messaging.MessagingProperties;
import io.mateu.ecdemo1.messaging.MessagingSchema;
import io.mateu.ecdemo1.messaging.MicrometerTraceContexts;
import io.mateu.ecdemo1.messaging.Outbox;
import io.mateu.ecdemo1.messaging.OutboxRelay;
import io.mateu.ecdemo1.messaging.OutboxTransport;
import io.mateu.ecdemo1.messaging.RelayScheduler;
import io.mateu.ecdemo1.messaging.TraceContexts;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.messaging.engine.EngineOutbox;
import io.mateu.ecdemo1.messaging.transport.StreamBridgeTransport;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.function.StreamOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * The outbox, its relay and the inbox, on the service's DataSource and transaction manager. A service
 * that has the jar has them; {@code messaging.*} ({@link MessagingProperties}) names its tables and
 * tunes the relay. The relay's transport is Spring Cloud Stream's where the service has it, or the
 * service's own {@link OutboxTransport} bean.
 *
 * <p>Outside {@code io.mateu} on purpose: the configuration Mateu's annotation processor generates in
 * every service with a UI component-scans all of {@code io.mateu}, with none of Boot's filters — an
 * auto-configuration there would be taken as one of the service's own configurations, and its
 * conditions evaluated before the service's beans are known.
 */
@AutoConfiguration(afterName = {
        // Boot 3
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
        // Boot 4
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"})
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnProperty(prefix = "messaging", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    static Clock clock(ObjectProvider<Clock> clock) {
        return clock.getIfAvailable(Clock::systemUTC);
    }

    static TransactionTemplate transactions(ObjectProvider<PlatformTransactionManager> transactionManager) {
        return new TransactionTemplate(transactionManager.getObject());
    }

    @Bean("messagingSchema")
    @ConditionalOnMissingBean
    public MessagingSchema messagingSchema(DataSource dataSource, MessagingProperties properties) {
        var schema = new MessagingSchema(new JdbcTemplate(dataSource), properties);
        if (properties.createSchema()) {
            schema.create();
        }
        return schema;
    }

    @Bean("messagingOutbox")
    @ConditionalOnMissingBean
    public Outbox outbox(DataSource dataSource, MessagingProperties properties, MessagingSchema schema,
                         TraceContexts traces, ObjectProvider<Clock> clock) {
        return new Outbox(new JdbcTemplate(dataSource), properties, traces, clock(clock));
    }

    @Bean("messagingInbox")
    @ConditionalOnMissingBean
    public Inbox inbox(DataSource dataSource, MessagingProperties properties, MessagingSchema schema,
                       ObjectProvider<PlatformTransactionManager> transactionManager, ObjectProvider<Clock> clock) {
        return new Inbox(new JdbcTemplate(dataSource), transactions(transactionManager), properties, clock(clock),
                schema.postgres());
    }

    @Bean("messagingRelayScheduler")
    @ConditionalOnMissingBean
    public RelayScheduler outboxRelayScheduler(DataSource dataSource, MessagingProperties properties,
                                               MessagingSchema schema,
                                               ObjectProvider<PlatformTransactionManager> transactionManager,
                                               ObjectProvider<OutboxTransport> transport, TraceContexts traces,
                                               ObjectProvider<Clock> clock) {
        return new RelayScheduler(() -> new OutboxRelay(new JdbcTemplate(dataSource), transactions(transactionManager),
                properties, transport.getObject(), traces, clock(clock), schema.postgres()), transport, properties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    static class Traced {
        @Bean("messagingTraceContexts")
        @ConditionalOnMissingBean
        TraceContexts traceContexts(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
            return new MicrometerTraceContexts(tracer, propagator);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("io.micrometer.tracing.Tracer")
    static class Untraced {
        @Bean("messagingTraceContexts")
        @ConditionalOnMissingBean
        TraceContexts traceContexts() {
            return TraceContexts.NONE;
        }
    }

    /** Spring Cloud Stream's StreamBridge, where the service has it and no transport of its own. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.cloud.stream.function.StreamOperations")
    static class StreamBridged {
        @Bean("messagingStreamBridgeTransport")
        @ConditionalOnMissingBean(OutboxTransport.class)
        OutboxTransport streamBridgeTransport(ObjectProvider<StreamOperations> streams) {
            return new StreamBridgeTransport(streams);
        }
    }

    /** The engine's requests, where the service has the engine's DTOs. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"io.mateu.workflow.dtos.events.integration.ProcessCreationRequested",
            "com.fasterxml.jackson.databind.ObjectMapper"})
    static class Engine {
        @Bean("messagingEngineOutbox")
        @ConditionalOnMissingBean
        EngineOutbox engineOutbox(Outbox outbox, TraceContexts traces, ObjectProvider<ObjectMapper> objectMapper,
                                  @Value("${messaging.engine.destination:outboxUpstream}") String destination) {
            return new EngineOutbox(outbox, traces, objectMapper, destination);
        }
    }
}
