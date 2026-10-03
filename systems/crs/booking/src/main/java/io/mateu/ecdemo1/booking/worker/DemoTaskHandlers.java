package io.mateu.ecdemo1.booking.worker;

import io.mateu.ecdemo1.booking.application.usecases.intake.CrsIntake;
import io.mateu.ecdemo1.booking.infra.in.ui.pages.DemoBookingSeeder;
import io.mateu.workflow.worker.api.TaskContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The CRS's steps of the demo's reset (process reset-demo): its intake paused while the services go
 * back to zero and resumed after, and — when asked — the demo bookings flow 1 starts from.
 */
@Component
@RequiredArgsConstructor
public class DemoTaskHandlers {

    /** {@code pause-intake@1}'s input. */
    public record PauseIntake(String processKey, String launchedBy) {
    }

    public record IntakePaused(String intakePausedUntil) {
    }

    /** {@code resume-intake@1}'s input. */
    public record ForProcess(String processKey) {
    }

    /**
     * {@code seed-demo-bookings@1}'s input: {@code sembrar}, the confirmation form's box — anything but
     * «true» seeds nothing. The step always runs (a CHOICE before it, and an XOR JOIN after, ended the
     * process early in the engine: the branch not taken cancelled the join).
     */
    public record Seed(String processKey, String sembrar) {
    }

    public record Seeded(String seededBookings) {
    }

    final CrsIntake intake;
    final DemoBookingSeeder seeder;

    public IntakePaused pauseIntake(PauseIntake input, TaskContext task) {
        var key = input == null || input.processKey() == null ? task.processId() : input.processKey();
        var until = intake.pause(key, input == null ? null : input.launchedBy());
        return new IntakePaused(until.toString());
    }

    public Void resumeIntake(ForProcess input, TaskContext task) {
        intake.resume(input == null || input.processKey() == null ? task.processId() : input.processKey());
        return null;
    }

    public Seeded seedDemoBookings(Seed input, TaskContext task) {
        if (input == null || !"true".equals(input.sembrar())) {
            return new Seeded("");
        }
        var key = input.processKey() == null ? task.processId() : input.processKey();
        return new Seeded(String.join(",", seeder.seed(key)));
    }
}
