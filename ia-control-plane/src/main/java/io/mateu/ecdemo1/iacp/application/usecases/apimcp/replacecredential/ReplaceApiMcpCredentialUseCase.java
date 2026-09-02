package io.mateu.ecdemo1.iacp.application.usecases.apimcp.replacecredential;

import io.mateu.ecdemo1.iacp.application.out.crypto.SecretCipher;
import io.mateu.ecdemo1.iacp.application.out.repository.ApiMcpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiCredential;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiMcpId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way an API's credential ever changes.
 *
 * <p>Separate from the update path for the same reason the LLM's is: saving the edit form must not
 * be able to touch it, and clearing it has to be asked for rather than being what happens when a
 * field arrives empty.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReplaceApiMcpCredentialUseCase {

    final ApiMcpRepository repository;
    final SecretCipher cipher;

    @Transactional
    public void handle(ReplaceApiMcpCredentialCommand command) {
        var api = repository.findById(new ApiMcpId(command.id()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No API MCP server with id '" + command.id() + "'"));
        var secret = command.secret();
        api.replaceCredential(secret == null || secret.isBlank()
                ? ApiCredential.none()
                : new ApiCredential(cipher.encrypt(secret)));
        repository.save(api);
        // That it happened, and nothing else about it. Never a prefix, never a length.
        log.info("Credential replaced for API MCP server {}", command.id());
    }
}
