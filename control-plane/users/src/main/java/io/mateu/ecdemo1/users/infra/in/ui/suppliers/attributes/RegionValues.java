package io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes;

import io.mateu.ecdemo1.users.application.query.AttributeValueQueryService;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import org.springframework.stereotype.Service;

/** The catalogue's {@link Attribute#REGION} values, for a lookup. */
@Service
public class RegionValues extends AttributeValueLookup {

    public RegionValues(AttributeValueQueryService queryService) {
        super(queryService, Attribute.REGION);
    }
}
