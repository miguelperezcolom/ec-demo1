package io.mateu.ecdemo1.crsintegration.worker;

import io.mateu.ecdemo1.crsintegration.commands.SystemCommands;
import io.mateu.ecdemo1.crsintegration.outbox.Outbox;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The steps this adapter runs for the integration's processes, by step id. Each is idempotent: the
 * engine delivers at least once, and a retry after a lost reply runs the step again.
 *
 * <p>Both write back to a system this adapter fronts, and neither needs its answer: each is a command
 * to that system, written to the outbox before the step answers the engine — the step is done once
 * the command is surely on its way. Its id is the task execution's, so a step run twice asks once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskHandlers {

    final Outbox outbox;
    final PlatformTransactionManager transactions;

    public Map<String, Function<TaskExecutionRequested, List<Variable>>> handlers() {
        return Map.of("annotate-pms-reference", this::annotatePmsReference,
                "annotate-partner-profile", this::annotatePartnerProfile);
    }

    /** Writes the PMS's reservation id back to the CRS. Writing it twice writes the same thing. */
    List<Variable> annotatePmsReference(TaskExecutionRequested task) {
        var locator = variable(task, ProcessVariables.LOCATOR);
        var pmsReservationId = variable(task, ProcessVariables.PMS_RESERVATION_ID);
        new TransactionTemplate(transactions).executeWithoutResult(status -> outbox.appendToCrs(
                new SystemCommands.AnnotatePmsReference(task.taskExecutionId(), locator, pmsReservationId)));
        log.info("Booking {} is reservation {} in the PMS", locator, pmsReservationId);
        return List.of();
    }

    /**
     * Writes back to the master of partners which profile the partner is in the PMS — the one the
     * connector created, or found there. Written twice, the same thing.
     */
    List<Variable> annotatePartnerProfile(TaskExecutionRequested task) {
        var code = variable(task, ProcessVariables.PARTNER_CODE);
        var profileId = variable(task, ProcessVariables.PMS_PROFILE_IDS);
        var profileType = task.variables().stream().filter(v -> "pmsProfileType".equals(v.name())).map(Variable::value)
                .findFirst().orElse(null);
        new TransactionTemplate(transactions).executeWithoutResult(status -> outbox.appendToPartners(
                new SystemCommands.RecordPmsProfile(task.taskExecutionId(), code, profileId, profileType)));
        log.info("Partner {} is profile {} ({}) in the PMS", code, profileId, profileType);
        return List.of();
    }

    static String variable(TaskExecutionRequested task, String name) {
        return task.variables().stream().filter(v -> name.equals(v.name())).map(Variable::value).findFirst()
                .filter(v -> !v.isBlank())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Step %s needs the variable %s".formatted(task.stepId(), name)));
    }
}
