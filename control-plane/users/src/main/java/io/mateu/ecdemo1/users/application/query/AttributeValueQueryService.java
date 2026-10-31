package io.mateu.ecdemo1.users.application.query;

import io.mateu.ecdemo1.users.application.query.dto.AttributeValueDto;
import io.mateu.ecdemo1.users.application.query.dto.AttributeValueRow;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import io.mateu.uidl.data.ListingData;
import io.mateu.uidl.data.Pageable;

public interface AttributeValueQueryService extends QueryService<AttributeValueDto, AttributeValueRow, String> {

    /** The values of one attribute matching the text in their code or label: what a select offers. */
    ListingData<AttributeValueRow> findByAttribute(Attribute attribute, String searchText, Pageable pageable);
}
