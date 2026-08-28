package io.mateu.ecdemo1.iacp.domain.aggregates.mcp;

import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.Endpoint;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpId;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpTransport;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Time;
import io.mateu.workflow.ddd.AggregateRoot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * An MCP server an agent may be given the tools of.
 *
 * <p>What it does <em>not</em> hold is which tools those are. That is the server's to declare and
 * it changes without this catalogue being told: the agent asks at connection time and gets
 * whatever is there. Recording a tool list here would be recording a copy that goes stale
 * silently, which is worse than not having one.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Mcp extends AggregateRoot {

    McpId id;
    Name name;
    Endpoint endpoint;
    McpTransport transport;
    /** How long a client waits for this server before giving up on it for that prompt. */
    Duration timeout;
    String description;
    Enabled enabled;
    Time created;

    public static Mcp of(McpId id, Name name, Endpoint endpoint, McpTransport transport,
                         Duration timeout, String description) {
        var mcp = new Mcp();
        mcp.id = id;
        mcp.name = name;
        mcp.endpoint = endpoint;
        mcp.transport = transport;
        mcp.timeout = timeout != null ? timeout : Duration.ofSeconds(60);
        mcp.description = description;
        mcp.enabled = Enabled.yes();
        mcp.created = new Time(LocalDateTime.now());
        return mcp;
    }

    public void update(Name name, Endpoint endpoint, McpTransport transport,
                       Duration timeout, String description, Enabled enabled) {
        this.name = name;
        this.endpoint = endpoint;
        this.transport = transport;
        this.timeout = timeout;
        this.description = description;
        this.enabled = enabled;
    }

    public boolean isUsable() {
        return enabled.value();
    }
}
