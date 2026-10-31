package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.NavigationCommands;
import io.mateu.ecdemo1.contenthub.domain.navigation.Section;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.interfaces.HttpRequest;
import io.mateu.uidl.interfaces.OptionsSupplier;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;

/** A section of the navigation tree: where it hangs, its position among its siblings and its names. */
@Service
@Scope("prototype")
@ContentAdmins
public class SectionForm implements SimpleCrud.CrudForm, OptionsSupplier {

    @NotEmpty
    @EditableOnlyWhenCreating
    @Label("Id (en la URL)")
    String id;
    @Label("Dentro de")
    @Stereotype(FieldStereotype.select)
    String parentId;
    @Label("Posición entre sus hermanas (0 = primera)")
    int position;
    @NotEmpty
    @Label("Nombre (es)")
    String nameEs;
    @Label("Name (en)")
    String nameEn;
    @Label("Name (de)")
    String nameDe;

    final NavigationCommands commands;
    final NavigationRepository navigation;
    final Readers readers;

    public SectionForm(NavigationCommands commands, NavigationRepository navigation, Readers readers) {
        this.commands = commands;
        this.navigation = navigation;
        this.readers = readers;
    }

    public SectionForm load(Section s) {
        id = s.id();
        parentId = s.parentId();
        position = s.order();
        nameEs = s.names().get("es");
        nameEn = s.names().get("en");
        nameDe = s.names().get("de");
        return this;
    }

    LinkedHashMap<String, String> names() {
        var names = new LinkedHashMap<String, String>();
        names.put("es", nameEs);
        if (nameEn != null && !nameEn.isBlank()) {
            names.put("en", nameEn);
        }
        if (nameDe != null && !nameDe.isBlank()) {
            names.put("de", nameDe);
        }
        return names;
    }

    @Override
    public String create(HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        commands.add(reader, id, parentId, names());
        commands.move(reader, id, parentId, position);
        return id;
    }

    @Override
    public void save(HttpRequest httpRequest) {
        var reader = readers.of(httpRequest);
        commands.rename(reader, id, names());
        commands.move(reader, id, parentId, position);
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "parentId".equals(fieldName);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return Catalog.get().sections().stream().filter(o -> !o.value().equals(id)).toList();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nueva sección" : nameEs;
    }
}
