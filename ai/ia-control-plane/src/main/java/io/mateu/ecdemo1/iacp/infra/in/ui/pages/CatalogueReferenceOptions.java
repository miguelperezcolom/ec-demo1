package io.mateu.ecdemo1.iacp.infra.in.ui.pages;

import io.mateu.ecdemo1.iacp.application.out.query.AgentQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.LlmQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.McpQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.RagQueryService;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.Pageable;
import io.mateu.uidl.data.Status;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.LookupLabelSupplier;
import io.mateu.uidl.interfaces.LookupOptionsSupplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

/**
 * The options and labels of the fields that refer to another catalogue — an agent's model, servers
 * and sources, and a route's target agent. One class for all of them because they differ only in
 * which catalogue they read; the field id says which.
 *
 * <p>The model and the target agent are combos, which searches as the user types. The servers and sources are
 * checkboxes, which Mateu renders from a list it is handed with the form rather than one it
 * searches — hence {@link #all}, which the editor serves as an {@code OptionsSupplier}. Both read
 * the same rows, so a label never differs between the two.
 *
 * <p>Entries an agent would not get are still offered, with their state in the label: the
 * catalogue allows composing from a disabled MCP server (it is dropped at read time, see
 * {@code ResolveAgentConfigUseCase}), so hiding it here would make an agent that already refers to
 * one impossible to show truthfully.
 */
@Service
@RequiredArgsConstructor
public class CatalogueReferenceOptions implements LookupOptionsSupplier, LookupLabelSupplier {

    final LlmQueryService llms;
    final McpQueryService mcps;
    final RagQueryService rags;
    final AgentQueryService agents;

    @Override
    public ListingData<Option> search(String fieldId, String searchText, Pageable pageable,
                                      HttpRequest httpRequest) {
        return switch (fieldId) {
            case "llmId" -> options(llms.findAll(searchText, null, pageable),
                    r -> option(r.id(), r.name(), r.provider().toLowerCase() + " · " + r.model(),
                            r.status(), "Usable"));
            case "mcpIds" -> options(mcps.findAll(searchText, null, pageable),
                    r -> option(r.id(), r.name(), r.url(), r.status(), "Enabled"));
            case "ragIds" -> options(rags.findAll(searchText, null, pageable),
                    r -> option(r.id(), r.name(), r.kind().toLowerCase() + " · " + r.collection(),
                            r.status(), "Enabled"));
            case "targetAgentId", "peerAgentIds", "inputGuardrailAgentIds",
                 "outputGuardrailAgentIds" -> options(agents.findAll(searchText, null, pageable),
                    r -> option(r.id(), r.name(), r.llm(), r.status(), "Enabled"));
            default -> throw new IllegalArgumentException("No references for field " + fieldId);
        };
    }

    /** Every entry of the field's catalogue, for the widgets that show them all at once. */
    public List<Option> all(String fieldId, HttpRequest httpRequest) {
        return search(fieldId, null, new Pageable(0, ALL, List.of()), httpRequest).page().content();
    }

    /** More than any catalogue here holds; a picker of this many checkboxes is not a picker. */
    static final int ALL = 200;

    /**
     * A reference to an entry deleted since is shown by its id: the query services answer
     * "Unknown", which would hide exactly the thing the user needs to see to fix it.
     */
    @Override
    public String label(String fieldId, Object id, HttpRequest httpRequest) {
        var label = switch (fieldId) {
            case "llmId" -> llms.getLabel((String) id);
            case "mcpIds" -> mcps.getLabel((String) id);
            case "ragIds" -> rags.getLabel((String) id);
            case "targetAgentId", "peerAgentIds", "inputGuardrailAgentIds",
                 "outputGuardrailAgentIds" -> agents.getLabel((String) id);
            default -> String.valueOf(id);
        };
        return "Unknown".equals(label) ? id + " (not in the catalogue)" : label;
    }

    static Option option(String id, String name, String description, Status status, String fine) {
        var label = fine.equals(status.message()) ? name : name + " (" + status.message() + ")";
        return new Option(id, label, description);
    }

    static <Row> ListingData<Option> options(ListingData<Row> found, Function<Row, Option> toOption) {
        return new ListingData<>(new Page<>(
                found.page().searchSignature(),
                found.page().pageSize(),
                found.page().pageNumber(),
                found.page().totalElements(),
                found.page().content().stream().map(toOption).toList()));
    }
}
