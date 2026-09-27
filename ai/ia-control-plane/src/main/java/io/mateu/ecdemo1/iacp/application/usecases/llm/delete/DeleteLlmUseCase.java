package io.mateu.ecdemo1.iacp.application.usecases.llm.delete;

import io.mateu.ecdemo1.iacp.application.out.repository.AgentRepository;
import io.mateu.ecdemo1.iacp.application.out.repository.LlmRepository;
import io.mateu.ecdemo1.iacp.application.out.repository.RagRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.llm.vo.LlmId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting an LLM that an agent is composed from would leave that agent unresolvable — not
 * degraded, unresolvable, because there is no model to fall back to. So this one refuses and names
 * the agents, where the MCP and RAG deletions merely warn at resolve time.
 *
 * <p>Disabling is the reversible alternative, and it is what an operator almost always wants.
 */
@Service
@RequiredArgsConstructor
public class DeleteLlmUseCase {

    final LlmRepository repository;
    final AgentRepository agentRepository;
    final RagRepository ragRepository;

    @Transactional
    public void handle(DeleteLlmCommand command) {
        var ids = command.ids().stream().map(LlmId::new).toList();
        for (var id : ids) {
            var usedByAgents = agentRepository.findAll().stream()
                    .filter(agent -> id.equals(agent.getLlmId()))
                    .map(agent -> agent.getName().value())
                    .toList();
            if (!usedByAgents.isEmpty()) {
                throw new IllegalStateException("LLM '" + id.value() + "' is the model of "
                        + String.join(", ", usedByAgents) + ". Disable it instead, or point those agents elsewhere first.");
            }
            var usedByRags = ragRepository.findAll().stream()
                    .filter(rag -> id.equals(rag.getEmbeddingLlmId()))
                    .map(rag -> rag.getName().value())
                    .toList();
            if (!usedByRags.isEmpty()) {
                throw new IllegalStateException("LLM '" + id.value() + "' is the embedding model of "
                        + String.join(", ", usedByRags) + ". A collection cannot be queried by a different model.");
            }
        }
        repository.deleteAllById(ids);
    }
}
