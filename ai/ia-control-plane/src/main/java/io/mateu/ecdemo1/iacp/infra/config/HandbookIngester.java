package io.mateu.ecdemo1.iacp.infra.config;

import io.mateu.ecdemo1.iacp.application.usecases.rag.ingest.IngestTextCommand;
import io.mateu.ecdemo1.iacp.application.usecases.rag.ingest.IngestTextUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the bundled handbook into a RAG source, once, in the background.
 *
 * <p>It exists because of two facts that pull against each other. Ingestion is deliberately an admin
 * action with no endpoint — the catalogue is not a document pipeline — yet a demo should come up
 * with a RAG source that already answers, not an empty one that teaches the model the source is
 * useless. The reconciliation is to ingest a small, bundled corpus at first start, the same moment
 * and under the same "only when empty" guard as the catalogue seed, so it runs exactly once and
 * never re-adds on a restart (ingestion is not idempotent — the same text twice is stored twice).
 *
 * <p><strong>In the background, and patient about it.</strong> The first embedding call reaches the
 * Text Embeddings Inference pod, which may still be pulling its model when the control plane starts,
 * so a synchronous ingest here would either block startup or fail the race. Instead this runs on a
 * daemon thread and retries for a couple of minutes. If it ultimately cannot — TEI never came up —
 * it gives up loudly and leaves the source attached but empty, which an operator can fill from the
 * console; it never takes the control plane down over documents.
 */
@Component
public class HandbookIngester {

    private static final Logger log = LoggerFactory.getLogger(HandbookIngester.class);
    private static final int MAX_ATTEMPTS = 18;
    private static final long RETRY_MILLIS = 10_000;

    private final IngestTextUseCase ingestText;

    public HandbookIngester(IngestTextUseCase ingestText) {
        this.ingestText = ingestText;
    }

    /** Kick off a one-time ingestion of the bundled handbook into {@code ragId}, off the main thread. */
    public void ingestInBackground(String ragId) {
        var docs = loadDocs();
        if (docs.isEmpty()) {
            log.warn("No handbook documents were bundled (classpath handbook/*.md) — nothing to ingest.");
            return;
        }
        var thread = new Thread(() -> ingestWithRetry(ragId, docs), "handbook-ingest");
        thread.setDaemon(true);
        thread.start();
    }

    private void ingestWithRetry(String ragId, List<String> docs) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                int chunks = 0;
                for (var doc : docs) {
                    chunks += ingestText.handle(new IngestTextCommand(ragId, doc));
                }
                log.info("Handbook ingested into RAG source '{}': {} chunk(s) from {} document(s).",
                        ragId, chunks, docs.size());
                return;
            } catch (Exception e) {
                // Almost always TEI not being ready yet on the first attempts. Log at info while
                // retrying so it does not read as an error until it actually gives up.
                log.info("Handbook ingest attempt {}/{} not ready ({}). Retrying in {}s.",
                        attempt, MAX_ATTEMPTS, e.getMessage(), RETRY_MILLIS / 1000);
                try {
                    Thread.sleep(RETRY_MILLIS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.warn("Gave up ingesting the handbook after {} attempts. RAG source '{}' is attached but "
                + "empty — ingest it from the console once the embedding model is reachable.",
                MAX_ATTEMPTS, ragId);
    }

    private List<String> loadDocs() {
        var texts = new ArrayList<String>();
        try {
            var resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:handbook/*.md");
            // Sorted by filename so a numbered corpus ingests in a stable order.
            java.util.Arrays.sort(resources, java.util.Comparator.comparing(Resource::getFilename));
            for (var resource : resources) {
                try (var in = resource.getInputStream()) {
                    texts.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (Exception e) {
            log.warn("Could not read bundled handbook documents: {}", e.toString());
        }
        return texts;
    }
}
