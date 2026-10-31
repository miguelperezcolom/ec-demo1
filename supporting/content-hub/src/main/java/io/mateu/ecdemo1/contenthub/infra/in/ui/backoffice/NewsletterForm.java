package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.infra.in.security.ContentAdmins;
import io.mateu.ecdemo1.contenthub.application.out.NewsletterRepository;
import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.NewsletterService;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterEdition;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.ecdemo1.contenthub.infra.in.ui.common.Ui;
import io.mateu.ecdemo1.uicommons.html.Html;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.Message;
import io.mateu.uidl.data.Option;
import io.mateu.uidl.fluent.Component;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * A newsletter edition: the items, in order, for an audience. Recipients are grouped by profile and
 * language; each group gets one email with the items IT sees — a group that sees none gets nothing.
 */
@Service
@Scope("prototype")
@ContentAdmins
public class NewsletterForm extends AudienceForm implements SimpleCrud.CrudForm {

    @Section("Edición")
    @ReadOnly
    @HiddenInCreate
    @Label("Id")
    String id;
    @ReadOnly
    @HiddenInCreate
    @Label("Estado")
    String state;
    @NotEmpty
    @Label("Título")
    String title;
    @Label("Introducción")
    @Stereotype(FieldStereotype.textarea)
    String intro;
    @Label("Items (en este orden)")
    @Stereotype(FieldStereotype.multiSelect)
    List<String> itemIds;
    @Label("Enviar el (vacío: al programar)")
    LocalDateTime scheduledAt;

    @Section("Grupos de destinatarios")
    @ReadOnly
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    @Stereotype(FieldStereotype.textarea)
    String groups;

    @Section("Vista previa (primer grupo)")
    @HiddenInCreate
    @Label("")
    @Colspan(2)
    Callable<Component> preview = this::preview;

    final NewsletterService newsletter;
    final NewsletterRepository editions;
    final AudienceCommands audienceCommands;
    final NamedAudiences namedAudiences;
    final Readers readers;

    public NewsletterForm(NewsletterService newsletter, NewsletterRepository editions, AudienceCommands audienceCommands,
                          NamedAudiences namedAudiences, Readers readers) {
        this.newsletter = newsletter;
        this.editions = editions;
        this.audienceCommands = audienceCommands;
        this.namedAudiences = namedAudiences;
        this.readers = readers;
    }

    public NewsletterForm load(NewsletterEdition e) {
        id = e.id();
        title = e.title();
        intro = e.intro();
        itemIds = e.itemIds();
        scheduledAt = Ui.local(e.scheduledAt());
        audience(e.audience(), audienceCommands, namedAudiences);
        state = switch (e.status()) {
            case DRAFT -> "Borrador";
            case SCHEDULED -> "Programada para el " + Ui.when(e.scheduledAt());
            case SENT -> "Enviada";
        };
        var sent = e.sends().stream().collect(Collectors.toMap(s -> s.hash() + "|" + s.language(), s -> s));
        groups = newsletter.groups(e).stream().map(g -> {
            var items = newsletter.itemsFor(e, g.profile());
            var s = sent.get(g.hash() + "|" + g.language());
            return g.profile().canonical().replace(";", " · ") + " [" + g.language() + "] — " + g.recipients().size()
                    + " destinatario(s), " + items.size() + " item(s) visibles"
                    + (s == null ? "" : s.error() != null ? " · ERROR: " + s.error()
                    : s.itemIds().isEmpty() ? " · sin items: no se envía" : " · enviado " + Ui.when(s.sentAt()));
        }).collect(Collectors.joining("\n"));
        if (groups.isBlank()) {
            groups = "Nadie de la audiencia tiene email";
        }
        return this;
    }

    Component preview() {
        if (id == null) {
            return Html.block("");
        }
        var e = editions.find(id).orElse(null);
        if (e == null) {
            return Html.block("");
        }
        var first = newsletter.groups(e).stream().filter(g -> !newsletter.itemsFor(e, g.profile()).isEmpty()).findFirst();
        return Html.block(first.map(g -> newsletter.render(e, newsletter.itemsFor(e, g.profile()), g.language()))
                .orElse("<p>Ningún grupo vería ninguno de estos items.</p>"));
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return newsletter.compose(readers.of(httpRequest), title, intro, itemIds, audience());
    }

    @Override
    public void save(HttpRequest httpRequest) {
        newsletter.edit(readers.of(httpRequest), id, title, intro, itemIds, audience());
    }

    @Toolbar
    @Action(confirmationRequired = true, confirmationTitle = "¿Programar el envío?",
            confirmationMessage = "Se enviará en la fecha indicada (o ya, si no hay fecha), un correo por grupo de destinatarios.")
    public Object programar(HttpRequest httpRequest) {
        newsletter.schedule(readers.of(httpRequest), id, Ui.instant(scheduledAt));
        return Message.success("Programada. El envío sale en menos de un minuto desde su hora.");
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "itemIds".equals(fieldName) || super.supports(fieldType, fieldName, formType);
    }

    @Override
    public List<Option> options(String fieldName, HttpRequest httpRequest) {
        return "itemIds".equals(fieldName) ? Catalog.get().publishedItems() : super.options(fieldName, httpRequest);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nueva edición" : title;
    }
}
