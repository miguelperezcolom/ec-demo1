package io.mateu.ecdemo1.iacp.infra.in.ui.pages.agent;

import io.mateu.ecdemo1.iacp.application.out.query.dto.AgentDto;
import io.mateu.ecdemo1.iacp.application.usecases.agent.ResolveAgentConfigUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.agent.create.CreateAgentCommand;
import io.mateu.ecdemo1.iacp.application.usecases.agent.create.CreateAgentUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.agent.update.UpdateAgentCommand;
import io.mateu.ecdemo1.iacp.application.usecases.agent.update.UpdateAgentUseCase;
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

import java.util.Arrays;
import java.util.List;

/**
 * The composition: a prompt, one model, and the servers and sources it may reach.
 *
 * <p>The two id lists are comma-separated text rather than pickers, and that is a real limitation
 * worth stating rather than hiding: a typo here is not refused on save — it becomes a reference
 * that {@code ResolveAgentConfigUseCase} drops at read time with a warning. "Preview resolved
 * configuration" is the button that surfaces that before a user does.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class AgentViewModel implements Identifiable {

    @Section("Agent")
    @ReadOnly
    @HiddenInCreate
    String id;

    @HiddenInList
    @Help("Only used when creating. This is the id a running service asks for — renaming it "
            + "later would take that service's chat panel down.")
    String newId;

    @NotEmpty
    String name;

    @Multiline
    String description;

    @Section("Model")
    @NotEmpty
    @Help("The id of an LLM from the LLM catalogue. Refused on save if it does not exist: "
            + "unlike a missing tool, a missing model leaves nothing to answer with.")
    String llmId;

    @Section("Instructions")
    @NotEmpty
    @Multiline
    @Help("Everything the model is told before anything a user says. It decides whether the "
            + "agent reports a failed tool call or invents an answer around it.")
    String systemPrompt;

    @Section("Tools and sources")
    @Help("Comma-separated MCP server ids. Ones that are missing or disabled are dropped when "
            + "the configuration is served, and reported — they do not stop the agent.")
    String mcpIds;

    @Help("Comma-separated RAG source ids. Same handling as the MCP servers above.")
    String ragIds;

    @Section("Status")
    boolean enabled;

    @ReadOnly
    @HiddenInList
    @Multiline
    @Help("Result of the last preview in this session. Not stored.")
    String lastPreview;

    final CreateAgentUseCase createAgentUseCase;
    final UpdateAgentUseCase updateAgentUseCase;
    final ResolveAgentConfigUseCase resolveAgentConfigUseCase;

    public String create(HttpRequest httpRequest) {
        return createAgentUseCase.handle(new CreateAgentCommand(newId, name, systemPrompt, llmId,
                split(mcpIds), split(ragIds), description));
    }

    public void save(HttpRequest httpRequest) {
        updateAgentUseCase.handle(new UpdateAgentCommand(id, name, systemPrompt, llmId,
                split(mcpIds), split(ragIds), description, enabled));
    }

    /**
     * Resolves this agent exactly as a running service would, and shows what came back — minus the
     * credential, which is the one field the preview must not print. What it is really for is the
     * warnings: a dropped MCP server is invisible in the catalogue and obvious here.
     */
    @Action(idempotent = true)
    public String previewResolvedConfiguration(HttpRequest httpRequest) {
        try {
            var resolved = resolveAgentConfigUseCase.handle(id);
            var sb = new StringBuilder();
            sb.append("LLM: ").append(resolved.llm().name())
                    .append(" (").append(resolved.llm().provider()).append('/')
                    .append(resolved.llm().model()).append(")\n");
            sb.append("MCP servers served: ").append(resolved.mcps().size()).append('\n');
            resolved.mcps().forEach(m -> sb.append("  - ").append(m.name())
                    .append(" ").append(m.url()).append('\n'));
            sb.append("RAG sources served: ").append(resolved.rags().size()).append('\n');
            resolved.rags().forEach(r -> sb.append("  - ").append(r.name())
                    .append(" / ").append(r.collection()).append('\n'));
            if (resolved.warnings().isEmpty()) {
                sb.append("No warnings.");
            } else {
                sb.append("Warnings:\n");
                resolved.warnings().forEach(w -> sb.append("  ! ").append(w).append('\n'));
            }
            lastPreview = sb.toString();
        } catch (ResolveAgentConfigUseCase.AgentNotUsableException e) {
            lastPreview = "Would not be served: " + e.getMessage();
        }
        return lastPreview;
    }

    static List<String> split(String raw) {
        return raw == null || raw.isBlank() ? List.of()
                : Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Override
    public String id() {
        return id;
    }

    public AgentViewModel load(AgentDto dto) {
        id = dto.id();
        newId = dto.id();
        name = dto.name();
        description = dto.description();
        llmId = dto.llmId();
        systemPrompt = dto.systemPrompt();
        mcpIds = String.join(", ", dto.mcpIds());
        ragIds = String.join(", ", dto.ragIds());
        enabled = dto.enabled();
        lastPreview = null;
        return this;
    }

    @Override
    public String toString() {
        return id != null ? name : "New agent";
    }
}
