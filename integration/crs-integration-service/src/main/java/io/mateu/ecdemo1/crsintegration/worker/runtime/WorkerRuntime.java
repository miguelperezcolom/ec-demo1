package io.mateu.ecdemo1.crsintegration.worker.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mateu.workflow.ddd.DomainEvent;
import io.mateu.workflow.dtos.TraceContext;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import io.mateu.workflow.worker.api.Cancellations;
import io.mateu.workflow.worker.api.TaskDispatcher;
import io.mateu.workflow.worker.api.TaskRegistry;
import io.mateu.workflow.worker.api.TaskReplySink;
import io.mateu.workflow.worker.api.TaskTracing;
import io.mateu.workflow.worker.api.WorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.Message;

import java.util.function.Consumer;

/**
 * The engine's worker runtime as this service uses it. worker-kafka binds {@code consumeWorkerEvent}
 * to the task topic and brings the reply sink, the cancellations and the tracing; this adds two
 * things to it:
 * <ul>
 *   <li>the {@link TaskDispatcher}, the runtime's own, bound through {@link ExactStrings};</li>
 *   <li>{@link LegacyTasks}: the tasks the engine dispatches with no {@code taskId}, from a step whose
 *       definition names no contract yet, run through that same dispatcher.</li>
 * </ul>
 */
@Configuration
@Slf4j
public class WorkerRuntime {

    /**
     * worker-api registers an ObjectMapper of its own when it finds none, and it can find none only
     * because the one this app uses is defined later (by a library, with Boot's backing off): then
     * there are two, and nothing that asks for an ObjectMapper starts. Only a fallback, then.
     */
    @Bean
    public static BeanFactoryPostProcessor workerApiObjectMapperOnlyAsFallback() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("workerApiObjectMapper")
                    && beanFactory.getBeanNamesForType(ObjectMapper.class, true, false).length > 1) {
                beanFactory.getBeanDefinition("workerApiObjectMapper").setAutowireCandidate(false);
            }
        };
    }

    /**
     * Named apart from worker-kafka's own {@code taskDispatcher} (bean overriding is off) and primary,
     * so {@code consumeWorkerEvent} and {@link LegacyTasks} both run through this one.
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

    @Bean
    public LegacyTasks legacyTasks(TaskDispatcher dispatcher, LegacyTaskRefs refs) {
        return new LegacyTasks(dispatcher, refs);
    }

    /** Bound to the task topic next to {@code consumeWorkerEvent}, on a group of its own. */
    @Bean
    public Consumer<Message<DomainEvent>> consumeLegacyTasks(LegacyTasks legacyTasks) {
        return message -> legacyTasks.accept(message.getPayload(), TraceContext.fromHeaders(message.getHeaders()));
    }

    /**
     * Runs a task dispatched with no {@code taskId} as the contract its step answers to, on the
     * consumer thread: a reply the broker refuses fails the listener and the task is redelivered.
     * Anything else — a contract-backed task, a cancellation, a step that is not this service's — is
     * left to {@code consumeWorkerEvent}.
     */
    public static final class LegacyTasks {

        final TaskDispatcher dispatcher;
        final LegacyTaskRefs refs;

        public LegacyTasks(TaskDispatcher dispatcher, LegacyTaskRefs refs) {
            this.dispatcher = dispatcher;
            this.refs = refs;
        }

        public boolean accept(DomainEvent event, TraceContext traceContext) {
            if (!(event instanceof TaskExecutionRequested task) || (task.taskId() != null && !task.taskId().isBlank())) {
                return false;
            }
            var ref = refs.ref(task.workflowDefinitionId(), task.stepId());
            if (ref == null) {
                return false;
            }
            dispatcher.dispatch(new TaskExecutionRequested(task.taskExecutionId(), task.processId(),
                    task.workflowDefinitionId(), task.stepId(), ref, task.variables()), traceContext);
            return true;
        }
    }
}
