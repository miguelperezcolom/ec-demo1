package io.mateu.ecdemo1.pmsintegration.worker;

import io.mateu.ecdemo1.pmsintegration.ohip.PmsTransientException;
import io.mateu.ecdemo1.pmsintegration.worker.runtime.LegacyTaskRefs;
import io.mateu.workflow.worker.api.ReplyNotAcceptedException;
import io.mateu.workflow.worker.api.TaskFailure;
import io.mateu.workflow.worker.api.TaskHandler;
import io.mateu.workflow.worker.api.TaskRegistration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Function;

/**
 * The tasks the PMS adapter serves, each bound to its contract in ec-definitions
 * ({@code definitions/tasks/<id>.ectask}): the engine's worker runtime (worker-kafka) collects these
 * registrations and dispatches every {@code <id>@<version>} it receives on the {@value #TOPIC} topic
 * to the handler here.
 *
 * <p>A step that fails is answered as a failure and the engine retries it with backoff, as it always
 * was; every failure also goes on the {@link RetryWatch}, which raises the alarm when it lasts, and a
 * success takes it off.
 */
@Configuration
@Slf4j
public class PmsTasks {

    public static final String TOPIC = "pms-integration";

    public static final String ENSURE_GUEST_PROFILE = "ensure-guest-profile";
    public static final String UPSERT_RESERVATION = "upsert-reservation";
    public static final String CANCEL_RESERVATION = "cancel-reservation";
    public static final String ENSURE_PARTNER_PROFILE = "ensure-partner-profile";
    public static final String PROJECT_STAY = "project-stay";

    @Bean
    public TaskRegistration<TaskHandlers.ReservationTask, TaskHandlers.GuestProfile> ensureGuestProfileTask(
            TaskHandlers handlers, RetryWatch watch) {
        return new TaskRegistration<>(ENSURE_GUEST_PROFILE, 1, TOPIC, TaskHandlers.ReservationTask.class,
                TaskHandlers.GuestProfile.class, watched(watch, handlers::ensureGuestProfile,
                        TaskHandlers.ReservationTask::hotelCode, TaskHandlers.ReservationTask::locator));
    }

    @Bean
    public TaskRegistration<TaskHandlers.ReservationTask, TaskHandlers.Write> upsertReservationTask(
            TaskHandlers handlers, RetryWatch watch) {
        return new TaskRegistration<>(UPSERT_RESERVATION, 1, TOPIC, TaskHandlers.ReservationTask.class,
                TaskHandlers.Write.class, watched(watch, handlers::upsertReservation,
                        TaskHandlers.ReservationTask::hotelCode, TaskHandlers.ReservationTask::locator));
    }

    @Bean
    public TaskRegistration<TaskHandlers.ReservationTask, TaskHandlers.Write> cancelReservationTask(
            TaskHandlers handlers, RetryWatch watch) {
        return new TaskRegistration<>(CANCEL_RESERVATION, 1, TOPIC, TaskHandlers.ReservationTask.class,
                TaskHandlers.Write.class, watched(watch, handlers::cancelReservation,
                        TaskHandlers.ReservationTask::hotelCode, TaskHandlers.ReservationTask::locator));
    }

    @Bean
    public TaskRegistration<TaskHandlers.PartnerTask, TaskHandlers.PartnerProfiled> ensurePartnerProfileTask(
            TaskHandlers handlers, RetryWatch watch) {
        return new TaskRegistration<>(ENSURE_PARTNER_PROFILE, 1, TOPIC, TaskHandlers.PartnerTask.class,
                TaskHandlers.PartnerProfiled.class, watched(watch, handlers::ensurePartnerProfile,
                        input -> null, TaskHandlers.PartnerTask::partnerCode));
    }

    @Bean
    public TaskRegistration<TaskHandlers.StayTask, Void> projectStayTask(TaskHandlers handlers, RetryWatch watch) {
        return new TaskRegistration<>(PROJECT_STAY, 1, TOPIC, TaskHandlers.StayTask.class, Void.class,
                watched(watch, handlers::projectStay, TaskHandlers.StayTask::pmsHotelCode,
                        input -> input.pmsHotelCode() + "/" + input.pmsReservationId()));
    }

    /**
     * The contract a step answers to when its definition does not name one yet: every step of
     * proyectar-reserva, proyectar-cancelacion, proyectar-interlocutor and proyectar-estancia today —
     * the step ids are the contract ids. Goes once those reference their tasks.
     */
    @Bean
    public LegacyTaskRefs legacyTaskRefs() {
        return (definitionId, stepId) -> stepId == null ? null : switch (stepId) {
            case ENSURE_GUEST_PROFILE, UPSERT_RESERVATION, CANCEL_RESERVATION, ENSURE_PARTNER_PROFILE, PROJECT_STAY ->
                    stepId + "@1";
            default -> null;
        };
    }

    /**
     * The handler, with the failure it gives the engine what it always was — Opera not answering is its
     * message, anything else the exception — and the {@link RetryWatch} told of every outcome.
     */
    static <I, O> TaskHandler<I, O> watched(RetryWatch watch, TaskHandler<I, O> handler, Function<I, String> hotel,
                                            Function<I, String> subject) {
        return (input, context) -> {
            try {
                var output = handler.handle(input, context);
                watch.succeeded(context.processId(), context.stepId());
                return output;
            } catch (TaskFailure | ReplyNotAcceptedException e) {
                throw e;
            } catch (PmsTransientException e) {
                log.warn("Step {} of {}: Opera not available, the engine will retry: {}", context.stepId(),
                        context.processId(), e.getMessage());
                watch.failed(context.processId(), context.stepId(), hotel.apply(input), subject.apply(input), e.getMessage());
                throw new TaskFailure(e.getMessage() == null ? e.toString() : e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Step {} of {} failed: {}", context.stepId(), context.processId(), e.toString());
                watch.failed(context.processId(), context.stepId(), hotel.apply(input), subject.apply(input), e.toString());
                throw new TaskFailure(e.toString());
            }
        };
    }
}
