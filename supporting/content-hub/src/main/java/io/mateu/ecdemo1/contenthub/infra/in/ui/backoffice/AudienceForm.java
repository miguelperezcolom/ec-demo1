package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Hidden;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;

import java.util.EnumMap;
import java.util.List;

/**
 * A form with an audience: the visual builder — a named audience, and the values of each attribute
 * (the real ones, from the directory) — and, read-only, who that is in words and how many employees.
 */
public abstract class AudienceForm implements OptionsSupplier {

    @Section("Audiencia")
    @Label("Audiencia con nombre")
    @Stereotype(FieldStereotype.select)
    String namedAudience;
    @Label("Empresa")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> companies;
    @Label("País")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> countries;
    @Label("Región")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> regions;
    @Label("Hotel")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> hotels;
    @Label("Departamento")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> departments;
    @Label("Puesto")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> jobTitles;
    @ReadOnly
    @Label("Quién lo verá")
    @Colspan(2)
    String audienceSummary;
    @Hidden
    String advancedAudience;

    protected Audience audience() {
        var values = new EnumMap<Attribute, List<String>>(Attribute.class);
        values.put(Attribute.COMPANY, companies);
        values.put(Attribute.COUNTRY, countries);
        values.put(Attribute.REGION, regions);
        values.put(Attribute.HOTEL, hotels);
        values.put(Attribute.DEPARTMENT, departments);
        values.put(Attribute.JOB_TITLE, jobTitles);
        return AudienceFields.build(namedAudience, values, advancedAudience);
    }

    protected void audience(Audience audience, AudienceCommands commands, NamedAudiences named) {
        var parsed = AudienceFields.parse(audience);
        namedAudience = parsed.named();
        companies = parsed.values().get(Attribute.COMPANY);
        countries = parsed.values().get(Attribute.COUNTRY);
        regions = parsed.values().get(Attribute.REGION);
        hotels = parsed.values().get(Attribute.HOTEL);
        departments = parsed.values().get(Attribute.DEPARTMENT);
        jobTitles = parsed.values().get(Attribute.JOB_TITLE);
        advancedAudience = parsed.advanced();
        summarize(audience, commands, named);
    }

    protected void summarize(Audience audience, AudienceCommands commands, NamedAudiences named) {
        var count = commands.count(audience);
        audienceSummary = audience.describe(named.labels()) + " — " + count + " empleado(s)"
                + (count == 0 ? " ⚠️ nadie cumple esta audiencia" : "")
                + (advancedAudience != null ? " (expresión avanzada: se conserva mientras no cambies los campos)" : "");
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "namedAudience".equals(fieldName) || AudienceFields.FIELDS.containsKey(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        if ("namedAudience".equals(fieldName)) {
            return Catalog.get().namedAudiences();
        }
        return AudienceFields.options(fieldName);
    }
}
