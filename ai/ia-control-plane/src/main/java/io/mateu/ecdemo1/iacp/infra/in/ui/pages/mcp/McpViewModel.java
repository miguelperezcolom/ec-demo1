package io.mateu.ecdemo1.iacp.infra.in.ui.pages.mcp;

import io.mateu.ecdemo1.iacp.application.out.probe.ConnectionProbe;
import io.mateu.ecdemo1.iacp.application.out.query.dto.McpDto;
import io.mateu.ecdemo1.iacp.application.out.repository.McpRepository;
import io.mateu.ecdemo1.iacp.application.usecases.mcp.create.CreateMcpCommand;
import io.mateu.ecdemo1.iacp.application.usecases.mcp.create.CreateMcpUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.mcp.update.UpdateMcpCommand;
import io.mateu.ecdemo1.iacp.application.usecases.mcp.update.UpdateMcpUseCase;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.Mcp;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpId;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpTransport;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.HiddenInList;
import io.mateu.uidl.annotations.Multiline;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype")
@RequiredArgsConstructor
public class McpViewModel implements Identifiable {

    @Section("Server")
    @ReadOnly
    @HiddenInCreate
    String id;

    @HiddenInList
    @Help("Only used when creating. Referenced by agents; cannot be changed afterwards.")
    String newId;

    @NotEmpty
    String name;

    @NotEmpty
    @Help("Absolute http(s) URL of the server itself, without /sse — e.g. http://booking:8108")
    String url;

    @NotEmpty
    McpTransport transport;

    @Help("Seconds a client waits before giving up on this server for one prompt. "
            + "It is not an error when it expires — the agent answers with fewer tools.")
    Long timeoutSeconds;

    @Multiline
    @Help("For whoever reads this catalogue. The tools themselves are not listed here: the "
            + "server declares them at connection time and they change without this being told.")
    String description;

    @Section("Status")
    boolean enabled;

    @ReadOnly
    @HiddenInList
    @Help("Result of the last 'Test connection' in this session. Not stored.")
    String lastProbe;

    final CreateMcpUseCase createMcpUseCase;
    final UpdateMcpUseCase updateMcpUseCase;
    final McpRepository repository;
    final ConnectionProbe<Mcp> probe;

    public String create(HttpRequest httpRequest) {
        return createMcpUseCase.handle(new CreateMcpCommand(newId, name, url, transport,
                timeoutSeconds, description));
    }

    public void save(HttpRequest httpRequest) {
        updateMcpUseCase.handle(new UpdateMcpCommand(id, name, url, transport, timeoutSeconds,
                description, enabled));
    }

    /**
     * Probes what is stored, not what is on screen. An unsaved edit reporting "reachable" about
     * the previous URL is the one result that would actively mislead.
     */
    @Action(idempotent = true)
    public String testConnection(HttpRequest httpRequest) {
        var stored = repository.findById(new McpId(id))
                .orElseThrow(() -> new IllegalStateException("Save this server before probing it"));
        var result = probe.probe(stored);
        lastProbe = (result.reachable() ? "OK — " : "Unreachable — ") + result.detail();
        return lastProbe;
    }

    @Override
    public String id() {
        return id;
    }

    public McpViewModel load(McpDto dto) {
        id = dto.id();
        newId = dto.id();
        name = dto.name();
        url = dto.url();
        transport = dto.transport();
        timeoutSeconds = dto.timeoutSeconds();
        description = dto.description();
        enabled = dto.enabled();
        lastProbe = null;
        return this;
    }

    @Override
    public String toString() {
        return id != null ? name : "New MCP server";
    }
}
