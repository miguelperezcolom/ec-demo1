package io.mateu.ecdemo1.iacp.application.usecases.llm.replacecredential;

import io.mateu.ecdemo1.iacp.application.out.crypto.SecretCipher;
import io.mateu.ecdemo1.iacp.application.out.repository.LlmRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.Credential;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way an LLM's credential ever changes.
 *
 * <p>Separate from the update path so that saving the edit form cannot touch it, and clearing it
 * has to be asked for explicitly rather than being what happens when a field arrives empty.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReplaceLlmCredentialUseCase {

    final LlmRepository repository;
    final SecretCipher cipher;

    @Transactional
    public void handle(ReplaceLlmCredentialCommand command) {
        var llm = repository.findById(new LlmId(command.id()))
                .orElseThrow(() -> new IllegalArgumentException("No LLM with id '" + command.id() + "'"));
        var key = command.apiKey();
        llm.replaceCredential(key == null || key.isBlank()
                ? Credential.none()
                : new Credential(cipher.encrypt(key)));
        repository.save(llm);
        // Worth a line in the log — it changes what this deployment spends money with — and the
        // line must say only that it happened. Never a prefix, never a length.
        log.info("Credential {} for LLM {}", key == null || key.isBlank() ? "cleared" : "replaced",
                command.id());
    }
}
