package io.mateu.ecdemo1.iacp.application.usecases.route.update;

import io.mateu.ecdemo1.iacp.application.out.repository.RouteRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.vo.RouteId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateRouteUseCase {

    final RouteRepository repository;

    @Transactional
    public void handle(UpdateRouteCommand command) {
        var route = repository.findById(new RouteId(command.id()))
                .orElseThrow(() -> new IllegalArgumentException("No route with id '" + command.id() + "'"));
        route.update(new Name(command.name()), command.priority(), command.role(), command.tenant(),
                command.locale(), command.routePrefix(), command.targetAgentId(),
                new Enabled(command.enabled()));
        repository.save(route);
    }
}
