package io.mateu.ecdemo1.iacp.infra.out.persistence;

import io.mateu.ecdemo1.iacp.application.out.repository.RouteRepository;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.Route;
import io.mateu.ecdemo1.iacp.domain.aggregates.route.vo.RouteId;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Enabled;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Name;
import io.mateu.ecdemo1.iacp.domain.aggregates.shared.vo.Time;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RouteDBRepository implements RouteRepository {

    final RouteEntityRepository entities;

    @Override
    public Route save(Route route) {
        var e = entities.findById(route.getId().value()).orElseGet(RouteEntity::new);
        e.setId(route.getId().value());
        e.setName(route.getName().value());
        e.setPriority(route.getPriority());
        e.setRole(route.getRole());
        e.setTenant(route.getTenant());
        e.setLocale(route.getLocale());
        e.setRoutePrefix(route.getRoutePrefix());
        e.setTargetAgentId(route.getTargetAgentId());
        e.setEnabled(route.getEnabled().value());
        e.setCreated(route.getCreated().value());
        entities.save(e);
        return route;
    }

    @Override
    public Optional<Route> findById(RouteId id) {
        return entities.findById(id.value()).map(RouteDBRepository::toDomain);
    }

    @Override
    public List<Route> findAll() {
        return entities.findAll().stream().map(RouteDBRepository::toDomain).toList();
    }

    @Override
    public List<Route> findEnabledOrderedByPriority() {
        return entities.findByEnabledTrueOrderByPriorityAsc().stream()
                .map(RouteDBRepository::toDomain).toList();
    }

    @Override
    public void deleteAllById(List<RouteId> ids) {
        entities.deleteAllById(ids.stream().map(RouteId::value).toList());
    }

    @Override
    public boolean existsById(RouteId id) {
        return entities.existsById(id.value());
    }

    static Route toDomain(RouteEntity e) {
        return new Route(
                new RouteId(e.getId()),
                new Name(e.getName()),
                e.getPriority(),
                e.getRole(),
                e.getTenant(),
                e.getLocale(),
                e.getRoutePrefix(),
                e.getTargetAgentId(),
                new Enabled(e.isEnabled()),
                new Time(e.getCreated()));
    }
}
