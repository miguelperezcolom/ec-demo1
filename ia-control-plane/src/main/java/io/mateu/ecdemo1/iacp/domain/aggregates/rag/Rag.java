package io.mateu.ecdemo1.iacp.domain.aggregates.rag;

import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmId;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagId;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagKind;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Time;
import io.mateu.workflow.ddd.AggregateRoot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A retrieval source: a vector store, a collection inside it, and the model that embedded it.
 *
 * <p><strong>Declarative.</strong> Nothing in this deployment retrieves anything yet. These
 * entries describe stores that live elsewhere, so that agents can be composed against them and the
 * retrieval pipeline can be built later without re-cataloguing. That means an entry here is not
 * evidence the store exists — which is what the probe is for.
 *
 * <p>The embedding model is an {@link LlmId} into the same catalogue, and it is not decoration:
 * a query has to be embedded by the model the collection was embedded with, or the vectors are in
 * different spaces and the retrieval returns confident nonsense rather than an error.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Rag extends AggregateRoot {

    RagId id;
    Name name;
    RagKind kind;
    String connectionUrl;
    String collection;
    LlmId embeddingLlmId;
    int topK;
    String description;
    Enabled enabled;
    Time created;

    public static Rag of(RagId id, Name name, RagKind kind, String connectionUrl,
                         String collection, LlmId embeddingLlmId, int topK, String description) {
        var rag = new Rag();
        rag.id = id;
        rag.name = name;
        rag.kind = kind;
        rag.connectionUrl = connectionUrl;
        rag.collection = collection;
        rag.embeddingLlmId = embeddingLlmId;
        rag.topK = topK > 0 ? topK : 5;
        rag.description = description;
        rag.enabled = Enabled.yes();
        rag.created = new Time(LocalDateTime.now());
        return rag;
    }

    public void update(Name name, RagKind kind, String connectionUrl, String collection,
                       LlmId embeddingLlmId, int topK, String description, Enabled enabled) {
        this.name = name;
        this.kind = kind;
        this.connectionUrl = connectionUrl;
        this.collection = collection;
        this.embeddingLlmId = embeddingLlmId;
        this.topK = topK;
        this.description = description;
        this.enabled = enabled;
    }

    public boolean isUsable() {
        return enabled.value();
    }
}
