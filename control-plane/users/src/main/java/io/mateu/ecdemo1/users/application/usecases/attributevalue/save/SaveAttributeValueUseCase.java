package io.mateu.ecdemo1.users.application.usecases.attributevalue.save;

import io.mateu.ecdemo1.users.application.out.AttributeValueRepository;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.AttributeValueId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes a value's label, or a hotel's location. Moving a hotel does not re-derive the country and
 * region of the users already in it: they are re-derived the next time each is saved. Good enough for
 * a catalogue that, in practice, never moves a hotel.
 */
@Service
@RequiredArgsConstructor
public class SaveAttributeValueUseCase {

    final AttributeValueRepository repository;

    @Transactional
    public void handle(SaveAttributeValueCommand command) {
        var value = repository.findById(new AttributeValueId(command.id())).orElseThrow();
        value.update(command.label(), command.country(), command.region());
        repository.save(value);
    }
}
