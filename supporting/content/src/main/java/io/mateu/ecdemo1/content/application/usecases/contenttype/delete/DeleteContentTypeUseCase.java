package io.mateu.ecdemo1.content.application.usecases.contenttype.delete;

import io.mateu.ecdemo1.content.application.out.ContentTypeRepository;
import io.mateu.ecdemo1.content.domain.aggregates.contenttype.vo.ContentTypeId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteContentTypeUseCase {

final ContentTypeRepository repository;

@Transactional
public void handle(DeleteContentTypeCommand command) {
repository.deleteAllById(command.ids().stream()
.map(Long::valueOf)
.map(ContentTypeId::new)
.toList());
}

}
