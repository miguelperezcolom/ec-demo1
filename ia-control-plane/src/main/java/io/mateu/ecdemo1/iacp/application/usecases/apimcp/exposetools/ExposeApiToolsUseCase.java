package io.mateu.ecdemo1.iacp.application.usecases.apimcp.exposetools;

import io.mateu.ecdemo1.iacp.application.out.repository.ApiMcpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiMcpId;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ExposedTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExposeApiToolsUseCase {

    final ApiMcpRepository repository;

    @Transactional
    public void handle(ExposeApiToolsCommand command) {
        var api = repository.findById(new ApiMcpId(command.id()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No API MCP server with id '" + command.id() + "'"));
        api.exposeExactly(command.tools().stream()
                .map(t -> new ExposedTool(t.operation(), t.toolName(), t.description(),
                        t.requiredRoles()))
                .toList());
        repository.save(api);
    }
}
