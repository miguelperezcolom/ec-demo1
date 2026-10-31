package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.NamedAudience;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.EditableOnlyWhenCreating;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * A reusable audience. Redefining it changes what everything that names it is visible to, once the
 * projection is recalculated (seconds).
 */
@Service
@Scope("prototype")
public class NamedAudienceForm extends AudienceForm implements SimpleCrud.CrudForm {

    @Section("Audiencia con nombre")
    @NotEmpty
    @EditableOnlyWhenCreating
    @Label("Id")
    String id;
    @NotEmpty
    @Label("Nombre")
    String name;

    @Section("Perfiles que la cumplen")
    @ReadOnly
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    @Stereotype(FieldStereotype.textarea)
    String profiles;

    final AudienceCommands commands;
    final NamedAudiences namedAudiences;
    final Readers readers;

    public NamedAudienceForm(AudienceCommands commands, NamedAudiences namedAudiences, Readers readers) {
        this.commands = commands;
        this.namedAudiences = namedAudiences;
        this.readers = readers;
    }

    public NamedAudienceForm load(NamedAudience a) {
        id = a.id();
        name = a.name();
        audience(a.expression(), commands, namedAudiences);
        profiles = commands.profilesIn(a.expression()).stream()
                .map(p -> p.attributes().canonical().replace(";", " · ") + " — " + p.employees() + " empleado(s)")
                .collect(Collectors.joining("\n"));
        return this;
    }

    @Override
    public String create(HttpRequest httpRequest) {
        commands.define(readers.of(httpRequest), id, name, audience());
        return id;
    }

    @Override
    public void save(HttpRequest httpRequest) {
        commands.redefine(readers.of(httpRequest), id, name, audience());
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nueva audiencia" : name;
    }
}
