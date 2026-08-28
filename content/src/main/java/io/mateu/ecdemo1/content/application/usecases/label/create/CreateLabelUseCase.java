package io.mateu.ecdemo1.content.application.usecases.label.create;

import io.mateu.ecdemo1.content.application.out.LabelRepository;
import io.mateu.ecdemo1.content.domain.aggregates.label.Label;
import io.mateu.ecdemo1.content.domain.aggregates.label.vo.LabelId;
import io.mateu.ecdemo1.content.domain.aggregates.label.vo.LabelName;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateLabelUseCase {

final LabelRepository repository;

@Transactional
public String handle(CreateLabelCommand command) {
return repository.save(Label.of(new LabelName(command.name()))
).id().toString();
}

}
