package io.mateu.ecdemo1.users.application.usecases.attributevalue.create;

import io.mateu.ecdemo1.users.application.out.AttributeValueRepository;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.AttributeValue;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateAttributeValueUseCase {

    final AttributeValueRepository repository;

    /**
     * Creates a catalogue value and answers its id. Refuses one that exists: the id is derived from
     * attribute and code, so a second "create" would silently merge over the first one's label.
     */
    @Transactional
    public String handle(CreateAttributeValueCommand command) {
        var value = AttributeValue.of(Attribute.valueOf(command.attribute()), command.code(), command.label(),
                command.country(), command.region());
        if (repository.findById(value.getId()).isPresent()) {
            throw new IllegalArgumentException("The value " + value.getCode() + " already exists for "
                    + value.getAttribute().label());
        }
        return repository.save(value).id();
    }
}
