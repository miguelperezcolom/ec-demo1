package io.mateu.ecdemo1.crsintegration.worker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.mateu.ecdemo1.crsintegration.commands.SystemCommands;
import io.mateu.ecdemo1.crsintegration.outbox.Outbox;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.workflow.worker.api.TaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The steps this adapter runs for the integration's processes, one per task contract (ec-definitions,
 * definitions/tasks). Each is idempotent: the engine delivers at least once, and a retry after a lost
 * reply runs the step again.
 *
 * <p>Both write back to a system this adapter fronts, and neither needs its answer: each is a command
 * to that system, written to the outbox before the step answers the engine — the step is done once
 * the command is surely on its way. Its id is the task execution's, so a step run twice asks once.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskHandlers {

    /** {@code annotate-pms-reference@1}'s input. A task carries every variable of its process; the rest are not its. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PmsReference(String locator, String pmsReservationId) {
    }

    /** {@code annotate-partner-profile@1}'s input. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PartnerProfile(String partnerCode, String pmsProfileIds, String pmsProfileType) {
    }

    final Outbox outbox;
    final PlatformTransactionManager transactions;

    /** Writes the PMS's reservation id back to the CRS. Writing it twice writes the same thing. */
    public Void annotatePmsReference(PmsReference input, TaskContext task) {
        var locator = required(task, ProcessVariables.LOCATOR, input.locator());
        var pmsReservationId = required(task, ProcessVariables.PMS_RESERVATION_ID, input.pmsReservationId());
        new TransactionTemplate(transactions).executeWithoutResult(status -> outbox.appendToCrs(
                new SystemCommands.AnnotatePmsReference(task.taskExecutionId(), locator, pmsReservationId)));
        log.info("Booking {} is reservation {} in the PMS", locator, pmsReservationId);
        return null;
    }

    /**
     * Writes back to the master of partners which profile the partner is in the PMS — the one the
     * connector created, or found there. Written twice, the same thing.
     */
    public Void annotatePartnerProfile(PartnerProfile input, TaskContext task) {
        var code = required(task, ProcessVariables.PARTNER_CODE, input.partnerCode());
        var profileId = required(task, ProcessVariables.PMS_PROFILE_IDS, input.pmsProfileIds());
        var profileType = input.pmsProfileType();
        new TransactionTemplate(transactions).executeWithoutResult(status -> outbox.appendToPartners(
                new SystemCommands.RecordPmsProfile(task.taskExecutionId(), code, profileId, profileType)));
        log.info("Partner {} is profile {} ({}) in the PMS", code, profileId, profileType);
        return null;
    }

    static String required(TaskContext task, String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Step %s needs the variable %s".formatted(task.stepId(), name));
        }
        return value;
    }
}
