package io.mateu.ecdemo1.iacp.application.usecases.agent;

import io.mateu.ecdemo1.iacp.InMemoryRepositories;
import io.mateu.ecdemo1.iacp.domain.aggregates.agent.Agent;
import io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo.AgentId;
import io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo.SystemPrompt;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A resolved configuration names the peers an agent may call, and drops the ones it cannot. */
class ResolveAgentPeersTest {

    final InMemoryRepositories.Agents agents = new InMemoryRepositories.Agents();
    final InMemoryRepositories.Llms llms = new InMemoryRepositories.Llms();
    ResolveAgentConfigUseCase resolve;

    @BeforeEach
    void setUp() {
        llms.save(InMemoryRepositories.usableLlm("llm"));
        resolve = new ResolveAgentConfigUseCase(agents, llms, new InMemoryRepositories.Mcps(),
                new InMemoryRepositories.Rags(), InMemoryRepositories.CIPHER);
        resolve.a2aBaseUrl = "http://ia-agent:8095/";
    }

    Agent agent(String id, String description, String... peers) {
        var agent = Agent.of(new AgentId(id), new Name("Agent " + id), new SystemPrompt("p"), new LlmId("llm"),
                List.of(), List.of(), java.util.Arrays.stream(peers).map(AgentId::new).toList(), description);
        agents.save(agent);
        return agent;
    }

    @Test
    void aPeerIsServedWithItsDescriptionAndItsA2aAddress() {
        agent("a", "Front desk", "b");
        agent("b", "Knows the code mapping");

        var resolved = resolve.handle("a");

        assertThat(resolved.agentDescription()).isEqualTo("Front desk");
        assertThat(resolved.peers()).containsExactly(new ResolveAgentConfigUseCase.ResolvedPeer(
                "b", "Agent b", "Knows the code mapping", "http://ia-agent:8095/a2a/b"));
        assertThat(resolved.warnings()).isEmpty();
    }

    @Test
    void aMissingOrDisabledPeerIsDroppedWithAWarning() {
        agent("a", "Front desk", "gone", "off", "b");
        var off = agent("off", "Disabled one");
        off.update(off.getName(), off.getSystemPrompt(), off.getLlmId(), List.of(), List.of(), List.of(),
                off.getDescription(), Enabled.no());
        agent("b", "Fine");

        var resolved = resolve.handle("a");

        assertThat(resolved.peers()).extracting(ResolveAgentConfigUseCase.ResolvedPeer::id).containsExactly("b");
        assertThat(resolved.warnings()).containsExactly(
                "Peer agent 'gone' is no longer in the catalogue — skipped",
                "Peer agent 'Agent off' is disabled — skipped");
    }

    @Test
    void anAgentWithNoPeersResolvesToAnEmptyList() {
        agent("a", null);
        assertThat(resolve.handle("a").peers()).isEmpty();
        assertThat(resolve.handle("a").guardrails().input()).isEmpty();
    }
}
