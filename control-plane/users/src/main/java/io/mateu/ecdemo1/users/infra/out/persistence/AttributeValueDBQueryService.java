package io.mateu.ecdemo1.users.infra.out.persistence;

import io.mateu.ecdemo1.uicommons.paging.DbPaging;
import io.mateu.ecdemo1.users.application.query.AttributeValueQueryService;
import io.mateu.ecdemo1.users.application.query.dto.AttributeValueDto;
import io.mateu.ecdemo1.users.application.query.dto.AttributeValueRow;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Pageable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AttributeValueDBQueryService implements AttributeValueQueryService {

    final AttributeValueEntityRepository repository;

    private AttributeValueRow toRow(AttributeValueEntity entity) {
        return new AttributeValueRow(
                entity.id,
                labelOf(entity.attribute),
                entity.code,
                entity.label,
                entity.country,
                entity.region
        );
    }

    /** The attribute as people read it ("Hotel"), or as stored if it is not one we know. */
    private static String labelOf(String attribute) {
        try {
            return Attribute.valueOf(attribute).label();
        } catch (RuntimeException e) {
            return attribute;
        }
    }

    @Override
    public String getLabel(String id) {
        return repository.findById(id).map(AttributeValueEntity::getLabel).orElse(id);
    }

    @Override
    public Optional<AttributeValueDto> getById(String id) {
        return repository.findById(id).map(entity -> new AttributeValueDto(
                entity.getId(),
                entity.getAttribute(),
                entity.getCode(),
                entity.getLabel(),
                entity.getCountry(),
                entity.getRegion()
        ));
    }

    @Override
    public ListingData<AttributeValueRow> findAll(String searchText, Object filters, Pageable pageable) {
        var page = repository.search(searchText == null ? "" : searchText, DbPaging.pageable(pageable));
        return DbPaging.listing(searchText, page, this::toRow);
    }

    @Override
    public ListingData<AttributeValueRow> findByAttribute(Attribute attribute, String searchText, Pageable pageable) {
        var page = repository.searchByAttribute(attribute.name(), searchText == null ? "" : searchText,
                DbPaging.pageable(pageable));
        return DbPaging.listing(searchText, page, this::toRow);
    }
}
