package io.mateu.ecdemo1.iacp.infra.in.ui.pages.route;

import io.mateu.ecdemo1.iacp.infra.in.ui.pages.CatalogueReferenceOptions;
import io.mateu.ecdemo1.uicommons.crud.CatalogueEditor;
import io.mateu.ecdemo1.iacp.application.out.query.dto.RouteDto;
import io.mateu.ecdemo1.iacp.application.usecases.route.create.CreateRouteCommand;
import io.mateu.ecdemo1.iacp.application.usecases.route.create.CreateRouteUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.route.update.UpdateRouteCommand;
import io.mateu.ecdemo1.iacp.application.usecases.route.update.UpdateRouteUseCase;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.vo.Guardrails;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The editor for a routing rule.
 *
 * <p>The four conditions are all optional and empty means "any": a rule with none set is a
 * catch-all, one with several is an AND. Rules are tried low priority first, and the first that
 * matches picks the agent — so a specific rule needs a lower number than the general one it should
 * beat.
 *
 * <p>The guardrails are agents of the catalogue too, called over A2A around the target: the input
 * ones before it sees the user's text, the output ones before the user sees its answer. Each
 * answers a verdict — allow, block, or rewrite — and runs in the order ticked.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class RouteViewModel implements CatalogueEditor<RouteDto>, OptionsSupplier {

    @Section("Route")
    @ReadOnly
    @HiddenInCreate
    @Help("Cannot be changed once created.")
    String id;

    @NotEmpty
    @Help("Only used when creating. Lowercase, no spaces — e.g. support-agents.")
    String newId;

    @NotEmpty
    String name;

    @Help("Lower is tried first. Give a specific rule a lower number than the catch-all it beats.")
    int priority;

    @Section("Conditions (empty = any)")
    @Help("A realm role the caller must have — e.g. support. Empty matches any role.")
    String role;

    @Help("A tenant the caller must belong to. Empty matches any tenant.")
    String tenant;

    @Help("A UI locale the request must carry — e.g. es. Empty matches any locale.")
    String locale;

    @Help("A prefix the current UI route must start with — e.g. /bookings. Empty matches any screen.")
    String routePrefix;

    @Section("Target")
    @NotEmpty
    @Lookup(search = CatalogueReferenceOptions.class, label = CatalogueReferenceOptions.class)
    @Label("Target agent")
    @Help("The agent that answers when this rule matches.")
    String targetAgentId;

    @Section("Guardrails")
    @Stereotype(FieldStereotype.checkbox)
    @Label("Input guardrails")
    @Help("Agents that read the user's text before the target agent does, in order. Each answers "
            + "ALLOW, BLOCK (the agent is never called) or REWRITE (the next one reads the new text).")
    List<String> inputGuardrailAgentIds;

    @Stereotype(FieldStereotype.checkbox)
    @Label("Output guardrails")
    @Help("Agents that read the target agent's answer before the user does, in order. With any "
            + "set, the answer is not streamed: it is shown once it has been checked.")
    List<String> outputGuardrailAgentIds;

    @Label("When a guardrail fails")
    @Help("CLOSED blocks the text when a guardrail cannot be reached or does not answer a verdict; "
            + "OPEN lets it through and logs a warning.")
    Guardrails.Failure guardrailFailure = Guardrails.Failure.CLOSED;

    @Section("Status")
    boolean enabled;

    final CreateRouteUseCase createRouteUseCase;
    final UpdateRouteUseCase updateRouteUseCase;
    final CatalogueReferenceOptions referenceOptions;

    public String create(HttpRequest httpRequest) {
        return createRouteUseCase.handle(new CreateRouteCommand(newId, name, priority, role, tenant,
                locale, routePrefix, targetAgentId, inputGuardrailAgentIds, outputGuardrailAgentIds,
                failure()));
    }

    public void save(HttpRequest httpRequest) {
        updateRouteUseCase.handle(new UpdateRouteCommand(id, name, priority, role, tenant, locale,
                routePrefix, targetAgentId, inputGuardrailAgentIds, outputGuardrailAgentIds, failure(),
                enabled));
    }

    private String failure() {
        return guardrailFailure == null ? null : guardrailFailure.name();
    }

    /**
     * The guardrail checkboxes: every agent but the one this route targets. The failure mode is
     * listed here too: an editor that supplies options answers for all its fields, enums included.
     */
    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return switch (fieldName) {
            case "guardrailFailure" -> List.of(
                    new Option("CLOSED", "CLOSED", "Block the text"),
                    new Option("OPEN", "OPEN", "Let it through and log a warning"));
            case "inputGuardrailAgentIds", "outputGuardrailAgentIds" ->
                    referenceOptions.all(fieldName, httpRequest).stream()
                            .filter(o -> targetAgentId == null || !targetAgentId.equals(o.value()))
                            .toList();
            default -> List.of();
        };
    }

    @Override
    public String id() {
        return id;
    }

    public RouteViewModel load(RouteDto dto) {
        id = dto.id();
        newId = dto.id();
        name = dto.name();
        priority = dto.priority();
        role = dto.role();
        tenant = dto.tenant();
        locale = dto.locale();
        routePrefix = dto.routePrefix();
        targetAgentId = dto.targetAgentId();
        inputGuardrailAgentIds = new ArrayList<>(dto.inputGuardrailAgentIds());
        outputGuardrailAgentIds = new ArrayList<>(dto.outputGuardrailAgentIds());
        guardrailFailure = Guardrails.Failure.parse(dto.guardrailFailure());
        enabled = dto.enabled();
        return this;
    }

    @Override
    public String toString() {
        return id != null ? name + " → " + targetAgentId : "New route";
    }
}
