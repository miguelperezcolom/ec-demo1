package io.mateu.ecdemo1.iacp.infra.in.rest;

import io.mateu.ecdemo1.iacp.application.usecases.agent.ResolveAgentConfigUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one endpoint that hands out a resolved agent configuration, credential included.
 *
 * <p><strong>This must never be routed through the ingress.</strong> It is deliberately not under
 * {@code /_ia-cp}, which is the Mateu UI's namespace and the only prefix the gateway routes to this
 * service; there is no gateway route to {@code /internal/**} and adding one would publish every
 * API key this deployment holds. The protection is that absence, plus the fact that the Service is
 * cluster-internal — this endpoint authenticates nothing itself, exactly like the users service's
 * gRPC port, and for the same reason it must stay inside the namespace.
 *
 * <p>The {@code /internal} prefix is not decoration: it is what makes a future gateway route
 * obviously wrong to whoever writes it.
 */
@RestController
@RequestMapping("/internal/agents")
@RequiredArgsConstructor
@Slf4j
public class AgentConfigController {

    final ResolveAgentConfigUseCase resolveAgentConfig;

    /** The shape a consumer gets. Same as Resolved, minus nothing — named here so it is greppable. */
    @GetMapping("/{agentId}/config")
    public ResponseEntity<?> config(@PathVariable("agentId") String agentId) {
        try {
            return ResponseEntity.ok(resolveAgentConfig.handle(agentId));
        } catch (ResolveAgentConfigUseCase.AgentNotUsableException e) {
            // 409 rather than 404: the agent may well exist, and the caller's retry should not be
            // "create it" but "someone has to fix the catalogue". The message says which.
            log.warn("Agent config refused for {}: {}", agentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new Problem(agentId, e.getMessage()));
        }
    }

    public record Problem(String agentId, String reason) {}
}
