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

/** Resolve precedence: a route (channel included), else the request's default agent, else the catalogue's. */
class ResolveByChannelTest {

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

    ResolveAgentConfigUseCase.Resolved ask(String route, String channel, String defaultAgent) {
        return resolve.handle(new ResolveByContextUseCase.RequestContext("u", List.of(), null, null, route,
                channel, defaultAgent));
    }

    @Test
    void aChannelRouteMatchesOnlyPromptsFromThatConsole() {
        agent("mapping");
        agent("control");
        routes.save(Route.of(new RouteId("m"), new Name("m"), 10, null, null, null, "/mapping", null, "mapping",
                Guardrails.none()));
        routes.save(Route.of(new RouteId("c"), new Name("c"), 5, null, null, null, "/mapping", "control-plane",
                "control", Guardrails.none()));

        assertThat(ask("/mapping/dictionary", "control-plane", "front").agentId()).isEqualTo("control");
        assertThat(ask("/mapping/dictionary", "data-plane", "front").agentId()).isEqualTo("mapping");
        // No channel said: a channel route does not match, a channel-less one still does.
        assertThat(ask("/mapping/dictionary", null, "front").agentId()).isEqualTo("mapping");
    }

    @Test
    void noRouteMatchingTheRequestsDefaultAgentAnswers() {
        assertThat(ask("/bookings", "front-office", "front").agentId()).isEqualTo("front");
    }

    @Test
    void noDefaultInTheRequestTheCataloguesAnswers() {
        resolve.defaultAgentId = "default";
        assertThat(ask("/bookings", null, null).agentId()).isEqualTo("default");
        assertThat(ask("/bookings", null, " ").agentId()).isEqualTo("default");
        assertThat(resolve.defaultAgentId()).isEqualTo("default");
    }
}
