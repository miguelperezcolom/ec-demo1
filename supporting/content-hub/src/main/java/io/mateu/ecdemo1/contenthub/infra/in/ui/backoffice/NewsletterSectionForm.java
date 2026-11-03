package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterEditorial;
import io.mateu.ecdemo1.contenthub.infra.in.security.Editors;
import io.mateu.uidl.annotations.HiddenInList;
import io.mateu.uidl.annotations.HiddenInEditor;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;

import java.util.ArrayList;
import java.util.List;

/**
 * One section of a newsletter («Editorial», «Noticias de la cadena»…): a section of its site, which every
 * issue fills. Its automatic content: the site's own items in it, and those of the intranet sections chosen.
 */
@Editors
public class NewsletterSectionForm implements OptionsSupplier {

    @Label("Id (minúsculas, cifras y guiones)")
    public String id;
    @Label("Nombre")
    public String name;
    @Label("Toma lo publicado en estas secciones de la intranet")
    @Stereotype(FieldStereotype.multiSelect)
    @HiddenInList
    public List<String> sources = new ArrayList<>();
    /** The same, for the table: a list in a table cell is drawn with its values run together. */
    @Label("Toma lo publicado de")
    @HiddenInEditor
    public String sourcesText;

    public static NewsletterSectionForm of(String id, String name, List<String> sources) {
        var f = new NewsletterSectionForm();
        f.id = id;
        f.name = name;
        f.sources = new ArrayList<>(sources == null ? List.of() : sources);
        f.sourcesText = String.join(", ", f.sources);
        return f;
    }

    public NewsletterEditorial.SectionSpec toSpec() {
        var key = id == null || id.isBlank() ? slug(name) : id.trim();
        return new NewsletterEditorial.SectionSpec(key, name, sources == null ? List.of() : List.copyOf(sources));
    }

    static String slug(String name) {
        var s = java.text.Normalizer.normalize(name == null ? "" : name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "seccion" : s;
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "sources".equals(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return Catalog.get().sections();
    }

    @Override
    public String toString() {
        return name == null ? "Sección" : name;
    }
}
