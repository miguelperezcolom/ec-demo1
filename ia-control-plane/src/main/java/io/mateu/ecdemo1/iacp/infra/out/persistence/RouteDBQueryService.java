package io.mateu.ecdemo1.iacp.infra.out.persistence;

import io.mateu.ecdemo1.iacp.application.out.query.RouteQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.RouteDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.RouteRow;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RouteDBQueryService implements RouteQueryService {

    final RouteEntityRepository entities;

    @Override
    public ListingData<RouteRow> findAll(String searchText, Object filters, Pageable pageable) {
        var springPageable = org.springframework.data.domain.Pageable
                .ofSize(pageable.size()).withPage(pageable.page());
        var page = searchText == null || searchText.isBlank()
                ? entities.findAll(springPageable)
                : entities.findByNameContainingIgnoreCase(searchText, springPageable);
        return new ListingData<>(new Page<>(searchText, page.getSize(), page.getNumber(),
                page.getTotalElements(),
                page.getContent().stream().map(RouteDBQueryService::toRow).toList()));
    }

    @Override
    public String getLabel(String id) {
        return entities.findById(id).map(RouteEntity::getName).orElse("Unknown");
    }

    @Override
    public Optional<RouteDto> getById(String id) {
        return entities.findById(id).map(RouteDBQueryService::toDto);
    }

    @Override
    public List<RouteDto> all() {
        return entities.findAll().stream().map(RouteDBQueryService::toDto).toList();
    }

    @Override
    public long count() {
        return entities.count();
    }

    @Override
    public long countEnabled() {
        return entities.countByEnabledTrue();
    }

    static RouteDto toDto(RouteEntity e) {
        return new RouteDto(e.getId(), e.getName(), e.getPriority(), e.getRole(), e.getTenant(),
                e.getLocale(), e.getRoutePrefix(), e.getTargetAgentId(), e.isEnabled(), e.getCreated());
    }

    static RouteRow toRow(RouteEntity e) {
        return new RouteRow(e.getId(), e.getName(), e.getPriority(), e.getTargetAgentId(),
                e.isEnabled() ? "enabled" : "disabled");
    }
}
