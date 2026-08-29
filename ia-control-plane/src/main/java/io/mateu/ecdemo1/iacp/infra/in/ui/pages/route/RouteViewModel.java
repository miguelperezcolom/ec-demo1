package io.mateu.ecdemo1.iacp.infra.in.ui.pages.route;

import io.mateu.ecdemo1.iacp.application.out.query.dto.RouteDto;
import io.mateu.ecdemo1.iacp.application.usecases.route.create.CreateRouteCommand;
import io.mateu.ecdemo1.iacp.application.usecases.route.create.CreateRouteUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.route.update.UpdateRouteCommand;
import io.mateu.ecdemo1.iacp.application.usecases.route.update.UpdateRouteUseCase;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * The editor for a routing rule.
 *
 * <p>The four conditions are all optional and empty means "any": a rule with none set is a
 * catch-all, one with several is an AND. Rules are tried low priority first, and the first that
 * matches picks the agent — so a specific rule needs a lower number than the general one it should
 * beat.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class RouteViewModel implements Identifiable {

    @Section("Route")
    @NotEmpty
    @ReadOnly
    @HiddenInCreate
    @Help("Cannot be changed once created.")
    String id;

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
    @Help("The id of the agent that answers when this rule matches.")
    String targetAgentId;

    @Section("Status")
    boolean enabled;

    final CreateRouteUseCase createRouteUseCase;
    final UpdateRouteUseCase updateRouteUseCase;

    public String create(HttpRequest httpRequest) {
        return createRouteUseCase.handle(new CreateRouteCommand(newId, name, priority, role, tenant,
                locale, routePrefix, targetAgentId));
    }

    public void save(HttpRequest httpRequest) {
        updateRouteUseCase.handle(new UpdateRouteCommand(id, name, priority, role, tenant, locale,
                routePrefix, targetAgentId, enabled));
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
        enabled = dto.enabled();
        return this;
    }

    @Override
    public String toString() {
        return id != null ? name + " → " + targetAgentId : "New route";
    }
}
