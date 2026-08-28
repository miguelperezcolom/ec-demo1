package io.mateu.ecdemo1.content.application.usecases.contenttype.create;

import io.mateu.ecdemo1.content.application.out.ContentTypeRepository;
import io.mateu.ecdemo1.content.domain.aggregates.contenttype.ContentType;
import io.mateu.ecdemo1.content.domain.aggregates.contenttype.vo.ContentTypeId;
import io.mateu.ecdemo1.content.domain.aggregates.contenttype.vo.ContentTypeName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateContentTypeUseCase {

final ContentTypeRepository repository;

@Transactional
public String handle(CreateContentTypeCommand command) {
return repository.save(ContentType.of(new ContentTypeName(command.name()))
).id().toString();
}

}
