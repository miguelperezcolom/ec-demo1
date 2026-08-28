package io.mateu.ecdemo1.iacp.application.usecases.mcp.update;

import io.mateu.ecdemo1.iacp.application.out.repository.McpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.Endpoint;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class UpdateMcpUseCase {

    final McpRepository repository;

    @Transactional
    public void handle(UpdateMcpCommand command) {
        var mcp = repository.findById(new McpId(command.id()))
                .orElseThrow(() -> new IllegalArgumentException("No MCP server with id '" + command.id() + "'"));
        mcp.update(new Name(command.name()), new Endpoint(command.url()), command.transport(),
                Duration.ofSeconds(command.timeoutSeconds() == null ? 60 : command.timeoutSeconds()),
                command.description(), new Enabled(command.enabled()));
        repository.save(mcp);
    }
}
