package io.mateu.ecdemo1.iacp.infra.out.persistence;

import io.mateu.ecdemo1.iacp.application.out.query.McpQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.McpDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.McpRow;
import io.mateu.ecdemo1.iacp.domain.aggregates.mcp.vo.McpTransport;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class McpDBQueryService implements McpQueryService {

    final McpEntityRepository entities;

    @Override
    public ListingData<McpRow> findAll(String searchText, Object filters, Pageable pageable) {
        var springPageable = org.springframework.data.domain.Pageable
                .ofSize(pageable.size()).withPage(pageable.page());
        var page = searchText == null || searchText.isBlank()
                ? entities.findAll(springPageable)
                : entities.findByNameContainingIgnoreCase(searchText, springPageable);
        return new ListingData<>(new Page<>(searchText, page.getSize(), page.getNumber(),
                page.getTotalElements(),
                page.getContent().stream().map(McpDBQueryService::toRow).toList()));
    }

    @Override
    public String getLabel(String id) {
        return entities.findById(id).map(McpEntity::getName).orElse("Unknown");
    }

    @Override
    public Optional<McpDto> getById(String id) {
        return entities.findById(id).map(McpDBQueryService::toDto);
    }

    @Override
    public List<McpDto> all() {
        return entities.findAll().stream().map(McpDBQueryService::toDto).toList();
    }

    @Override
    public long count() {
        return entities.count();
    }

    @Override
    public long countEnabled() {
        return entities.countByEnabledTrue();
    }

    static McpDto toDto(McpEntity e) {
        return new McpDto(e.getId(), e.getName(), e.getUrl(),
                McpTransport.valueOf(e.getTransport()), e.getTimeoutSeconds(),
                e.getDescription(), e.isEnabled(), e.getCreated());
    }

    static McpRow toRow(McpEntity e) {
        return new McpRow(e.getId(), e.getName(), e.getUrl(), e.getTransport(),
                e.isEnabled() ? "enabled" : "disabled");
    }
}
