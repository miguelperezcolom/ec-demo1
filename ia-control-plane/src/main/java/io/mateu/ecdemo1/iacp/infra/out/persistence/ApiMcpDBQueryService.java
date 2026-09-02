package io.mateu.ecdemo1.iacp.infra.out.persistence;

import io.mateu.ecdemo1.iacp.application.out.query.ApiMcpQueryService;
import io.mateu.ecdemo1.iacp.application.out.query.dto.ApiMcpDto;
import io.mateu.ecdemo1.iacp.application.out.query.dto.ApiMcpRow;
import io.mateu.ecdemo1.iacp.domain.aggregates.apimcp.vo.ApiKind;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Page;
import io.mateu.uidl.data.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApiMcpDBQueryService implements ApiMcpQueryService {

    final ApiMcpEntityRepository entities;

    @Override
    public ListingData<ApiMcpRow> findAll(String searchText, Object filters, Pageable pageable) {
        var springPageable = org.springframework.data.domain.Pageable
                .ofSize(pageable.size()).withPage(pageable.page());
        var page = searchText == null || searchText.isBlank()
                ? entities.findAll(springPageable)
                : entities.findByNameContainingIgnoreCase(searchText, springPageable);
        return new ListingData<>(new Page<>(searchText, page.getSize(), page.getNumber(),
                page.getTotalElements(),
                page.getContent().stream().map(ApiMcpDBQueryService::toRow).toList()));
    }

    @Override
    public String getLabel(String id) {
        return entities.findById(id).map(ApiMcpEntity::getName).orElse("Unknown");
    }

    @Override
    public Optional<ApiMcpDto> getById(String id) {
        return entities.findById(id).map(ApiMcpDBQueryService::toDto);
    }

    @Override
    public List<ApiMcpDto> all() {
        return entities.findAll().stream().map(ApiMcpDBQueryService::toDto).toList();
    }

    @Override
    public long count() {
        return entities.count();
    }

    @Override
    public long countEnabled() {
        return entities.countByEnabledTrue();
    }

    static ApiMcpDto toDto(ApiMcpEntity e) {
        var api = ApiMcpDBRepository.toDomain(e);
        return new ApiMcpDto(
                e.getId(), e.getName(), ApiKind.valueOf(e.getKind()), e.getBaseUrl(), e.getSpecUrl(),
                // The boolean, never the value. Nothing outside the one method that decrypts should
                // be able to carry it — the same rule the LLM catalogue follows.
                api.getCredential().isSet(),
                api.getTools().stream()
                        .map(t -> new ApiMcpDto.ExposedToolDto(t.operation(), t.toolName(),
                                t.description(), t.requiredRoles()))
                        .toList(),
                e.getDescription(), e.isEnabled(), e.getCreated());
    }

    static ApiMcpRow toRow(ApiMcpEntity e) {
        var api = ApiMcpDBRepository.toDomain(e);
        return new ApiMcpRow(e.getId(), e.getName(), e.getKind(), e.getBaseUrl(),
                api.getTools().size(),
                api.getCredential().isSet() ? "set" : "missing",
                // Catalogued but offering nothing is the state worth seeing in a listing: the entry
                // looks finished and an agent given it gets no tools at all.
                !e.isEnabled() ? "disabled"
                        : api.getTools().isEmpty() ? "no tools yet"
                        : "enabled");
    }
}
