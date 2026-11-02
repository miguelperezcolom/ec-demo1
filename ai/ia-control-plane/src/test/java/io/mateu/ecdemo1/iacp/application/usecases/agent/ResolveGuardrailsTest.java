package io.mateu.ecdemo1.iacp.application.usecases.agent;

import io.mateu.ecdemo1.iacp.InMemoryRepositories;
import io.mateu.ecdemo1.iacp.application.usecases.budget.CheckBudgetUseCase;
import io.mateu.ecdemo1.iacp.domain.aggregates.agent.Agent;
import io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo.AgentId;
import io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo.SystemPrompt;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmId;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.Route;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.vo.Guardrails;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.vo.RouteId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** /internal/agents/resolve hands out the guardrails of the route that matched, minus the unusable ones. */
class ResolveGuardrailsTest {

    final InMemoryRepositories.Agents agents = new InMemoryRepositories.Agents();
    final InMemoryRepositories.Llms llms = new InMemoryRepositories.Llms();
    final InMemoryRepositories.Routes routes = new InMemoryRepositories.Routes();
    ResolveByContextUseCase resolve;

    @BeforeEach
    void setUp() {
        llms.save(InMemoryRepositories.usableLlm("llm"));
        var config = new ResolveAgentConfigUseCase(agents, llms, new InMemoryRepositories.Mcps(),
                new InMemoryRepositories.Rags(), InMemoryRepositories.CIPHER);
        config.a2aBaseUrl = "http://ia-agent:8095";
        var budgets = new CheckBudgetUseCase(null, null) {
            @Override
            public Verdict check(String agentId, String llmId, String userId, String tenant) {
                return new Verdict(true, null);
            }
        };
        resolve = new ResolveByContextUseCase(routes, config, budgets, agents);
        agent("front");
        agent("default");
    }

    Agent agent(String id) {
        var agent = Agent.of(new AgentId(id), new Name("Agent " + id), new SystemPrompt("p"), new LlmId("llm"),
                List.of(), List.of(), List.of(), null);
        agents.save(agent);
        return agent;
    }

    ResolveAgentConfigUseCase.Resolved ask(String role) {
        return resolve.handle(new ResolveByContextUseCase.RequestContext("u", List.of(role), null, null, null,
                "default"));
    }

    @Test
    void theMatchingRoutesGuardrailsComeInOrderWithTheirA2aAddress() {
        agent("pii");
        agent("toxicity");
        routes.save(Route.of(new RouteId("r"), new Name("r"), 1, "support", null, null, null, null, "front",
                new Guardrails(List.of("toxicity", "pii"), List.of("pii"), Guardrails.Failure.OPEN)));

        var resolved = ask("support");

        assertThat(resolved.agentId()).isEqualTo("front");
        assertThat(resolved.guardrails().input()).containsExactly(
                new ResolveAgentConfigUseCase.ResolvedGuardrail("toxicity", "Agent toxicity", "http://ia-agent:8095/a2a/toxicity"),
                new ResolveAgentConfigUseCase.ResolvedGuardrail("pii", "Agent pii", "http://ia-agent:8095/a2a/pii"));
        assertThat(resolved.guardrails().output()).extracting(ResolveAgentConfigUseCase.ResolvedGuardrail::id)
                .containsExactly("pii");
        assertThat(resolved.guardrails().failure()).isEqualTo("OPEN");
        assertThat(resolved.warnings()).isEmpty();
    }

    @Test
    void aMissingOrDisabledGuardrailIsDroppedWithAWarning() {
        var off = agent("off");
        off.update(off.getName(), off.getSystemPrompt(), off.getLlmId(), List.of(), List.of(), List.of(), null,
                Enabled.no());
        agent("pii");
        routes.save(Route.of(new RouteId("r"), new Name("r"), 1, "support", null, null, null, null, "front",
                new Guardrails(List.of("gone", "pii"), List.of("off"), null)));

        var resolved = ask("support");

        assertThat(resolved.guardrails().input()).extracting(ResolveAgentConfigUseCase.ResolvedGuardrail::id)
                .containsExactly("pii");
        assertThat(resolved.guardrails().output()).isEmpty();
        assertThat(resolved.guardrails().failure()).isEqualTo("CLOSED");
        assertThat(resolved.warnings()).containsExactly(
                "Input guardrail 'gone' is no longer in the catalogue — skipped",
                "Output guardrail 'Agent off' is disabled — skipped");
    }

    @Test
    void aNamedAgentKeepsItsIdAndTakesTheGuardrailsOfTheRouteThatCoversItOverA2a() {
        agent("pii");
        agent("toxicity");
        // A console's route is not about A2A; another agent's route is not about this one.
        routes.save(Route.of(new RouteId("console"), new Name("console"), 1, null, null, null, null, "data-plane", "front",
                new Guardrails(List.of("toxicity"), List.of(), null)));
        routes.save(Route.of(new RouteId("other"), new Name("other"), 2, null, null, null, null, "a2a", "default",
                new Guardrails(List.of("toxicity"), List.of(), null)));
        routes.save(Route.of(new RouteId("a2a"), new Name("a2a"), 3, null, null, null, null, "a2a", "front",
                new Guardrails(List.of("pii"), List.of(), null)));

        var resolved = resolve.handleNamed("front", new ResolveByContextUseCase.RequestContext("u", List.of(), null, null,
                null, null));

        assertThat(resolved.agentId()).isEqualTo("front");
        assertThat(resolved.guardrails().input()).extracting(ResolveAgentConfigUseCase.ResolvedGuardrail::id)
                .containsExactly("pii");
    }

    @Test
    void aNamedAgentIsNotRoutedAwayAndHasNoGuardrailsWithoutARoute() {
        routes.save(Route.of(new RouteId("r"), new Name("r"), 1, null, null, null, null, null, "front",
                new Guardrails(List.of("front-guard"), List.of(), null)));

        var resolved = resolve.handleNamed("default", new ResolveByContextUseCase.RequestContext("u", List.of(), null,
                null, null, null));

        assertThat(resolved.agentId()).isEqualTo("default");
        assertThat(resolved.guardrails()).isEqualTo(ResolveAgentConfigUseCase.ResolvedGuardrails.none());
    }

    @Test
    void aSpentBudgetRefusesANamedAgent() {
        var refusing = new CheckBudgetUseCase(null, null) {
            @Override
            public Verdict check(String agentId, String llmId, String userId, String tenant) {
                return new Verdict(false, new Exceeded("b", "daily", io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetScope.USER,
                        userId, io.mateu.ecdemo1.iacp.domain.aggregates.budget.vo.BudgetPeriod.DAY, 10, 10));
            }
        };
        var config = new ResolveAgentConfigUseCase(agents, llms, new InMemoryRepositories.Mcps(),
                new InMemoryRepositories.Rags(), InMemoryRepositories.CIPHER);
        var strict = new ResolveByContextUseCase(routes, config, refusing, agents);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> strict.handleNamed("default",
                        new ResolveByContextUseCase.RequestContext("u", List.of(), null, null, null, null)))
                .isInstanceOf(ResolveByContextUseCase.BudgetExceededException.class)
                .hasMessageContaining("daily");
    }

    @Test
    void noMatchingRouteNoGuardrails() {
        routes.save(Route.of(new RouteId("r"), new Name("r"), 1, "support", null, null, null, null, "front",
                new Guardrails(List.of("front-guard"), List.of(), null)));

        var resolved = ask("guest");

        assertThat(resolved.agentId()).isEqualTo("default");
        assertThat(resolved.guardrails()).isEqualTo(ResolveAgentConfigUseCase.ResolvedGuardrails.none());
    }
}
