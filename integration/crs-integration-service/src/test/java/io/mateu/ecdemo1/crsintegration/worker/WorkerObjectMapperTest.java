package io.mateu.ecdemo1.crsintegration.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.ecdemo1.crsintegration.worker.runtime.WorkerRuntime;
import io.mateu.workflow.worker.api.WorkerApiAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * worker-api's fallback ObjectMapper is evaluated before Boot's Jackson auto-configuration, which
 * then backs off: the app's only mapper was a bare one, and a LocalDate did not read (ec1, the
 * booking's own read back from the CRS). With the post-processor it is the mapper Boot would build.
 */
class WorkerObjectMapperTest {

    @Configuration
    static class PostProcessor {
        @Bean
        static org.springframework.beans.factory.config.BeanFactoryPostProcessor fallback() {
            return WorkerRuntime.workerApiObjectMapperOnlyAsFallback();
        }
    }

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WorkerApiAutoConfiguration.class, JacksonAutoConfiguration.class));

    @Test
    void withoutItTheOnlyMapperIsABareOne() {
        runner.run(context -> assertThat(context.getBeanNamesForType(ObjectMapper.class))
                .containsExactly("workerApiObjectMapper"));
    }

    @Test
    void theOnlyMapperIsTheOneBootWouldHaveBuilt() {
        runner.withUserConfiguration(PostProcessor.class).run(context -> {
            var mapper = context.getBean(ObjectMapper.class);
            assertThat(mapper.writeValueAsString(LocalDate.of(2026, 11, 12))).isEqualTo("\"2026-11-12\"");
            assertThat(mapper.readValue("\"2026-11-12\"", LocalDate.class)).isEqualTo(LocalDate.of(2026, 11, 12));
        });
    }

    @Test
    void besideAnotherMapperItIsNoCandidate() {
        runner.withUserConfiguration(PostProcessor.class)
                .withBean("objectMapper", ObjectMapper.class, ObjectMapper::new)
                .run(context -> assertThat(context.getBean(ObjectMapper.class))
                        .isSameAs(context.getBean("objectMapper")));
    }
}
