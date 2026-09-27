package io.mateu.ecdemo1.iacp.domain.aggregates.apimcp;

import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiCredential;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiKind;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiMcpId;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ExposedTool;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Endpoint;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Time;
import io.mateu.workflow.ddd.AggregateRoot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An existing API, offered to agents as an MCP server.
 *
 * <p><b>This is not an {@code Mcp}, and the difference is the reason it is its own aggregate.</b>
 * An {@code Mcp} is a server somebody else runs, and it deliberately holds no tool list: the tools
 * are the server's to declare, they change without this catalogue being told, and a copy here would
 * go stale in silence. Here the opposite is true — nothing else knows what this API offers as
 * tools, because the offer is something an operator composes: which operations, under which names,
 * described how, reachable by whom. That list IS the entry. Putting both in one aggregate would
 * break the invariant the other one is built on.
 *
 * <p>The credential belongs to the API, not to a tool: one key opens all the operations this entry
 * exposes. It arrives already encrypted and can only be replaced, never read back — see
 * {@link ApiCredential}.
 *
 * <p>What this aggregate does not do is serve anything. It is a catalogue entry, read by whatever
 * turns it into a running MCP server; keeping the translation out of the control plane is what
 * keeps the control plane off the path every tool call travels.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ApiMcp extends AggregateRoot {

    ApiMcpId id;
    Name name;
    ApiKind kind;
    /** Where calls go. The spec's own server list is advisory; this is the one that is used. */
    Endpoint baseUrl;
    /** Where the OpenAPI document or WSDL is fetched from when operations are imported. */
    Endpoint specUrl;
    ApiCredential credential;
    /** The operations offered as tools. Empty until someone imports and chooses. */
    List<ExposedTool> tools;
    String description;
    Enabled enabled;
    Time created;

    public static ApiMcp of(ApiMcpId id, Name name, ApiKind kind, Endpoint baseUrl,
                            Endpoint specUrl, String description) {
        var api = new ApiMcp();
        api.id = id;
        api.name = name;
        api.kind = kind;
        api.baseUrl = baseUrl;
        api.specUrl = specUrl;
        api.credential = ApiCredential.none();
        api.tools = List.of();
        api.description = description;
        api.enabled = Enabled.yes();
        api.created = new Time(LocalDateTime.now());
        return api;
    }

    /** Everything about an API except its credential and its tools, which change on their own. */
    public void update(Name name, ApiKind kind, Endpoint baseUrl, Endpoint specUrl,
                       String description, Enabled enabled) {
        this.name = name;
        this.kind = kind;
        this.baseUrl = baseUrl;
        this.specUrl = specUrl;
        this.description = description;
        this.enabled = enabled;
    }

    /** Replaces the credential with an already-encrypted one. The only way it ever changes. */
    public void replaceCredential(ApiCredential credential) {
        this.credential = credential;
    }

    /**
     * Replaces the whole offer, because it is one decision rather than several.
     *
     * <p>Choosing which operations to expose, naming them and describing them is a single act by
     * one person looking at one spec; letting tools be added and removed one at a time would invite
     * an entry that is half of an old import and half of a new one, with no way to tell which.
     */
    public void exposeExactly(List<ExposedTool> tools) {
        var chosen = tools == null ? List.<ExposedTool>of() : List.copyOf(tools);
        var names = chosen.stream().map(ExposedTool::toolName).distinct().count();
        if (names != chosen.size()) {
            // A model picks a tool by name. Two with the same one is not a duplicate row, it is an
            // ambiguity the model resolves silently and differently each time.
            throw new IllegalArgumentException("Two tools of this API share a name");
        }
        this.tools = chosen;
    }

    /**
     * Usable means an agent can actually be given these tools: enabled, with something to offer,
     * and with the key that opens it if it needs one.
     */
    public boolean isUsable() {
        return enabled.value() && !tools.isEmpty();
    }
}
