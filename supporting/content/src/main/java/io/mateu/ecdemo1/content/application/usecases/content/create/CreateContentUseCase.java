package io.mateu.ecdemo1.content.application.usecases.content.create;

import io.mateu.ecdemo1.content.application.out.ContentRepository;
import io.mateu.ecdemo1.content.domain.aggregates.content.Content;
import io.mateu.ecdemo1.content.domain.aggregates.content.vo.*;
import io.mateu.ecdemo1.content.domain.aggregates.contenttype.vo.ContentTypeId;
import io.mateu.ecdemo1.content.domain.aggregates.label.vo.LabelId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateContentUseCase {

final ContentRepository repository;

@Transactional
public String handle(CreateContentCommand command) {
return repository.save(Content.of(
        new ContentName(command.name()),
        new ContentTypeId(Long.valueOf(command.contentType())),
        command.labels().stream().map(Long::valueOf).map(LabelId::new).toList(),
        command.values().stream()
        .map(value -> new ContentValue(value.language(), value.country(), value.value()))
        .toList())
).id().toString();
}

}
