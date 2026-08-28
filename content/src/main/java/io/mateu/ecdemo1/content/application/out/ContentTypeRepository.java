package io.mateu.ecdemo1.content.application.out;

import io.mateu.ecdemo1.content.domain.aggregates.contenttype.ContentType;
import io.mateu.ecdemo1.content.domain.aggregates.contenttype.vo.ContentTypeId;

public interface ContentTypeRepository extends Repository<ContentType, ContentTypeId> {
}
