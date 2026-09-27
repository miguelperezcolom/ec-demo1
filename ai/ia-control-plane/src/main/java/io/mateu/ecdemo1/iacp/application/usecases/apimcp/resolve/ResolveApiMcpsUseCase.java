package io.mateu.ecdemo1.iacp.application.usecases.apimcp.resolve;

import io.mateu.ecdemo1.iacp.application.out.crypto.SecretCipher;
import io.mateu.ecdemo1.iacp.application.out.repository.ApiMcpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.ApiMcp;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiMcpId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns catalogued APIs into what the pod that serves them as MCP needs to make a call.
 *
 * <p>The second place in this service that decrypts, and it is worth saying why there is a second
 * one rather than an extension of the first. {@link
 * io.mateu.ecdemo1.iacp.application.usecases.agent.ResolveAgentConfigUseCase} resolves what ONE
 * agent may reach; this resolves what ONE API needs to be called, and the two are asked by
 * different services at different moments — the agent on every prompt, the serving pod when it
 * mounts an endpoint. Folding them together would mean an agent's configuration carrying API keys
 * for tools it never uses.
 *
 * <p>What it does NOT carry is the operations' parameters. The catalogue holds the offer — which
 * operations, under which names, described how — and the spec holds the shapes. The serving pod
 * reads the spec itself, with a real parser, because resolving refs, components and schemas is
 * what CALLING an operation needs and this service deliberately does not carry that. See
 * {@code OpenApiSpecReader}, which lists operations here and does no more than list them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResolveApiMcpsUseCase {

    final ApiMcpRepository repository;
    final SecretCipher cipher;

    /**
     * One API with its credential in the clear, and the offer an operator composed on it.
     *
     * <p>{@code secret} is null when the API needs none — a public spec and a public base url is a
     * legitimate entry, and an empty string would be indistinguishable from a key that failed to
     * decrypt.
     */
    public record ResolvedApiMcp(String id, String name, String kind, String baseUrl,
                                 String specUrl, String secret, List<ResolvedTool> tools,
                                 String description) {
    }

    /**
     * {@code operation} is the handle into the spec — {@code GET /bookings/{id}} — and is what the
     * serving pod resolves parameters against. {@code description} is what the model reads.
     */
    public record ResolvedTool(String operation, String toolName, String description,
                               List<String> requiredRoles) {
    }

    public static class ApiMcpNotUsableException extends RuntimeException {
        public ApiMcpNotUsableException(String message) { super(message); }
    }

    /**
     * Every API worth mounting an endpoint for.
     *
     * <p>Drops rather than fails, like the agent's resolution and for the same reason: one entry
     * whose credential cannot be read must not take the other endpoints down with it. What was
     * dropped is logged, because unlike an agent's configuration there is no caller here with a
     * screen to show a warning on.
     */
    @Transactional(readOnly = true)
    public List<ResolvedApiMcp> handle() {
        var resolved = new ArrayList<ResolvedApiMcp>();
        for (var api : repository.findAll()) {
            if (!api.isUsable()) {
                // Not a warning. A catalogued entry with nothing exposed yet is the normal state
                // of a half-composed offer, and saying so on every poll would bury the real ones.
                continue;
            }
            try {
                resolved.add(resolve(api));
            } catch (RuntimeException e) {
                log.error("API MCP server {} could not be resolved and will not be served: {}",
                        api.getId(), e.getMessage());
            }
        }
        return List.copyOf(resolved);
    }

    /** One API by id, refused with a sentence when it is not in a state that can be served. */
    @Transactional(readOnly = true)
    public ResolvedApiMcp handle(String id) {
        var api = repository.findById(new ApiMcpId(id))
                .orElseThrow(() -> new ApiMcpNotUsableException(
                        "No API MCP server with id '" + id + "'"));
        if (!api.getEnabled().value()) {
            throw new ApiMcpNotUsableException("API MCP server '" + id + "' is disabled");
        }
        if (api.getTools().isEmpty()) {
            // The single most likely state of a half-finished entry, and the one an operator
            // would otherwise chase in the serving pod's logs rather than on the screen they
            // composed it on.
            throw new ApiMcpNotUsableException("API MCP server '" + id
                    + "' exposes no tools yet — import its operations and choose some");
        }
        return resolve(api);
    }

    private ResolvedApiMcp resolve(ApiMcp api) {
        return new ResolvedApiMcp(
                api.getId().value(),
                api.getName().value(),
                api.getKind().name(),
                api.getBaseUrl().value(),
                api.getSpecUrl().value(),
                api.getCredential().isSet() ? cipher.decrypt(api.getCredential().cipherText()) : null,
                api.getTools().stream()
                        .map(t -> new ResolvedTool(t.operation(), t.toolName(), t.description(),
                                t.requiredRoles()))
                        .toList(),
                api.getDescription());
    }
}
