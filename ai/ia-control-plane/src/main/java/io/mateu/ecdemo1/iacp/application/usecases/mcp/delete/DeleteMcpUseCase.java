package io.mateu.ecdemo1.iacp.application.usecases.mcp.delete;

import io.mateu.ecdemo1.iacp.application.out.repository.McpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unlike an LLM, this one goes through even when agents name it. An agent without one of its MCP
 * servers still answers, with fewer tools, and {@code ResolveAgentConfigUseCase} reports the gap
 * — so refusing here would be stricter than the invariant the design actually keeps.
 */
@Service
@RequiredArgsConstructor
public class DeleteMcpUseCase {

    final McpRepository repository;

    @Transactional
    public void handle(DeleteMcpCommand command) {
        repository.deleteAllById(command.ids().stream().map(McpId::new).toList());
    }
}
