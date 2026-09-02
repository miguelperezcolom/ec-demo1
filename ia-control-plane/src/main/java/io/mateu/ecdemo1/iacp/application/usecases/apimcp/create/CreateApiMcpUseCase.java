package io.mateu.ecdemo1.iacp.application.usecases.apimcp.create;

import io.mateu.ecdemo1.iacp.application.out.repository.ApiMcpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.ApiMcp;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiMcpId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Endpoint;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateApiMcpUseCase {

    final ApiMcpRepository repository;

    @Transactional
    public String handle(CreateApiMcpCommand command) {
        var id = new ApiMcpId(command.id());
        if (repository.existsById(id)) {
            throw new IllegalArgumentException(
                    "An API MCP server with id '" + command.id() + "' already exists");
        }
        repository.save(ApiMcp.of(id, new Name(command.name()), command.kind(),
                new Endpoint(command.baseUrl()), new Endpoint(command.specUrl()),
                command.description()));
        return id.value();
    }
}
