package io.mateu.ecdemo1.iacp.application.usecases.rag.search;

import io.mateu.ecdemo1.iacp.application.out.crypto.SecretCipher;
import io.mateu.ecdemo1.iacp.application.out.rag.RagStore;
import io.mateu.ecdemo1.iacp.application.out.repository.LlmRepository;
import io.mateu.ecdemo1.iacp.application.out.repository.RagRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagId;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Retrieval, from the catalogue's point of view: find the source, find the model it was embedded
 * with, decrypt that model's credential, and ask the store.
 *
 * <p>This is the second and last method in the service that decrypts anything — the other is
 * {@code ResolveAgentConfigUseCase}. Unlike that one, what it returns carries no credential at
 * all: passages of text and their scores, and nothing the caller could authenticate with. That is
 * what makes it safe to put on the request path of an agent that is not trusted with the embedding
 * key.
 *
 * <p>Every failure here is a {@link RagStore.UnsupportedStoreException} with a sentence in it,
 * because the caller is a tool call inside a prompt and the sentence is what the model reports
 * back. "Unavailable" would make an agent invent an answer around it; naming the reason is what
 * lets it say what actually happened.
 */
@Service
@RequiredArgsConstructor
public class SearchRagUseCase {

    private static final Logger log = LoggerFactory.getLogger(SearchRagUseCase.class);

    final RagRepository ragRepository;
    final LlmRepository llmRepository;
    final SecretCipher cipher;
    final RagStore store;

    @Transactional(readOnly = true)
    public List<RagStore.Chunk> handle(SearchRagCommand command) {
        var rag = ragRepository.findById(new RagId(command.ragId()))
                .orElseThrow(() -> new RagStore.UnsupportedStoreException(
                        "There is no RAG source with id '" + command.ragId() + "'."));
        if (!rag.isUsable()) {
            throw new RagStore.UnsupportedStoreException(
                    "RAG source '" + rag.getName() + "' is disabled.");
        }
        var embedding = llmRepository.findById(rag.getEmbeddingLlmId())
                .orElseThrow(() -> new RagStore.UnsupportedStoreException(
                        "RAG source '" + rag.getName() + "' names embedding model '"
                                + rag.getEmbeddingLlmId() + "', which is not in the catalogue."));

        var spec = new RagStore.EmbeddingSpec(embedding.getModel().value(),
                embedding.getBaseUrl(), cipher.decrypt(embedding.getCredential().cipherText()));
        var chunks = store.search(rag, spec, command.query(),
                command.topK() == null ? rag.getTopK() : command.topK());
        log.info("RAG search on {} returned {} chunk(s)", rag.getId(), chunks.size());
        return chunks;
    }
}
