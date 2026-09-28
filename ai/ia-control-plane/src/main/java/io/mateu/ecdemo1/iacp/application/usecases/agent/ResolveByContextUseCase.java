package io.mateu.ecdemo1.iacp.application.usecases.agent;

import io.mateu.ecdemo1.iacp.application.out.repository.AgentRepository;
import io.mateu.ecdemo1.iacp.application.out.repository.RouteRepository;
import io.mateu.ecdemo1.iacp.application.usecases.budget.CheckBudgetUseCase;
import io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo.AgentId;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.Route;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a request's context into the configuration to answer it with: which agent, resolved, and
 * only if it is within budget.
 *
 * <p>Three steps, in order. <strong>Route</strong>: the enabled rules are tried in priority order
 * and the first that matches the caller's roles, tenant, locale and screen picks the agent; if none
 * matches, the caller's default agent stands in, so routing is additive — a deployment with no rules
 * behaves exactly as before. <strong>Resolve</strong>: the chosen agent is turned into a
 * configuration by {@link ResolveAgentConfigUseCase}, which already drops what is unusable and
 * refuses an agent with no model. <strong>Check</strong>: the budgets are consulted for that agent,
 * its model, and the caller; if any is spent, the request is refused here rather than after the
 * money is gone.
 *
 * <p>The route that matched also brings its guardrails: the agents to run over A2A before and after
 * the chosen one, resolved like an agent's peers — a missing or disabled one is dropped with a
 * warning. With no route matching there are none.
 *
 * <p>Both refusals — an unusable agent and an exhausted budget — are the same kind of answer to the
 * caller: "not now, and here is why", a 409 with a sentence, not a 500. The agent surfaces it as the
 * chat panel's reply.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResolveByContextUseCase {

    private final RouteRepository routes;
    private final ResolveAgentConfigUseCase resolveAgentConfig;
    private final CheckBudgetUseCase checkBudget;
    private final AgentRepository agents;

    /** What the agent knows about a request: who is asking, and from where. */
    public record RequestContext(String userId, List<String> roles, String tenant, String locale,
                                 String route, String defaultAgentId) {
    }

    /** A budget stood in the way. Distinct from AgentNotUsable so the message can say which. */
    public static class BudgetExceededException extends RuntimeException {
        public BudgetExceededException(String message) {
            super(message);
        }
    }

    @Transactional(readOnly = true)
    public ResolveAgentConfigUseCase.Resolved handle(RequestContext context) {
        var route = route(context);
        var agentId = route != null ? route.getTargetAgentId() : context.defaultAgentId();
        var resolved = resolveAgentConfig.handle(agentId);

        var verdict = checkBudget.check(agentId, resolved.llm().id(), context.userId(),
                context.tenant());
        if (!verdict.allowed()) {
            var e = verdict.exceeded();
            throw new BudgetExceededException("Budget '" + e.name() + "' is spent: "
                    + e.spent() + " of " + e.limit() + " tokens this " + e.period()
                    + " for " + e.scope() + " '" + e.subjectId() + "'. Try again after it resets.");
        }
        if (route == null) {
            return resolved;
        }
        var warnings = new ArrayList<String>();
        var guardrails = route.getGuardrails();
        var resolvedGuardrails = new ResolveAgentConfigUseCase.ResolvedGuardrails(
                guardrails(guardrails.input(), "Input", warnings),
                guardrails(guardrails.output(), "Output", warnings),
                guardrails.failure().name());
        if (!warnings.isEmpty()) {
            log.warn("Route {} resolved with {} guardrail warning(s): {}", route.getId(), warnings.size(), warnings);
        }
        return resolved.withGuardrails(resolvedGuardrails, warnings);
    }

    private List<ResolveAgentConfigUseCase.ResolvedGuardrail> guardrails(List<String> ids, String side,
                                                                       List<String> warnings) {
        var out = new ArrayList<ResolveAgentConfigUseCase.ResolvedGuardrail>();
        for (var id : ids) {
            var found = agents.findById(new AgentId(id));
            if (found.isEmpty()) {
                warnings.add(side + " guardrail '" + id + "' is no longer in the catalogue — skipped");
                continue;
            }
            var agent = found.get();
            if (!agent.isUsable()) {
                warnings.add(side + " guardrail '" + agent.getName() + "' is disabled — skipped");
                continue;
            }
            out.add(new ResolveAgentConfigUseCase.ResolvedGuardrail(id, agent.getName().value(),
                    resolveAgentConfig.a2aUrl(id)));
        }
        return out;
    }

    /** The first enabled rule that matches, or null when none does and the caller's default stands. */
    private Route route(RequestContext ctx) {
        for (var route : routes.findEnabledOrderedByPriority()) {
            if (route.matches(ctx.roles(), ctx.tenant(), ctx.locale(), ctx.route())) {
                log.debug("Route '{}' matched — using agent '{}'", route.getId(), route.getTargetAgentId());
                return route;
            }
        }
        return null;
    }
}
