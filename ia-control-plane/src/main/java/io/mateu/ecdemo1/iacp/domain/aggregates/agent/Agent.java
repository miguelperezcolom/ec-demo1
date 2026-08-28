package io.mateu.ecdemo1.iacp.domain.aggregates.agent;

import io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo.AgentId;
import io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo.SystemPrompt;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmId;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpId;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Time;
import io.mateu.workflow.ddd.AggregateRoot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * What the other three catalogues are for: a prompt, one model, and the tools and sources it may
 * reach. An agent is the only thing here a running service is ever handed.
 *
 * <p>It refers to the other aggregates by id and holds nothing of them. That is the point of the
 * split — an MCP server's URL changes in one place, a model's key is rotated in one place, and
 * every agent composed from them follows without being touched.
 *
 * <p><strong>The references are not validated here, and that is deliberate.</strong> An aggregate
 * cannot see the other catalogues without becoming a query, and a rule enforced at write time
 * would only hold at write time anyway: nothing stops an LLM being disabled, or an MCP server
 * deleted, an hour after an agent was composed from it. So the invariant this design commits to is
 * the one that can actually be kept — {@code resolve} at read time, which drops what is no longer
 * usable and reports what it dropped. Write-time validation is a convenience on top of that (the
 * editor does it, to catch typos), never the guarantee.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Agent extends AggregateRoot {

    AgentId id;
    Name name;
    SystemPrompt systemPrompt;
    LlmId llmId;
    List<McpId> mcpIds;
    List<RagId> ragIds;
    String description;
    Enabled enabled;
    Time created;

    public static Agent of(AgentId id, Name name, SystemPrompt systemPrompt, LlmId llmId,
                           List<McpId> mcpIds, List<RagId> ragIds, String description) {
        var agent = new Agent();
        agent.id = id;
        agent.name = name;
        agent.systemPrompt = systemPrompt;
        agent.llmId = llmId;
        agent.mcpIds = dedupe(mcpIds);
        agent.ragIds = dedupe(ragIds);
        agent.description = description;
        agent.enabled = Enabled.yes();
        agent.created = new Time(LocalDateTime.now());
        return agent;
    }

    public void update(Name name, SystemPrompt systemPrompt, LlmId llmId,
                       List<McpId> mcpIds, List<RagId> ragIds, String description, Enabled enabled) {
        this.name = name;
        this.systemPrompt = systemPrompt;
        this.llmId = llmId;
        this.mcpIds = dedupe(mcpIds);
        this.ragIds = dedupe(ragIds);
        this.description = description;
        this.enabled = enabled;
    }

    /**
     * Same id twice is the same server twice, and an agent handed the same MCP endpoint twice gets
     * every one of its tools twice — which Spring AI rejects outright with "Multiple tools with the
     * same name", failing the whole prompt rather than the duplicate. Cheap to prevent here;
     * confusing to diagnose there.
     */
    private static <T> List<T> dedupe(List<T> ids) {
        return ids == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(ids));
    }

    public boolean isUsable() {
        return enabled.value();
    }
}
