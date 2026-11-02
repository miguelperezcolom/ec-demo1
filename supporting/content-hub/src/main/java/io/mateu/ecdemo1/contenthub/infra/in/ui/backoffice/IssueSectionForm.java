package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;

import java.util.ArrayList;
import java.util.List;

/** What an issue carries in one section of its newsletter: the items, in order (published ones, of any site). */
@Editors
public class IssueSectionForm implements OptionsSupplier {

    @ReadOnly
    @Label("Sección")
    public String section;
    @Label("Items (en este orden)")
    @Stereotype(FieldStereotype.multiSelect)
    public List<String> itemIds = new ArrayList<>();

    public static IssueSectionForm of(String section, List<String> itemIds) {
        var f = new IssueSectionForm();
        f.section = section;
        f.itemIds = new ArrayList<>(itemIds == null ? List.of() : itemIds);
        return f;
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "itemIds".equals(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return Catalog.get().publishedItems();
    }

    @Override
    public String toString() {
        return section == null ? "Sección" : section;
    }
}
