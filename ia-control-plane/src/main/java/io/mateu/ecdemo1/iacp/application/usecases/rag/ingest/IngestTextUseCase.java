package io.mateu.ecdemo1.iacp.application.usecases.rag.ingest;

import io.mateu.ecdemo1.iacp.application.out.crypto.SecretCipher;
import io.mateu.ecdemo1.iacp.application.out.rag.RagStore;
import io.mateu.ecdemo1.iacp.application.out.repository.LlmRepository;
import io.mateu.ecdemo1.iacp.application.out.repository.RagRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Puts text into a RAG source, from the control console.
 *
 * <p>Deliberately the smallest thing that makes the catalogue demonstrable rather than a document
 * pipeline: paste text, it is split, embedded and stored. There is no crawler, no file upload, no
 * incremental sync and no way to remove what was added except through the store itself — a source
 * that needs any of those is a source whose content is loaded by something else, and the catalogue
 * exists precisely so that this service does not have to be that something.
 *
 * <p>It is also not idempotent: ingesting the same text twice stores it twice. Given that, it is
 * an admin action on an admin console rather than anything a running service can call — there is
 * no endpoint for it.
 */
@Service
@RequiredArgsConstructor
public class IngestTextUseCase {

    final RagRepository ragRepository;
    final LlmRepository llmRepository;
    final SecretCipher cipher;
    final RagStore store;

    @Transactional(readOnly = true)
    public int handle(IngestTextCommand command) {
        if (command.text() == null || command.text().isBlank()) {
            throw new IllegalArgumentException("There is nothing to ingest.");
        }
        var rag = ragRepository.findById(new RagId(command.ragId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "There is no RAG source with id '" + command.ragId() + "'."));
        var embedding = llmRepository.findById(rag.getEmbeddingLlmId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "RAG source '" + rag.getName() + "' names embedding model '"
                                + rag.getEmbeddingLlmId() + "', which is not in the catalogue."));
        var spec = new RagStore.EmbeddingSpec(embedding.getModel().value(),
                embedding.getBaseUrl(), cipher.decrypt(embedding.getCredential().cipherText()));
        return store.ingest(rag, spec, List.of(command.text()));
    }
}
