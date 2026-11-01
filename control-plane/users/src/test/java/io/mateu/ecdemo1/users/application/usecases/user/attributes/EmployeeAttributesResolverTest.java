package io.mateu.ecdemo1.users.application.usecases.user.attributes;

import io.mateu.ecdemo1.users.application.out.AttributeValueRepository;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.AttributeValue;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.AttributeValueId;
import io.mateu.ecdemo1.users.domain.aggregates.user.vo.EmployeeAttributes;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeAttributesResolverTest {

    static class Catalogue implements AttributeValueRepository {
        final Map<AttributeValueId, AttributeValue> values = new HashMap<>();

        Catalogue with(AttributeValue value) {
            values.put(value.getId(), value);
            return this;
        }

        @Override
        public Optional<AttributeValue> findById(AttributeValueId id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public AttributeValueId save(AttributeValue entity) {
            values.put(entity.getId(), entity);
            return entity.getId();
        }

        @Override
        public void deleteAllById(List<AttributeValueId> selectedIds) {
            selectedIds.forEach(values::remove);
        }
    }

    final EmployeeAttributesResolver resolver = new EmployeeAttributesResolver(new Catalogue()
            .with(AttributeValue.of(Attribute.HOTEL, "MRU01", "Riu Demo Mauricio", "MU", "MAURICIO"))
            .with(AttributeValue.of(Attribute.HOTEL, "TFS01", "Riu Demo Tenerife", "ES", "CANARIAS")));

    @Test
    void theHotelDecidesCountryAndRegionWhateverWasTyped() {
        var resolved = resolver.resolve(new EmployeeAttributes("C", "ES", "BALEARES", "MRU01", "PISOS", null, "en"));

        assertThat(resolved).isEqualTo(new EmployeeAttributes("C", "MU", "MAURICIO", "MRU01", "PISOS", null, "en"));
    }

    @Test
    void withoutAHotelCountryAndRegionAreKept() {
        var given = new EmployeeAttributes("C", "ES", "BALEARES", null, "DIRECCION", null, "es");

        assertThat(resolver.resolve(given)).isEqualTo(given);
    }

    @Test
    void aHotelTheCatalogueDoesNotKnowIsKeptAsGiven() {
        var given = new EmployeeAttributes(null, "ES", null, "XXX99", null, null, null);

        assertThat(resolver.resolve(given)).isEqualTo(given);
    }

    @Test
    void blanksAreNoValueAndNullIsNone() {
        assertThat(resolver.resolve(null)).isEqualTo(EmployeeAttributes.NONE);
        assertThat(resolver.resolve(new EmployeeAttributes("", " ", null, "", null, null, null)))
                .isEqualTo(EmployeeAttributes.NONE);
    }

    @Test
    void onlyAHotelKeepsALocation() {
        var department = AttributeValue.of(Attribute.DEPARTMENT, "IT", "IT", "ES", "CANARIAS");

        assertThat(department.getCountry()).isNull();
        assertThat(department.getRegion()).isNull();
        assertThat(department.getId().id()).isEqualTo("DEPARTMENT:IT");
    }
}
