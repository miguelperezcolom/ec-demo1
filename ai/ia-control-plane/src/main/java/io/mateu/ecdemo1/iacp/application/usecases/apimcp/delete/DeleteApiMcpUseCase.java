package io.mateu.ecdemo1.iacp.application.usecases.apimcp.delete;

import io.mateu.ecdemo1.iacp.application.out.repository.ApiMcpRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiMcpId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteApiMcpUseCase {

    final ApiMcpRepository repository;

    @Transactional
    public void handle(DeleteApiMcpCommand command) {
        repository.deleteAllById(command.ids().stream().map(ApiMcpId::new).toList());
    }
}
