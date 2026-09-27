package io.mateu.ecdemo1.iacp.application.usecases.route.delete;

import io.mateu.ecdemo1.iacp.application.out.repository.RouteRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.vo.RouteId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteRouteUseCase {

    final RouteRepository repository;

    @Transactional
    public void handle(DeleteRouteCommand command) {
        repository.deleteAllById(command.ids().stream().map(RouteId::new).toList());
    }
}
