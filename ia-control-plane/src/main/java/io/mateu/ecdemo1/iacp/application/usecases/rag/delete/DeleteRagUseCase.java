package io.mateu.ecdemo1.iacp.application.usecases.rag.delete;

import io.mateu.ecdemo1.iacp.application.out.repository.RagRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Allowed while agents name it, for the same reason as an MCP server's: they degrade, not break. */
@Service
@RequiredArgsConstructor
public class DeleteRagUseCase {

    final RagRepository repository;

    @Transactional
    public void handle(DeleteRagCommand command) {
        repository.deleteAllById(command.ids().stream().map(RagId::new).toList());
    }
}
