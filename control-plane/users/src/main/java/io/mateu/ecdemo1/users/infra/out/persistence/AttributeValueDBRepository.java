package io.mateu.ecdemo1.users.infra.out.persistence;

import io.mateu.ecdemo1.users.application.out.AttributeValueRepository;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.AttributeValue;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.AttributeValueId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttributeValueDBRepository implements AttributeValueRepository {

    final AttributeValueEntityRepository repository;

    @Override
    public Optional<AttributeValue> findById(AttributeValueId id) {
        return repository.findById(id.id()).map(this::toDomain);
    }

    private AttributeValue toDomain(AttributeValueEntity entity) {
        return new AttributeValue(
                new AttributeValueId(entity.id),
                Attribute.valueOf(entity.attribute),
                entity.code,
                entity.label,
                entity.country,
                entity.region
        );
    }

    private AttributeValueEntity toEntity(AttributeValue value) {
        return new AttributeValueEntity(
                value.getId().id(),
                value.getAttribute().name(),
                value.getCode(),
                value.getLabel(),
                value.getCountry(),
                value.getRegion()
        );
    }

    @Override
    public AttributeValueId save(AttributeValue value) {
        return new AttributeValueId(repository.save(toEntity(value)).id);
    }

    @Override
    public void deleteAllById(List<AttributeValueId> selectedIds) {
        repository.deleteAllById(selectedIds.stream().map(AttributeValueId::id).toList());
    }
}
