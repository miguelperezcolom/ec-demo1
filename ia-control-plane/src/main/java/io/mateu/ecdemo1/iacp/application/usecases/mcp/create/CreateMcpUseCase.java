package io.mateu.ecdemo1.iacp.application.usecases.mcp.create;

import io.mateu.ecdemo1.iacp.application.out.repository.McpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.Mcp;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.Endpoint;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CreateMcpUseCase {

    final McpRepository repository;

    @Transactional
    public String handle(CreateMcpCommand command) {
        var id = new McpId(command.id());
        if (repository.existsById(id)) {
            throw new IllegalArgumentException("An MCP server with id '" + command.id() + "' already exists");
        }
        repository.save(Mcp.of(id, new Name(command.name()), new Endpoint(command.url()),
                command.transport(),
                command.timeoutSeconds() == null ? null : Duration.ofSeconds(command.timeoutSeconds()),
                command.description()));
        return id.value();
    }
}
