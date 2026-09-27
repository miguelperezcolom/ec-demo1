package io.mateu.ecdemo1.content.infra.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import io.mateu.ecdemo1.content.application.query.LabelQueryService;
import io.mateu.ecdemo1.content.application.query.dto.LabelDto;
import io.mateu.ecdemo1.content.application.query.dto.LabelRow;
import io.mateu.ecdemo1.content.domain.aggregates.label.vo.LabelId;
import io.mateu.ecdemo1.content.domain.aggregates.label.vo.LabelName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static io.mateu.core.infra.JsonSerializer.listFromJson;


@Service
@RequiredArgsConstructor
public class LabelDBQueryService implements LabelQueryService {

final LabelEntityRepository repository;

private LabelRow toDomain(LabelEntity entity) {
return new LabelRow(
entity.id.toString(),
entity.name
);
}

@Override
public String getLabel(String id) {
return repository.findById(Long.valueOf(id)).map(LabelEntity::getName).orElse("Unknown");
}

@Override
public Optional<LabelDto> getById(String id) {
    return repository.findById(Long.valueOf(id)).map(this::toDto);
    }

    private LabelDto toDto(LabelEntity entity) {
    return new LabelDto(
    entity.id.toString(),
    entity.name
    );
    }

    @Override
    public Page<LabelRow> findAll(String searchText, Object filters, Pageable pageable) {
        return repository.findAllByNameContainingIgnoreCase(searchText == null ? "" : searchText, pageable)
                .map(this::toDomain);
    }
}
