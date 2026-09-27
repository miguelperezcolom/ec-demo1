package io.mateu.ecdemo1.content.application.out;

import io.mateu.ecdemo1.content.domain.aggregates.content.Content;
import io.mateu.ecdemo1.content.domain.aggregates.content.vo.ContentId;

public interface ContentRepository extends Repository<Content, ContentId> {
}
