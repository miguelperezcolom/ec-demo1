package io.mateu.ecdemo1.users.application.usecases.user.attributes;

import io.mateu.ecdemo1.users.application.out.AttributeValueRepository;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.AttributeValueId;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Makes an employee's country and region those of their hotel. Called by the user use cases before
 * the attributes reach the aggregate, so a user in MRU01 is in Mauritius whatever the form said — an
 * audience on {@code region} and one on {@code hotel} can then never disagree about the same person.
 *
 * <p>Without a hotel, the country and region given are kept: someone in headquarters has a country
 * but no hotel. A hotel the catalogue does not know (deleted, or never there) is kept as it is, with
 * the location given — refusing the save would block every edit of that user over a catalogue
 * problem that is not theirs.
 */
@Service
@RequiredArgsConstructor
public class EmployeeAttributesResolver {

    final AttributeValueRepository catalogue;

    public EmployeeAttributes resolve(EmployeeAttributes attributes) {
        if (attributes == null) {
            return EmployeeAttributes.NONE;
        }
        if (attributes.hotel() == null) {
            return attributes;
        }
        return catalogue.findById(AttributeValueId.of(Attribute.HOTEL, attributes.hotel()))
                .map(hotel -> attributes.locatedAt(hotel.getCountry(), hotel.getRegion()))
                .orElse(attributes);
    }
}
