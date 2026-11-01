package io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes;

import io.mateu.ecdemo1.users.application.query.AttributeValueQueryService;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import org.springframework.stereotype.Service;

/** The catalogue's {@link Attribute#JOB_TITLE} values, for a lookup. */
@Service
public class JobTitleValues extends AttributeValueLookup {

    public JobTitleValues(AttributeValueQueryService queryService) {
        super(queryService, Attribute.JOB_TITLE);
    }
}
