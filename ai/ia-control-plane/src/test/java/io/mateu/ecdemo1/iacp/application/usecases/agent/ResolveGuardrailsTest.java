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
    void noMatchingRouteNoGuardrails() {
        routes.save(Route.of(new RouteId("r"), new Name("r"), 1, "support", null, null, null, null, "front",
                new Guardrails(List.of("front-guard"), List.of(), null)));

        var resolved = ask("guest");

        assertThat(resolved.agentId()).isEqualTo("default");
        assertThat(resolved.guardrails()).isEqualTo(ResolveAgentConfigUseCase.ResolvedGuardrails.none());
    }
}
