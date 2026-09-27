package io.mateu.ecdemo1.content.application.usecases.content.delete;

import io.mateu.ecdemo1.content.application.out.ContentRepository;
import io.mateu.ecdemo1.content.domain.aggregates.content.vo.ContentId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteContentUseCase {

final ContentRepository repository;

@Transactional
public void handle(DeleteContentCommand command) {
repository.deleteAllById(command.ids().stream()
.map(Long::valueOf)
.map(ContentId::new)
.toList());
}

}
