package ecdemo1.demoreset.autoconfigure;

import java.util.Set;

import io.mateu.ecdemo1.demoreset.ConsumerPause;
import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import io.mateu.ecdemo1.demoreset.StreamBindingsPause;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.stream.binding.BindingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outside {@code io.mateu}, like messaging's: the apps scan {@code io.mateu.*} (Mateu), and a scanned
 * auto-configuration is read as a plain configuration, before the service's — its
 * {@code @ConditionalOnBean(DemoResetPlan)} would see no plan.
 *
 * <p>The service's {@link DemoReset}, once it says what it empties (a {@link DemoResetPlan} bean), and
 * its Spring Cloud Stream consumers paused around it when it has any.
 *
 * <ul>
 *   <li>{@code demo-reset.settle} (2s): what a paused consumer had in hand, given time to finish;</li>
 *   <li>{@code demo-reset.kept-bindings}: input bindings never paused — the engine's, by default.</li>
 * </ul>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.cloud.stream.config.BindingServiceConfiguration"})
public class DemoResetAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(BindingService.class)
    static class StreamBindings {

        @Bean
        @ConditionalOnBean({DemoResetPlan.class, BindingService.class})
        @ConditionalOnMissingBean(StreamBindingsPause.class)
        StreamBindingsPause streamBindingsPause(BindingService bindings,
                                                @Value("${demo-reset.kept-bindings:consumeWorkerEvent-in-0}") String kept,
                                                @Value("${demo-reset.pause-wait:5s}") String wait) {
            return new StreamBindingsPause(bindings, Set.of(kept.trim().split("\\s*,\\s*")), DurationStyle.detectAndParse(wait));
        }
    }

    @Bean
    @ConditionalOnBean(DemoResetPlan.class)
    @ConditionalOnMissingBean(DemoReset.class)
    DemoReset demoReset(DemoResetPlan plan, JdbcTemplate jdbc, PlatformTransactionManager transactions,
                        ObjectProvider<ConsumerPause> consumers,
                        @Value("${demo-reset.settle:2s}") String settle) {
        return new DemoReset(plan, jdbc, new TransactionTemplate(transactions), consumers.orderedStream().toList(),
                DurationStyle.detectAndParse(settle));
    }
}
