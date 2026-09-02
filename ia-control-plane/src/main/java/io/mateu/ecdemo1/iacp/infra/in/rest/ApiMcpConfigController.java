package io.mateu.ecdemo1.iacp.infra.in.rest;

import io.mateu.ecdemo1.iacp.application.usecases.apimcp.resolve.ResolveApiMcpsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The catalogued APIs, resolved for the pod that serves them as MCP servers.
 *
 * <p><strong>Never routed through the ingress</strong>, exactly like {@link AgentConfigController}
 * and with the same force: what this hands out includes each API's credential in the clear. There
 * is no gateway route to {@code /internal/**}, the Service is cluster-internal, and this endpoint
 * authenticates nothing itself. The {@code /internal} prefix is what makes a future route to it
 * obviously wrong to whoever writes it.
 *
 * <p>A list endpoint and a single-entry one, because the serving pod uses both and they answer
 * different questions. The list is what endpoints exist and is polled; the single entry is asked
 * when a connection arrives for an id, and it says WHY when the answer is no — a disabled entry
 * and an entry with nothing exposed are the two states an operator has to be told apart.
 */
@RestController
@RequestMapping("/internal/api-mcps")
@RequiredArgsConstructor
@Slf4j
public class ApiMcpConfigController {

    final ResolveApiMcpsUseCase resolveApiMcps;

    /** Every API in a state worth mounting an endpoint for. Unusable entries are simply absent. */
    @GetMapping
    public List<ResolveApiMcpsUseCase.ResolvedApiMcp> all() {
        return resolveApiMcps.handle();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> one(@PathVariable("id") String id) {
        try {
            return ResponseEntity.ok(resolveApiMcps.handle(id));
        } catch (ResolveApiMcpsUseCase.ApiMcpNotUsableException e) {
            // 409 and not 404, for the reason the agent's config endpoint gives: the entry
            // probably exists, and the fix is not "create it" but "somebody has to finish it".
            log.warn("API MCP config refused for {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new Problem(id, e.getMessage()));
        }
    }

    /** Deliberately the same shape the agent config endpoint refuses with. */
    public record Problem(String id, String reason) {
    }
}
