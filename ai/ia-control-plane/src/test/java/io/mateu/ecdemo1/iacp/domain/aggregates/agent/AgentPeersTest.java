package io.mateu.ecdemo1.iacp.domain.aggregates.agent;

import io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo.AgentId;
import io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo.SystemPrompt;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The agents an agent may call over A2A: never itself, and each once. */
class AgentPeersTest {

    static Agent agent(String id, List<AgentId> peers) {
        return Agent.of(new AgentId(id), new Name(id), new SystemPrompt("prompt"), new LlmId("llm"),
                List.of(), List.of(), peers, "desc");
    }

    @Test
    void anAgentKeepsItsPeersInOrderAndOnce() {
        var a = agent("a", List.of(new AgentId("b"), new AgentId("c"), new AgentId("b")));
        assertThat(a.getPeerAgentIds()).containsExactly(new AgentId("b"), new AgentId("c"));
    }

    @Test
    void noPeersIsAnEmptyListNotNull() {
        assertThat(agent("a", null).getPeerAgentIds()).isEmpty();
    }

    @Test
    void anAgentCannotBeItsOwnPeerWhenCreated() {
        assertThatThrownBy(() -> agent("a", List.of(new AgentId("b"), new AgentId("a"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot call itself");
    }

    @Test
    void norWhenUpdated() {
        var a = agent("a", List.of());
        assertThatThrownBy(() -> a.update(new Name("a"), new SystemPrompt("p"), new LlmId("llm"),
                List.of(), List.of(), List.of(new AgentId("a")), "d", Enabled.yes()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(a.getPeerAgentIds()).isEmpty();
    }
}
