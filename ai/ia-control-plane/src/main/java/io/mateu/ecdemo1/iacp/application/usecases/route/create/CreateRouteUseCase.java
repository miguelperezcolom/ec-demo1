package io.mateu.ecdemo1.iacp.application.usecases.route.create;

import io.mateu.ecdemo1.iacp.application.out.repository.RouteRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.Route;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.vo.RouteId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateRouteUseCase {

    final RouteRepository repository;

    @Transactional
    public String handle(CreateRouteCommand command) {
        var id = new RouteId(command.id());
        if (repository.existsById(id)) {
            throw new IllegalArgumentException("A route with id '" + command.id() + "' already exists");
        }
        repository.save(Route.of(id, new Name(command.name()), command.priority(), command.role(),
                command.tenant(), command.locale(), command.routePrefix(), command.targetAgentId()));
        return id.value();
    }
}
