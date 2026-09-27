package io.mateu.ecdemo1.iacp.application.usecases.rag.create;

import io.mateu.ecdemo1.iacp.application.out.repository.LlmRepository;
import io.mateu.ecdemo1.iacp.application.out.repository.RagRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmId;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.Rag;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateRagUseCase {

    final RagRepository repository;
    final LlmRepository llmRepository;

    @Transactional
    public String handle(CreateRagCommand command) {
        var id = new RagId(command.id());
        if (repository.existsById(id)) {
            throw new IllegalArgumentException("A RAG source with id '" + command.id() + "' already exists");
        }
        var embeddingLlmId = new LlmId(command.embeddingLlmId());
        // Checked here as a convenience, to catch a typo while the operator is looking at the
        // form. It is not the guarantee — that one is at resolve time, because an LLM can be
        // deleted after this passes.
        if (!llmRepository.existsById(embeddingLlmId)) {
            throw new IllegalArgumentException("No LLM with id '" + command.embeddingLlmId() + "' to embed with");
        }
        repository.save(Rag.of(id, new Name(command.name()), command.kind(),
                command.connectionUrl(), command.collection(), embeddingLlmId,
                command.topK() == null ? 5 : command.topK(), command.description()));
        return id.value();
    }
}
