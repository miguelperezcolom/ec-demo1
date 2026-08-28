package io.mateu.ecdemo1.iacp.infra.out.persistence;

import io.mateu.ecdemo1.iacp.application.out.query.RagQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.RagDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.RagRow;
import io.mateu.ecdemo1.iacp.domain.aggregates.rag.vo.RagKind;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RagDBQueryService implements RagQueryService {

    final RagEntityRepository entities;

    @Override
    public ListingData<RagRow> findAll(String searchText, Object filters, Pageable pageable) {
        var springPageable = org.springframework.data.domain.Pageable
                .ofSize(pageable.size()).withPage(pageable.page());
        var page = searchText == null || searchText.isBlank()
                ? entities.findAll(springPageable)
                : entities.findByNameContainingIgnoreCase(searchText, springPageable);
        return new ListingData<>(new Page<>(searchText, page.getSize(), page.getNumber(),
                page.getTotalElements(),
                page.getContent().stream().map(RagDBQueryService::toRow).toList()));
    }

    @Override
    public String getLabel(String id) {
        return entities.findById(id).map(RagEntity::getName).orElse("Unknown");
    }

    @Override
    public Optional<RagDto> getById(String id) {
        return entities.findById(id).map(RagDBQueryService::toDto);
    }

    @Override
    public List<RagDto> all() {
        return entities.findAll().stream().map(RagDBQueryService::toDto).toList();
    }

    @Override
    public long count() {
        return entities.count();
    }

    @Override
    public long countEnabled() {
        return entities.countByEnabledTrue();
    }

    static RagDto toDto(RagEntity e) {
        return new RagDto(e.getId(), e.getName(), RagKind.valueOf(e.getKind()),
                e.getConnectionUrl(), e.getCollectionName(), e.getEmbeddingLlmId(),
                e.getTopK(), e.getDescription(), e.isEnabled(), e.getCreated());
    }

    static RagRow toRow(RagEntity e) {
        return new RagRow(e.getId(), e.getName(), e.getKind(), e.getCollectionName(),
                e.isEnabled() ? "enabled" : "disabled");
    }
}
