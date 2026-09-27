package io.mateu.ecdemo1.iacp.infra.in.rest;

import io.mateu.ecdemo1.iacp.application.out.rag.RagStore;
import io.mateu.ecdemo1.iacp.application.usecases.rag.search.SearchRagCommand;
import io.mateu.ecdemo1.iacp.application.usecases.rag.search.SearchRagUseCase;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Retrieval, for the agent.
 *
 * <p>The agent turns each RAG source in its configuration into a tool, and the tool calls this.
 * That puts this service on the data path for a lookup, which it is not for anything else — a
 * deliberate trade, and the reasoning is worth stating because it is the sort of thing that looks
 * like a mistake later:
 *
 * <ul>
 *   <li>Retrieval needs the store's connection <em>and</em> the embedding model's credential.
 *       Both are already here. Doing it in the agent instead would mean sending a second
 *       credential to a service that only needs an answer.</li>
 *   <li>Ingestion needs exactly the same two things, and it is unambiguously an admin action.
 *       Splitting the two would put the same pgvector and embedding plumbing in both modules.</li>
 * </ul>
 *
 * <p>What it costs: if this service is down, RAG tools fail. The chat panel does not — the agent
 * caches its configuration and its MCP tools do not come through here. So an outage costs the
 * agent its documents, not its voice.
 *
 * <p>Under {@code /internal} with the configuration endpoint, and under the same rule: no gateway
 * route, ever. This one returns no credential, but it does return the contents of whatever has
 * been ingested, which is not public either.
 */
@RestController
@RequestMapping("/internal/rag")
@RequiredArgsConstructor
public class RagSearchController {

    private static final Logger log = LoggerFactory.getLogger(RagSearchController.class);

    /** Hard ceiling on what one call may pull back, whatever the caller or the catalogue says. */
    private static final int MAX_TOP_K = 20;

    final SearchRagUseCase searchRag;

    public record SearchRequest(String query, Integer topK) {}

    public record Passage(String text, double score, String source) {}

    public record SearchResponse(String ragId, int count, List<Passage> passages) {}

    public record Problem(String ragId, String reason) {}

    @PostMapping("/{ragId}/search")
    public ResponseEntity<?> search(@PathVariable("ragId") String ragId,
                                    @RequestBody SearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().body(new Problem(ragId, "A query is required."));
        }
        try {
            var chunks = searchRag.handle(new SearchRagCommand(ragId, request.query(),
                    request.topK() == null ? null : Math.min(request.topK(), MAX_TOP_K)));
            return ResponseEntity.ok(new SearchResponse(ragId, chunks.size(),
                    chunks.stream()
                            .map(c -> new Passage(c.text(), c.score(), c.source()))
                            .toList()));
        } catch (RagStore.UnsupportedStoreException e) {
            // 409, like the config endpoint's refusal, and for the same reason: the caller
            // retrying will not help, and the message names what a person has to change. It
            // reaches the model as a tool failure, which its instructions tell it to report.
            log.warn("RAG search refused for {}: {}", ragId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new Problem(ragId, e.getMessage()));
        }
    }
}
