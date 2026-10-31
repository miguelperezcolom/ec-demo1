package io.mateu.ecdemo1.contenthub.application.visibility;

import io.mateu.ecdemo1.contenthub.application.out.ContentIndex;
import io.mateu.ecdemo1.contenthub.application.out.Embeddings;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * The RAG's retrieval: the passages most like a question, among what the reader may see. Also the job
 * that embeds new passages — an item is visible before its passages are embedded; it just is not found by
 * the chat until they are.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentQuestions {

    final ContentIndex index;
    final Embeddings embeddings;
    final Projection projection;
    final Clock clock;

    public List<ContentIndex.Passage> passagesFor(EmployeeAttributes reader, String question, int limit) {
        if (!index.knowsProfile(reader.hash())) {
            projection.materialise(reader);
        }
        var vector = embeddings.embed(List.of(question), true).getFirst();
        return index.similarVisiblePassages(reader.hash(), reader.language(), clock.instant(), vector, limit);
    }

    /** @return how many passages were embedded */
    public int embedPending(int batch) {
        var pending = index.passagesToEmbed(batch);
        if (pending.isEmpty()) {
            return 0;
        }
        var vectors = embeddings.embed(pending.stream().map(ContentIndex.Passage::text).toList(), false);
        for (int i = 0; i < pending.size(); i++) {
            index.saveEmbedding(pending.get(i), vectors.get(i));
        }
        return pending.size();
    }
}
