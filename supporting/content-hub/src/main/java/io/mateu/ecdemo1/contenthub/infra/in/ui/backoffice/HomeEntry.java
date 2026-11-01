package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;

import java.util.List;

/** One page of the homes list: a published page. */
@ContentAdmins
public class HomeEntry implements OptionsSupplier {

    @Label("Página")
    @Stereotype(FieldStereotype.select)
    public String pageId;

    public static HomeEntry of(String pageId) {
        var e = new HomeEntry();
        e.pageId = pageId;
        return e;
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "pageId".equals(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return Catalog.get().publishedPages();
    }

    @Override
    public String toString() {
        return Catalog.get() == null || pageId == null ? "Página" : Catalog.get().publishedPages().stream()
                .filter(o -> pageId.equals(o.value())).map(o -> String.valueOf(o.label())).findFirst().orElse(pageId);
    }
}
