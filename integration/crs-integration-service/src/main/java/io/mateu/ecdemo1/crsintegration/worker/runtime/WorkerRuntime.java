package io.mateu.ecdemo1.crsintegration.worker.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.workflow.worker.api.Cancellations;
import io.mateu.workflow.worker.api.TaskDispatcher;
import io.mateu.workflow.worker.api.TaskRegistry;
import io.mateu.workflow.worker.api.TaskReplySink;
import io.mateu.workflow.worker.api.TaskTracing;
import io.mateu.workflow.worker.api.WorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * The engine's worker runtime as this service uses it. worker-kafka binds {@code consumeWorkerEvent}
 * to the task topic and brings the reply sink, the cancellations and the tracing; this gives it its
 * {@link TaskDispatcher}, the runtime's own, bound through {@link ExactStrings}. Every step the engine
 * dispatches here names its contract ({@code taskId}), so the handler is found by it.
 */
@Configuration
@Slf4j
public class WorkerRuntime {

    /**
     * worker-api registers an ObjectMapper of its own — a bare {@code new ObjectMapper()} — when it
     * finds none, and it is evaluated before Boot's JacksonAutoConfiguration: so either Boot's backs
     * off and the whole app gets a mapper with no JavaTimeModule (a LocalDate no longer reads), or a
     * library defines its own later and there are two. Here it is only a fallback: with another
     * mapper it stops being a candidate; alone, it becomes the one Boot would have built.
     */
    @Bean
    public static BeanFactoryPostProcessor workerApiObjectMapperOnlyAsFallback() {
        return beanFactory -> {
            if (!beanFactory.containsBeanDefinition("workerApiObjectMapper")) {
                return;
            }
            if (beanFactory.getBeanNamesForType(ObjectMapper.class, true, false).length > 1) {
                beanFactory.getBeanDefinition("workerApiObjectMapper").setAutowireCandidate(false);
            } else if (beanFactory instanceof BeanDefinitionRegistry registry) {
                var asBoot = new RootBeanDefinition(ObjectMapper.class,
                        () -> beanFactory.getBean(Jackson2ObjectMapperBuilder.class).createXmlMapper(false).build());
                asBoot.setPrimary(true);
                registry.removeBeanDefinition("workerApiObjectMapper");
                registry.registerBeanDefinition("workerApiObjectMapper", asBoot);
            }
        };
    }

    /**
     * Named apart from worker-kafka's own {@code taskDispatcher} (bean overriding is off) and primary,
     * so {@code consumeWorkerEvent} runs through this one.
     */
    @Bean
    @Primary
    public TaskDispatcher exactStringsTaskDispatcher(TaskRegistry registry, TaskReplySink sink, Cancellations cancellations,
                                                     ObjectMapper objectMapper, WorkerProperties properties,
                                                     ObjectProvider<TaskTracing> tracing) {
        log.info("Serving the tasks {}", registry.refs());
        return new TaskDispatcher(registry, sink, cancellations, new ExactStrings(objectMapper), properties.isStrict(),
                tracing.getIfAvailable(() -> TaskTracing.NOOP));
    }
}
