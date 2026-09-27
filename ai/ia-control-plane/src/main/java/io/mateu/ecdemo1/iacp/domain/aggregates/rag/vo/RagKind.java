package io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo;

/**
 * Which vector store backs a retrieval source.
 *
 * <p>Nothing in this deployment retrieves anything yet — this catalogue is declarative, and its
 * entries describe stores that exist elsewhere. PGVECTOR is the one that would be cheapest to
 * make real, since there is already a PostgreSQL here to put an extension on.
 */
public enum RagKind {
    PGVECTOR,
    QDRANT,
    ELASTICSEARCH
}
