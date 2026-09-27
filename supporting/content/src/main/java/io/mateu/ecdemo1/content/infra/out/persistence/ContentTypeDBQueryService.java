package io.mateu.ecdemo1.content.infra.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import io.mateu.ecdemo1.content.application.query.ContentTypeQueryService;
import io.mateu.ecdemo1.content.application.query.dto.ContentTypeDto;
import io.mateu.ecdemo1.content.application.query.dto.ContentTypeRow;
import io.mateu.ecdemo1.content.domain.aggregates.contenttype.vo.ContentTypeId;
import io.mateu.ecdemo1.content.domain.aggregates.contenttype.vo.ContentTypeName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static io.mateu.core.infra.JsonSerializer.listFromJson;


@Service
@RequiredArgsConstructor
public class ContentTypeDBQueryService implements ContentTypeQueryService {

final ContentTypeEntityRepository repository;

private ContentTypeRow toDomain(ContentTypeEntity entity) {
return new ContentTypeRow(
entity.id.toString(),
entity.name
);
}

@Override
public String getLabel(String id) {
return repository.findById(Long.valueOf(id)).map(ContentTypeEntity::getName).orElse("Unknown");
}

@Override
public Optional<ContentTypeDto> getById(String id) {
    return repository.findById(Long.valueOf(id)).map(this::toDto);
    }

    private ContentTypeDto toDto(ContentTypeEntity entity) {
    return new ContentTypeDto(
    entity.id.toString(),
    entity.name
    );
    }

    @Override
    public Page<ContentTypeRow> findAll(String searchText, Object filters, Pageable pageable) {
        return repository.findAllByNameContainingIgnoreCase(searchText == null ? "" : searchText, pageable)
                .map(this::toDomain);
    }
}
