package io.mateu.ecdemo1.contenthub.application.out;

import java.util.List;

/** Turns text into vectors for the RAG (the cluster's embeddings service). */
public interface Embeddings {

    /** @param query true for a question, false for a passage — e5 models embed them differently */
    List<float[]> embed(List<String> texts, boolean query);
}
