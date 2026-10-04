package ecdemo1.demoreset.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.mateu.ecdemo1.demoreset.ConsumerPause;
import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DemoResetAutoConfigurationTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:1/none")
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
                    DataSourceTransactionManagerAutoConfiguration.class, DemoResetAutoConfiguration.class));

    @Test
    void aPlanGetsItsResetWithEveryConsumerPause() {
        runner.withBean(DemoResetPlan.class, () -> DemoResetPlan.truncate("x", "a"))
                .withBean("pause", ConsumerPause.class, () -> new ConsumerPause() {
                    public void pause() { }
                    public void resume() { }
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(DemoReset.class);
                    assertThat(context.getBean(DemoReset.class).plan().service()).isEqualTo("x");
                });
    }

    @Test
    void noPlanNoReset() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DemoReset.class));
    }

    @Test
    void isNotInAPackageMateuScans() {
        assertThat(DemoResetAutoConfiguration.class.getPackageName()).doesNotStartWith("io.mateu");
    }
}
