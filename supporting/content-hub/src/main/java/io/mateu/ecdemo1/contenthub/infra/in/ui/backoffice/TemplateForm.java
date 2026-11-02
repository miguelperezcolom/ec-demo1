package io.mateu.ecdemo1.contenthub.infra.in.ui.backoffice;

import io.mateu.ecdemo1.contenthub.application.usecases.AudienceCommands;
import io.mateu.ecdemo1.contenthub.application.usecases.Templates;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.template.PageTemplate;
import io.mateu.ecdemo1.contenthub.infra.in.security.Publishers;
import io.mateu.ecdemo1.contenthub.infra.in.security.Readers;
import io.mateu.uidl.annotations.Action;
import io.mateu.uidl.annotations.Colspan;
import io.mateu.uidl.annotations.Help;
import io.mateu.uidl.annotations.HiddenInCreate;
import io.mateu.uidl.annotations.Label;
import io.mateu.uidl.annotations.ReadOnly;
import io.mateu.uidl.annotations.Section;
import io.mateu.uidl.annotations.Stereotype;
import io.mateu.uidl.annotations.Toolbar;
import io.mateu.uidl.data.FieldStereotype;
import io.mateu.uidl.data.UICommand;
import io.mateu.uidl.interfaces.HttpRequest;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * A page template: its name, what it is for, its blocks (the same form as a page's, with placeholder
 * texts) and, in «Audiencia», the audience a page made from it starts with (empty: everyone).
 */
@Service
@Scope("prototype")
@Publishers
public class TemplateForm extends AudienceForm implements SimpleCrud.CrudForm {

    @Section("Plantilla")
    @ReadOnly
    @HiddenInCreate
    @Label("Id")
    String id;
    @NotEmpty
    @Label("Nombre")
    String name;
    @Label("Para qué sirve")
    @Stereotype(FieldStereotype.textarea)
    @Colspan(2)
    String description;
    @Label("Sitio (vacío: todos)")
    @Help("Una plantilla global sirve en la intranet y en cada newsletter; una de un sitio, solo en él.")
    @Stereotype(FieldStereotype.select)
    String siteId;

    @Section("Bloques")
    @Label("Bloques (con textos de ejemplo que el editor cambia)")
    @Help("La audiencia de la sección «Audiencia» es la que tendrá de partida la página nueva; vacía, todos.")
    List<BlockForm> blocks = new ArrayList<>();

    final Templates templates;
    final AudienceCommands audienceCommands;
    final NamedAudiences namedAudiences;
    final Readers readers;
    final io.mateu.ecdemo1.contenthub.application.out.SiteRepository sites;

    public TemplateForm(Templates templates, AudienceCommands audienceCommands, NamedAudiences namedAudiences,
                        Readers readers, io.mateu.ecdemo1.contenthub.application.out.SiteRepository sites) {
        this.sites = sites;
        this.templates = templates;
        this.audienceCommands = audienceCommands;
        this.namedAudiences = namedAudiences;
        this.readers = readers;
    }

    public TemplateForm load(PageTemplate t) {
        id = t.id();
        name = t.name();
        description = t.description();
        siteId = t.siteId();
        blocks = new ArrayList<>(t.blocks().stream().map(b -> BlockForm.of(b, PageTemplate.LANGUAGE)).toList());
        audience(t.defaultAudience() == null ? Audience.EVERYONE : t.defaultAudience(), audienceCommands, namedAudiences);
        return this;
    }

    List<io.mateu.ecdemo1.contenthub.domain.item.Block> blockList() {
        return blocks == null ? List.of() : blocks.stream().map(b -> b.toBlock(PageTemplate.LANGUAGE)).toList();
    }

    @Override
    public String create(HttpRequest httpRequest) {
        return templates.create(readers.of(httpRequest), name, description, blockList(), audience(), siteId);
    }

    @Override
    public void save(HttpRequest httpRequest) {
        templates.edit(readers.of(httpRequest), id, name, description, blockList(), audience(), siteId);
    }

    /** To «Nueva página desde plantilla», where any editor makes a page from it. */
    @Toolbar
    @Action
    @Label("Nueva página desde plantilla")
    public Object nuevaPagina(HttpRequest httpRequest) {
        return UICommand.navigateTo(NewPageFromTemplatePage.ROUTE);
    }

    @Override
    public boolean supports(Class<?> fieldType, String fieldName, Class<?> formType) {
        return "siteId".equals(fieldName) || super.supports(fieldType, fieldName, formType);
    }

    @Override
    public List<io.mateu.uidl.data.Option> options(String fieldName, HttpRequest httpRequest) {
        return "siteId".equals(fieldName) ? NewPageFromTemplatePage.siteOptions(sites) : super.options(fieldName, httpRequest);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String toString() {
        return id == null ? "Nueva plantilla" : name;
    }
}
