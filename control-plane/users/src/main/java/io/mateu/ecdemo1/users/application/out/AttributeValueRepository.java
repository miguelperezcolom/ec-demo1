package io.mateu.ecdemo1.users.application.out;

import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.AttributeValue;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.AttributeValueId;

public interface AttributeValueRepository extends Repository<AttributeValue, AttributeValueId> {
}
