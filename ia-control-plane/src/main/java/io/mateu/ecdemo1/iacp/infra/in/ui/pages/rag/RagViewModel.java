package io.mateu.ecdemo1.iacp.infra.in.ui.pages.rag;

import io.mateu.ecdemo1.iacp.application.out.probe.ConnectionProbe;
import io.mateu.ecdemo1.iacp.application.out.query.dto.RagDto;
import io.mateu.ecdemo1.iacp.application.out.repository.RagRepository;
import io.mateu.ecdemo1.iacp.application.usecases.rag.create.CreateRagCommand;
import io.mateu.ecdemo1.iacp.application.usecases.rag.create.CreateRagUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.rag.ingest.IngestTextCommand;
import io.mateu.ecdemo1.iacp.application.usecases.rag.ingest.IngestTextUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.rag.search.SearchRagCommand;
import io.mateu.ecdemo1.iacp.application.usecases.rag.search.SearchRagUseCase;
import io.mateu.ecdemo1.iacp.application.usecases.rag.update.UpdateRagCommand;
import io.mateu.ecdemo1.iacp.application.usecases.rag.update.UpdateRagUseCase;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.Rag;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagId;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagKind;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.HiddenInList;
import io.mateu.uidl.annotations.Multiline;
import io.mateu.uidl.annotations.Notice;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.Identifiable;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

/**
 * A retrieval source, and the two things you can do to one from here.
 *
 * <p>The entry itself stays declarative — a source describes a store that lives elsewhere, and
 * saving does not verify it. What is no longer declarative is the store: <em>Ingest text</em>
 * writes into it and <em>Try a query</em> reads from it, both through the same pgvector and
 * embedding path an agent's tool call takes. So this editor is where you find out that a source
 * works, before an agent finds out that it does not.
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class RagViewModel implements Identifiable {

    @Section("Source")
    @ReadOnly
    @HiddenInCreate
    String id;

    @HiddenInList
    @Help("Only used when creating. Referenced by agents; cannot be changed afterwards.")
    String newId;

    @NotEmpty
    String name;

    @NotEmpty
    RagKind kind;

    @NotEmpty
    @Help("Where the store is. May carry credentials in its userinfo — it is stored as typed "
            + "and is not encrypted, unlike an LLM's key.")
    String connectionUrl;

    @NotEmpty
    @Help("The collection, index or table inside that store.")
    String collection;

    @Section("Embedding")
    @NotEmpty
    @Help("The id of an LLM from the LLM catalogue. It must be the model the collection was "
            + "embedded with: a different one puts the query in a different vector space, and "
            + "retrieval then returns confident nonsense rather than an error.")
    String embeddingLlmId;

    @Help("How many chunks a query retrieves.")
    Integer topK;

    @Section("Notes")
    @Multiline
    @Help("Shown to the model as the tool's description. It is what tells it when this source is "
            + "worth searching, so write it for a reader who has to decide that in one line.")
    String description;

    @Section("Content")
    @Multiline
    @Help("Paste text and use 'Ingest text'. It is split, embedded and stored. Not idempotent — "
            + "the same text twice is stored twice — and there is no way to remove it from here.")
    String textToIngest;

    @Multiline
    @Help("Ask this source something and see what comes back, exactly as an agent's tool would.")
    String testQuery;

    @Section("Status")
    boolean enabled;

    @ReadOnly
    @HiddenInList
    @Help("Result of the last 'Test connection' in this session. Not stored.")
    String lastProbe;

    final CreateRagUseCase createRagUseCase;
    final UpdateRagUseCase updateRagUseCase;
    final IngestTextUseCase ingestTextUseCase;
    final SearchRagUseCase searchRagUseCase;
    final RagRepository repository;
    final ConnectionProbe<Rag> probe;

    public String create(HttpRequest httpRequest) {
        return createRagUseCase.handle(new CreateRagCommand(newId, name, kind, connectionUrl,
                collection, embeddingLlmId, topK, description));
    }

    public void save(HttpRequest httpRequest) {
        updateRagUseCase.handle(new UpdateRagCommand(id, name, kind, connectionUrl, collection,
                embeddingLlmId, topK, description, enabled));
    }

    @Action(idempotent = true)
    public String testConnection(HttpRequest httpRequest) {
        var stored = repository.findById(new RagId(id))
                .orElseThrow(() -> new IllegalStateException("Save this source before probing it"));
        var result = probe.probe(stored);
        lastProbe = (result.reachable() ? "OK — " : "Unreachable — ") + result.detail();
        return lastProbe;
    }

    @Action(confirmationRequired = true,
            confirmationTitle = "Ingest this text?",
            confirmationMessage = "It is embedded and stored. Ingesting the same text twice "
                    + "stores it twice, and nothing here can take it out again.")
    public String ingestText(HttpRequest httpRequest) {
        int chunks = ingestTextUseCase.handle(new IngestTextCommand(id, textToIngest));
        textToIngest = null;
        lastProbe = "Ingested " + chunks + " chunk(s).";
        return lastProbe;
    }

    @Action(idempotent = true)
    public String tryAQuery(HttpRequest httpRequest) {
        var passages = searchRagUseCase.handle(new SearchRagCommand(id, testQuery, null));
        if (passages.isEmpty()) {
            // Not an error, and the distinction matters: an empty collection and a query that
            // matches nothing look identical from here, and both are worth saying out loud
            // rather than showing as a blank box.
            lastProbe = "The store answered, and nothing matched. Either this collection is "
                    + "empty or the query is far from everything in it.";
            return lastProbe;
        }
        var sb = new StringBuilder(passages.size() + " passage(s):\n");
        passages.forEach(p -> sb.append(String.format("  [%.3f] %s%n", p.score(),
                p.text().length() > 200 ? p.text().substring(0, 200) + "…" : p.text())));
        lastProbe = sb.toString();
        return lastProbe;
    }

    @Override
    public String id() {
        return id;
    }

    public RagViewModel load(RagDto dto) {
        id = dto.id();
        newId = dto.id();
        name = dto.name();
        kind = dto.kind();
        connectionUrl = dto.connectionUrl();
        collection = dto.collection();
        embeddingLlmId = dto.embeddingLlmId();
        topK = dto.topK();
        description = dto.description();
        enabled = dto.enabled();
        textToIngest = null;
        testQuery = null;
        lastProbe = null;
        return this;
    }

    @Override
    public String toString() {
        return id != null ? name : "New RAG source";
    }
}
