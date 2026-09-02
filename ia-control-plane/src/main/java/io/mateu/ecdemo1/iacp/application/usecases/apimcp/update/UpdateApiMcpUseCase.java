package io.mateu.ecdemo1.iacp.application.usecases.apimcp.update;

import io.mateu.ecdemo1.iacp.application.out.repository.ApiMcpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiMcpId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Endpoint;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateApiMcpUseCase {

    final ApiMcpRepository repository;

    @Transactional
    public void handle(UpdateApiMcpCommand command) {
        var api = repository.findById(new ApiMcpId(command.id()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No API MCP server with id '" + command.id() + "'"));
        api.update(new Name(command.name()), command.kind(), new Endpoint(command.baseUrl()),
                new Endpoint(command.specUrl()), command.description(),
                new Enabled(command.enabled()));
        repository.save(api);
    }
}
