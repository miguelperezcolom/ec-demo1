package io.mateu.ecdemo1.mapping.worker;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.process.Outcome;
import io.mateu.ecdemo1.integration.model.process.ProcessVariables;
import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.clients.IntegrationClients;
import io.mateu.ecdemo1.mapping.prepare.Preparation;
import io.mateu.ecdemo1.mapping.store.PartnerProfile;
import io.mateu.ecdemo1.mapping.store.PartnerProfileRepository;
import io.mateu.workflow.dtos.Variable;
import io.mateu.workflow.worker.api.TaskContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * The steps the mapping runs for the integration's processes, one per task contract (ec-definitions,
 * definitions/tasks). Every one idempotent.
 *
 * <p>Each input takes what its contract declares and ignores the rest: a task carries every variable
 * of its process, not only the ones the step reads.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskHandlers {

    /**
     * The input of the three preparations: what the process is about, and what its successor is
     * started with should it have to wait (Causes' relaunch variables).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Subject(String definitionId, String processKey, String hotelCode, String locator,
                          String partnerCode, String version, String eventId, String origin) {

        /** The relaunch variables the process carries, as the process has them. */
        List<Variable> variables() {
            var variables = new ArrayList<Variable>();
            add(variables, ProcessVariables.DEFINITION_ID, definitionId);
            add(variables, ProcessVariables.HOTEL_CODE, hotelCode);
            add(variables, ProcessVariables.LOCATOR, locator);
            add(variables, ProcessVariables.PARTNER_CODE, partnerCode);
            add(variables, ProcessVariables.VERSION, version);
            add(variables, ProcessVariables.EVENT_ID, eventId);
            add(variables, ProcessVariables.ORIGIN, origin);
            return variables;
        }

        private static void add(List<Variable> variables, String name, String value) {
            if (value != null) {
                variables.add(new Variable(name, value));
            }
        }
    }

    /** {@code record-partner-profile@1}'s input. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PartnerProfileRecorded(String partnerCode, String pmsProfileIds, String pmsProfileType,
                                         String version) {
    }

    /** {@code resolve-projection@1}'s input. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Projected(String hotelCode, String locator) {
    }

    /** {@code relaunch-process@1}'s input. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Released(String processKey) {
    }

    /** The preparations' output: {@code prepareOutcome}. */
    public record PrepareOutcome(String prepareOutcome) {
    }

    /** {@code relaunch-process@1}'s output: {@code successorKey}. */
    public record Successor(String successorKey) {
    }

    final Preparation preparation;
    final Causes causes;
    final IntegrationClients clients;
    final PartnerProfileRepository partnerProfiles;
    final Clock clock;

    public PrepareOutcome prepareReservation(Subject input, TaskContext task) {
        var reservation = clients.reservation(required(task, ProcessVariables.HOTEL_CODE, input.hotelCode()),
                required(task, ProcessVariables.LOCATOR, input.locator()));
        return outcome(preparation.reservation(reservation, waitContext(task, input, reservation.locator())));
    }

    public PrepareOutcome prepareCancellation(Subject input, TaskContext task) {
        var reservation = clients.reservation(required(task, ProcessVariables.HOTEL_CODE, input.hotelCode()),
                required(task, ProcessVariables.LOCATOR, input.locator()));
        return outcome(preparation.cancellation(reservation, waitContext(task, input, reservation.locator())));
    }

    public PrepareOutcome preparePartner(Subject input, TaskContext task) {
        var partner = clients.partner(required(task, ProcessVariables.PARTNER_CODE, input.partnerCode()));
        return outcome(preparation.partner(partner, waitContext(task, input, partner.code())));
    }

    /**
     * Records which PMS profile the partner is, at which version — and resumes every reservation
     * that was waiting for the partner to exist in the PMS.
     */
    @Transactional
    public Void recordPartnerProfile(PartnerProfileRecorded input, TaskContext task) {
        var code = required(task, ProcessVariables.PARTNER_CODE, input.partnerCode());
        var profile = partnerProfiles.findById(code).orElseGet(PartnerProfile::new);
        profile.setPartnerCode(code);
        profile.setPmsProfileId(required(task, ProcessVariables.PMS_PROFILE_IDS, input.pmsProfileIds()));
        profile.setProfileType(blankAsNull(input.pmsProfileType()));
        profile.setProjectedVersion(Long.parseLong(required(task, ProcessVariables.VERSION, input.version())));
        profile.setUpdatedAt(clock.instant());
        partnerProfiles.save(profile);
        causes.resolveIfOpen(Cause.missingPartner(code).key(), "proyectar-interlocutor");
        return null;
    }

    /** The reservation is in the PMS: a cancellation waiting for that can go on. */
    public Void resolveProjection(Projected input, TaskContext task) {
        causes.resolveIfOpen(Cause.notYetProjected(required(task, ProcessVariables.HOTEL_CODE, input.hotelCode()),
                required(task, ProcessVariables.LOCATOR, input.locator())).key(), "proyectar-reserva");
        return null;
    }

    public Successor relaunch(Released input, TaskContext task) {
        return new Successor(causes.relaunch(required(task, ProcessVariables.PROCESS_KEY, input.processKey())));
    }

    static Preparation.WaitContext waitContext(TaskContext task, Subject input, String subject) {
        return new Preparation.WaitContext(required(task, ProcessVariables.PROCESS_KEY, input.processKey()),
                required(task, ProcessVariables.DEFINITION_ID, input.definitionId()), subject, input.variables(),
                blankAsNull(input.origin()));
    }

    static PrepareOutcome outcome(Outcome outcome) {
        return new PrepareOutcome(outcome.name());
    }

    static String required(TaskContext task, String name, String value) {
        var present = blankAsNull(value);
        if (present == null) {
            throw new IllegalArgumentException("Step %s needs the variable %s".formatted(task.stepId(), name));
        }
        return present;
    }

    static String blankAsNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
