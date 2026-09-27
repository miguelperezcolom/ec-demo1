package io.mateu.ecdemo1.iacp.application.usecases.rag.update;

import io.mateu.ecdemo1.iacp.application.out.repository.LlmRepository;
import io.mateu.ecdemo1.iacp.application.out.repository.RagRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmId;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateRagUseCase {

    final RagRepository repository;
    final LlmRepository llmRepository;

    @Transactional
    public void handle(UpdateRagCommand command) {
        var rag = repository.findById(new RagId(command.id()))
                .orElseThrow(() -> new IllegalArgumentException("No RAG source with id '" + command.id() + "'"));
        var embeddingLlmId = new LlmId(command.embeddingLlmId());
        if (!llmRepository.existsById(embeddingLlmId)) {
            throw new IllegalArgumentException("No LLM with id '" + command.embeddingLlmId() + "' to embed with");
        }
        rag.update(new Name(command.name()), command.kind(), command.connectionUrl(),
                command.collection(), embeddingLlmId,
                command.topK() == null ? 5 : command.topK(), command.description(),
                new Enabled(command.enabled()));
        repository.save(rag);
    }
}
