package io.mateu.ecdemo1.users.application.usecases.attributevalue.delete;

import io.mateu.ecdemo1.users.application.out.AttributeValueRepository;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.AttributeValueId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes values from the catalogue. Users holding one keep the code — it stays in their attributes
 * and in the directory, and audiences written with it still match — it just cannot be chosen again.
 */
@Service
@RequiredArgsConstructor
public class DeleteAttributeValueUseCase {

    final AttributeValueRepository repository;

    @Transactional
    public void handle(DeleteAttributeValueCommand command) {
        repository.deleteAllById(command.ids().stream().map(AttributeValueId::new).toList());
    }
}
