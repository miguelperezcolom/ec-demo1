package io.mateu.ecdemo1.booking.worker;

import io.mateu.ecdemo1.demoreset.DemoReset;
import io.mateu.ecdemo1.demoreset.DemoResetPlan;
import io.mateu.ecdemo1.demoreset.DemoResetTask;

import io.mateu.ecdemo1.booking.worker.runtime.Reasons;
import io.mateu.workflow.worker.api.TaskRegistration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The tasks the CRS serves, each bound to its contract in ec-definitions
 * ({@code definitions/tasks/<id>.ectask}): the engine's worker runtime (worker-kafka) collects these
 * registrations and dispatches every {@code <id>@<version>} it receives on the {@value #TOPIC} topic
 * to the handler here.
 */
@Configuration
public class BookingTasks {

    public static final String TOPIC = "booking";

    public static final String REGISTER_NO_SHOW = "register-no-show";

    public static final String PAUSE_INTAKE = "pause-intake";
    public static final String RESUME_INTAKE = "resume-intake";
    public static final String SEED_DEMO_BOOKINGS = "seed-demo-bookings";

    /**
     * What the demo's reset (reset-demo) empties of the CRS — deploy/demo/zero.sh's list: the bookings,
     * the rate plans added in steady state and the outbox. The catalog imported from Opera stays, and
     * so does the intake pause the reset itself holds (crs_intake_pause).
     */
    @Bean
    public DemoResetPlan demoResetPlan() {
        return DemoResetPlan.truncate("booking", "booking_entity", "crs_booking", "catalog_rate_plan", "outbox_message");
    }

    @Bean
    public TaskRegistration<DemoResetTask.Input, Void> resetTask(DemoReset reset) {
        return DemoResetTask.registration(TOPIC, reset);
    }

    @Bean
    public TaskRegistration<DemoTaskHandlers.PauseIntake, DemoTaskHandlers.IntakePaused> pauseIntakeTask(
            DemoTaskHandlers handlers) {
        return new TaskRegistration<>(PAUSE_INTAKE, 1, TOPIC, DemoTaskHandlers.PauseIntake.class,
                DemoTaskHandlers.IntakePaused.class, Reasons.asBefore(handlers::pauseIntake));
    }

    @Bean
    public TaskRegistration<DemoTaskHandlers.ForProcess, Void> resumeIntakeTask(DemoTaskHandlers handlers) {
        return new TaskRegistration<>(RESUME_INTAKE, 1, TOPIC, DemoTaskHandlers.ForProcess.class, Void.class,
                Reasons.asBefore(handlers::resumeIntake));
    }

    @Bean
    public TaskRegistration<DemoTaskHandlers.Seed, DemoTaskHandlers.Seeded> seedDemoBookingsTask(
            DemoTaskHandlers handlers) {
        return new TaskRegistration<>(SEED_DEMO_BOOKINGS, 1, TOPIC, DemoTaskHandlers.Seed.class,
                DemoTaskHandlers.Seeded.class, Reasons.asBefore(handlers::seedDemoBookings));
    }

    @Bean
    public TaskRegistration<TaskHandlers.NoShow, Void> registerNoShowTask(TaskHandlers handlers) {
        return new TaskRegistration<>(REGISTER_NO_SHOW, 1, TOPIC, TaskHandlers.NoShow.class, Void.class,
                Reasons.asBefore(handlers::registerNoShow));
    }
}
