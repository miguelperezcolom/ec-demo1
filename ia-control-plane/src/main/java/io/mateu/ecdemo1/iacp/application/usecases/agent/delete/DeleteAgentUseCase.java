package io.mateu.ecdemo1.iacp.application.usecases.agent.delete;

import io.mateu.ecdemo1.iacp.application.out.repository.AgentRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.agent.vo.AgentId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nothing in this service refers to an agent, so there is no reference to check. What refers to
 * one is outside: a running service asks for an agent by id, and deleting the one it asks for
 * takes its chat panel down until someone recreates it under the same id. That is not enforceable
 * from here — this service does not know who is asking — so it is a thing to know rather than a
 * rule. Disabling has the same effect and is reversible.
 */
@Service
@RequiredArgsConstructor
public class DeleteAgentUseCase {

    final AgentRepository repository;

    @Transactional
    public void handle(DeleteAgentCommand command) {
        repository.deleteAllById(command.ids().stream().map(AgentId::new).toList());
    }
}
