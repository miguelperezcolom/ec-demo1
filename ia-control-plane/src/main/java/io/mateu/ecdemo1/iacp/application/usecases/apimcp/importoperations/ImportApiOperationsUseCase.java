package io.mateu.ecdemo1.iacp.application.usecases.apimcp.importoperations;

import io.mateu.ecdemo1.iacp.application.out.apispec.ApiOperation;
import io.mateu.ecdemo1.iacp.application.out.apispec.ApiSpecReader;
import io.mateu.ecdemo1.iacp.application.out.repository.ApiMcpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiMcpId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Asks an API what it can do, so an operator can choose from it.
 *
 * <p>Reads and returns; it changes nothing. What gets exposed is a separate decision, taken on the
 * screen with this list in front of it — see ExposeApiToolsUseCase. Importing and exposing in one
 * step would mean re-importing a spec silently re-writes an offer somebody composed by hand.
 */
@Service
@RequiredArgsConstructor
public class ImportApiOperationsUseCase {

    final ApiMcpRepository repository;
    final List<ApiSpecReader> readers;

    @Transactional(readOnly = true)
    public List<ApiOperation> handle(String apiMcpId) {
        var api = repository.findById(new ApiMcpId(apiMcpId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No API MCP server with id '" + apiMcpId + "'"));
        var reader = readers.stream()
                .filter(r -> r.supports(api.getKind()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        // Said out loud rather than returning nothing: an empty list would read as
                        // "this API declares no operations", which is a different and much more
                        // confusing thing than "this kind is not implemented yet".
                        api.getKind() + " descriptions are not read yet — only REST/OpenAPI is. "
                        + "The entry can stay catalogued, and its operations can be imported the "
                        + "day a reader for it exists."));
        return reader.read(api.getSpecUrl().value());
    }
}
