package io.mateu.ecdemo1.users.infra.in.ui.pages.attributevalues;

import io.mateu.ecdemo1.uicommons.crud.CatalogueEditor;
import io.mateu.ecdemo1.users.application.query.dto.AttributeValueDto;
import io.mateu.ecdemo1.users.application.usecases.attributevalue.create.CreateAttributeValueCommand;
import io.mateu.ecdemo1.users.application.usecases.attributevalue.create.CreateAttributeValueUseCase;
import io.mateu.ecdemo1.users.application.usecases.attributevalue.save.SaveAttributeValueCommand;
import io.mateu.ecdemo1.users.application.usecases.attributevalue.save.SaveAttributeValueUseCase;
import io.mateu.ecdemo1.users.domain.aggregates.attributevalue.vo.Attribute;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.CountryValues;
import io.mateu.ecdemo1.users.infra.in.ui.suppliers.attributes.RegionValues;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Lookup;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * A value of the employee attribute catalogue. The attribute and the code are fixed once created —
 * users and audiences hold the code — so only the label and a hotel's location can be edited. The
 * attribute is a String with its options rather than the enum, so the select reads "Hotel" and not
 * "HOTEL".
 */
@Service
@Scope("prototype")
@RequiredArgsConstructor
public class AttributeValueViewModel implements CatalogueEditor<AttributeValueDto>, OptionsSupplier {

    @HiddenInCreate
    @ReadOnly
    String id;

    @EditableOnlyWhenCreating
    @NotEmpty
    @Stereotype(FieldStereotype.select)
    @Label("Atributo")
    String attribute = Attribute.HOTEL.name();

    @EditableOnlyWhenCreating
    @NotEmpty
    @Label("Código")
    @Help("Lo que se guarda en el usuario y con lo que se escriben las audiencias (MRU01, RECEPCION, es…). No se puede cambiar después.")
    String code;

    @NotEmpty
    @Label("Nombre")
    String label;

    @Lookup(search = CountryValues.class, label = CountryValues.class)
    @Label("País (solo hoteles)")
    @Help("El país de los empleados de este hotel se deriva de aquí.")
    String country;

    @Lookup(search = RegionValues.class, label = RegionValues.class)
    @Label("Región (solo hoteles)")
    @Help("La región de los empleados de este hotel se deriva de aquí.")
    String region;

    final CreateAttributeValueUseCase createAttributeValueUseCase;
    final SaveAttributeValueUseCase saveAttributeValueUseCase;

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        if ("attribute".equals(fieldName)) {
            return Arrays.stream(Attribute.values()).map(a -> new Option(a.name(), a.label())).toList();
        }
        return List.of();
    }

    public String create(HttpRequest httpRequest) {
        id = createAttributeValueUseCase.handle(new CreateAttributeValueCommand(attribute, code, label, country, region));
        return id;
    }

    public void save(HttpRequest httpRequest) {
        saveAttributeValueUseCase.handle(new SaveAttributeValueCommand(id, label, country, region));
    }

    @Override
    public String id() {
        return id;
    }

    public AttributeValueViewModel load(AttributeValueDto value) {
        id = value.id();
        attribute = value.attribute();
        code = value.code();
        label = value.label();
        country = value.country();
        region = value.region();
        return this;
    }

    @Override
    public String toString() {
        return id != null ? label : "Nuevo valor";
    }
}
