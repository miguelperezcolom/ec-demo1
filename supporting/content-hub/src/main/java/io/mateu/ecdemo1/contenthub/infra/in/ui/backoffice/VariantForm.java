package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.slot.SlotVariant;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;

import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/** A slot's variant: what it shows, to whom, and with which priority over the others. */
public class VariantForm implements OptionsSupplier {

    @Label("Prioridad (mayor gana)")
    public int priority;
    @Label("Título")
    public String title;
    @Label("HTML (admite [[media:id]])")
    @Stereotype(FieldStereotype.textarea)
    public String html;
    @Label("Enlace")
    public String linkUrl;
    @Label("Audiencia con nombre")
    @Stereotype(FieldStereotype.select)
    public String namedAudience;
    @Label("Regiones")
    @Stereotype(FieldStereotype.multiSelect)
    public List<String> blockRegions;
    @Label("Hoteles")
    @Stereotype(FieldStereotype.multiSelect)
    public List<String> blockHotels;
    @Label("Departamentos")
    @Stereotype(FieldStereotype.multiSelect)
    public List<String> blockDepartments;
    @Label("Id")
    public String id;

    public static VariantForm of(SlotVariant v) {
        var f = new VariantForm();
        f.id = v.id();
        f.priority = v.priority();
        f.title = v.title();
        f.html = v.html();
        f.linkUrl = v.linkUrl();
        var parsed = AudienceFields.parse(v.audience());
        f.namedAudience = parsed.named();
        f.blockRegions = parsed.values().get(Attribute.REGION);
        f.blockHotels = parsed.values().get(Attribute.HOTEL);
        f.blockDepartments = parsed.values().get(Attribute.DEPARTMENT);
        return f;
    }

    public SlotVariant toVariant() {
        var values = new EnumMap<Attribute, List<String>>(Attribute.class);
        values.put(Attribute.REGION, blockRegions);
        values.put(Attribute.HOTEL, blockHotels);
        values.put(Attribute.DEPARTMENT, blockDepartments);
        Audience audience = AudienceFields.build(namedAudience, values, null);
        return new SlotVariant(id == null || id.isBlank() ? "v" + UUID.randomUUID().toString().substring(0, 6) : id,
                audience, priority, title, html, linkUrl);
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "namedAudience".equals(fieldName) || AudienceFields.supports(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return "namedAudience".equals(fieldName) ? Catalog.get().namedAudiences() : AudienceFields.options(fieldName);
    }

    @Override
    public String toString() {
        return priority + " · " + (title == null ? "" : title);
    }
}
