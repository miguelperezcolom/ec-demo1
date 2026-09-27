package io.mateu.ecdemo1.mapping.worker;

import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.process.Definitions;
import io.mateu.ecdemo1.integration.model.process.Outcome;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
import io.mateu.ecdemo1.mapping.prepare.Preparation;
import io.mateu.ecdemo1.mapping.store.PartnerProfile;
import io.mateu.ecdemo1.mapping.store.PartnerProfileRepository;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.dtos.events.integration.TaskExecutionRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The steps the mapping runs for the integration's processes, by step id. Every one idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskHandlers {

    final Preparation preparation;
    final Causes causes;
    final IntegrationClients clients;
    final PartnerProfileRepository partnerProfiles;
    final Clock clock;

    /**
     * The handler for a task, by its definition and step: "prepare" is a step of every one of the
     * integration's processes, and what it prepares depends on which. Empty for a step that is not
     * the mapping's.
     */
    public Optional<Function<TaskExecutionRequested, List<Variable>>> handler(TaskExecutionRequested task) {
        var step = task.stepId();
        if (step.startsWith("relaunch")) {
            return Optional.of(this::relaunch);
        }
        return Optional.ofNullable(switch (step) {
            case "prepare" -> switch (task.workflowDefinitionId()) {
                case Definitions.PROJECT_RESERVATION -> this::prepareReservation;
                case Definitions.PROJECT_CANCELLATION -> this::prepareCancellation;
                case Definitions.PROJECT_PARTNER -> this::preparePartner;
                default -> null;
            };
            case "record-partner-profile" -> this::recordPartnerProfile;
            case "resolve-projection" -> this::resolveProjection;
            default -> null;
        });
    }

    List<Variable> prepareReservation(TaskExecutionRequested task) {
        var reservation = clients.reservation(var(task, ProcessVariables.HOTEL_CODE), var(task, ProcessVariables.LOCATOR));
        return outcome(preparation.reservation(reservation, waitContext(task, reservation.locator())));
    }

    List<Variable> prepareCancellation(TaskExecutionRequested task) {
        var reservation = clients.reservation(var(task, ProcessVariables.HOTEL_CODE), var(task, ProcessVariables.LOCATOR));
        return outcome(preparation.cancellation(reservation, waitContext(task, reservation.locator())));
    }

    List<Variable> preparePartner(TaskExecutionRequested task) {
        var partner = clients.partner(var(task, ProcessVariables.PARTNER_CODE));
        return outcome(preparation.partner(partner, waitContext(task, partner.code())));
    }

    /**
     * Records which PMS profile the partner is, at which version — and resumes every reservation
     * that was waiting for the partner to exist in the PMS.
     */
    @Transactional
    List<Variable> recordPartnerProfile(TaskExecutionRequested task) {
        var code = var(task, ProcessVariables.PARTNER_CODE);
        var profile = partnerProfiles.findById(code).orElseGet(PartnerProfile::new);
        profile.setPartnerCode(code);
        profile.setPmsProfileId(var(task, ProcessVariables.PMS_PROFILE_IDS));
        profile.setProfileType(optional(task, "pmsProfileType"));
        profile.setProjectedVersion(Long.parseLong(var(task, ProcessVariables.VERSION)));
        profile.setUpdatedAt(clock.instant());
        partnerProfiles.save(profile);
        causes.resolveIfOpen(Cause.missingPartner(code).key(), "proyectar-interlocutor");
        return List.of();
    }

    /** The reservation is in the PMS: a cancellation waiting for that can go on. */
    List<Variable> resolveProjection(TaskExecutionRequested task) {
        causes.resolveIfOpen(Cause.notYetProjected(var(task, ProcessVariables.HOTEL_CODE),
                var(task, ProcessVariables.LOCATOR)).key(), "proyectar-reserva");
        return List.of();
    }

    List<Variable> relaunch(TaskExecutionRequested task) {
        return List.of(new Variable("successorKey", causes.relaunch(var(task, ProcessVariables.PROCESS_KEY))));
    }

    static Preparation.WaitContext waitContext(TaskExecutionRequested task, String subject) {
        return new Preparation.WaitContext(var(task, ProcessVariables.PROCESS_KEY), var(task, ProcessVariables.DEFINITION_ID),
                subject, task.variables(), optional(task, ProcessVariables.ORIGIN));
    }

    static List<Variable> outcome(Outcome outcome) {
        return List.of(new Variable(ProcessVariables.PREPARE_OUTCOME, outcome.name()));
    }

    static String var(TaskExecutionRequested task, String name) {
        var value = optional(task, name);
        if (value == null) {
            throw new IllegalArgumentException("Step %s needs the variable %s".formatted(task.stepId(), name));
        }
        return value;
    }

    static String optional(TaskExecutionRequested task, String name) {
        return task.variables().stream().filter(v -> name.equals(v.name())).map(Variable::value)
                .filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
    }
}
