package io.mateu.ecdemo1.content.infra.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import io.mateu.ecdemo1.content.application.query.ContentQueryService;
import io.mateu.ecdemo1.content.application.query.dto.ContentDto;
import io.mateu.ecdemo1.content.application.query.dto.ContentRow;
import io.mateu.ecdemo1.content.application.usecases.content.ContentValueDto;
import io.mateu.ecdemo1.content.domain.aggregates.content.vo.ContentId;
import io.mateu.ecdemo1.content.domain.aggregates.content.vo.ContentName;
import io.mateu.ecdemo1.content.domain.aggregates.content.vo.CountryCode;
import io.mateu.ecdemo1.content.domain.aggregates.content.vo.LanguageCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static io.mateu.core.infra.JsonSerializer.listFromJson;


@Service
@RequiredArgsConstructor
public class ContentDBQueryService implements ContentQueryService {

final ContentEntityRepository repository;

private ContentRow toDomain(ContentEntity entity) {
return new ContentRow(
entity.id.toString(),
entity.name
);
}

@Override
public String getLabel(String id) {
return repository.findById(Long.valueOf(id)).map(ContentEntity::getName).orElse("Unknown");
}

@Override
public Optional<ContentDto> getById(String id) {
    return repository.findById(Long.valueOf(id)).map(this::toDto);
    }

    private ContentDto toDto(ContentEntity entity) {
    return new ContentDto(
    entity.id.toString(),
    entity.name,
            entity.contentTypeId.toString(),
            listFromJson(entity.labelsJson, Long.class).stream()
                    .map(String::valueOf).toList(),
            listFromJson(entity.valuesJson, ContentValueEntity.class).stream()
                    .map(value -> new ContentValueDto(
                            CountryCode.valueOf(value.country()),
                            LanguageCode.valueOf(value.language()),
                            value.value()
                            )
                    ).toList()
    );
    }

    @Override
    public Page<ContentRow> findAll(String searchText, Object filters, Pageable pageable) {
        return repository.findAllByNameContainingIgnoreCase(searchText == null ? "" : searchText, pageable)
                .map(this::toDomain);
    }
}
