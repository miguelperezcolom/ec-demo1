package io.mateu.ecdemo1.iacp.application.out.rag;

import io.mateu.ecdemo1.iacp.domain.aggregates.rag.Rag;

import java.util.List;

/**
 * Reading from and writing to whatever backs a RAG source.
 *
 * <p>A port, so the use cases never name pgvector. The implementation decides what a
 * {@link Rag}'s {@code kind} and {@code connectionUrl} mean, and is entitled to refuse a kind it
 * cannot serve — the catalogue is allowed to hold entries for stores nothing here can talk to yet,
 * and that refusal has to be an answer rather than a startup failure.
 *
 * <p>Both methods take the embedding credential as a parameter rather than reading it. Only one
 * place in this service decrypts anything, and it is not here.
 */
public interface RagStore {

    /** One retrieved passage and how close it was. */
    record Chunk(String text, double score, String source) {}

    /**
     * Everything needed to embed, resolved from the catalogue's embedding LLM.
     *
     * <p>A record rather than three parameters because the three always travel together and
     * always come from the same place — and because {@code baseUrl} is easy to forget, which is
     * exactly what happened the first time this was written: an OpenAI-compatible endpoint was
     * catalogued with a base URL and embedded against api.openai.com anyway.
     *
     * @param apiKey already decrypted; the store never sees ciphertext
     */
    record EmbeddingSpec(String model, String baseUrl, String apiKey) {}

    List<Chunk> search(Rag rag, EmbeddingSpec embedding, String query, int topK);

    /**
     * Embeds and stores. Returns how many chunks were written, which is not the number of texts
     * given: a long text is split.
     */
    int ingest(Rag rag, EmbeddingSpec embedding, List<String> texts);

    /** Raised for a kind, a URL or a store this implementation cannot serve. */
    class UnsupportedStoreException extends RuntimeException {
        public UnsupportedStoreException(String message) { super(message); }
        public UnsupportedStoreException(String message, Throwable cause) { super(message, cause); }
    }
}
