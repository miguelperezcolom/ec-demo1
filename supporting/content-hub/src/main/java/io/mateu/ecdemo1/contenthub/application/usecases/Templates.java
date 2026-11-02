package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.TemplateRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.site.Site;
import io.mateu.ecdemo1.contenthub.domain.template.PageTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Page templates. Using one — a new page from it — is an editor's ({@code content:edit}): it only makes a
 * draft. Defining them is a publisher's ({@code content:publish}, or admin): a template is what every
 * editor starts from, so it is curated by whoever decides what is published, like slot content.
 */
@Service
@RequiredArgsConstructor
public class Templates {

    final TemplateRepository templates;
    final ItemRepository items;
    final ItemCommands itemCommands;
    final Clock clock;

    public List<PageTemplate> all() {
        return templates.findAll();
    }

    /** The templates a new page of this site may start from: the global ones and the site's own. */
    public List<PageTemplate> availableIn(String siteId) {
        return templates.findAll().stream().filter(t -> t.availableIn(siteId)).toList();
    }

    public Optional<PageTemplate> find(String id) {
        return templates.find(id);
    }

    @Transactional
    public String create(Reader reader, String name, String description, List<Block> blocks, Audience defaultAudience,
                         String siteId) {
        reader.require(EditorialPermission.PUBLISH);
        var id = "t-" + UUID.randomUUID().toString().substring(0, 8);
        var template = PageTemplate.create(id, name, description, blocks, defaultAudience, reader.userId(), clock.instant());
        template.restrictTo(siteId);
        templates.save(template);
        return id;
    }

    @Transactional
    public void edit(Reader reader, String id, String name, String description, List<Block> blocks, Audience defaultAudience,
                     String siteId) {
        reader.require(EditorialPermission.PUBLISH);
        var template = templates.find(id).orElseThrow(() -> new IllegalArgumentException("No existe la plantilla " + id));
        template.edit(name, description, blocks, defaultAudience, reader.userId(), clock.instant());
        template.restrictTo(siteId);
        templates.save(template);
    }

    @Transactional
    public void delete(Reader reader, String id) {
        reader.require(EditorialPermission.PUBLISH);
        templates.delete(id);
    }

    /** «Guardar como plantilla»: the page draft's blocks and audience, as a new template. */
    @Transactional
    public String saveFromPage(Reader reader, String pageId, String name, String description) {
        reader.require(EditorialPermission.PUBLISH);
        var page = items.find(pageId).orElseThrow(() -> new IllegalArgumentException("No existe el contenido " + pageId));
        var id = "t-" + UUID.randomUUID().toString().substring(0, 8);
        templates.save(PageTemplate.fromPage(id, page, name, description, reader.userId(), clock.instant()));
        return id;
    }

    /**
     * «Nueva página desde plantilla»: a PAGE draft of a site (null: the intranet) with the template's blocks
     * and default audience and the title given — through {@link ItemCommands}, so it is sanitised and
     * authored like any other. A template of another site is refused.
     */
    @Transactional
    public String newPage(Reader reader, String templateId, String title, String sectionId, String siteId) {
        reader.require(EditorialPermission.EDIT);
        var template = templates.find(templateId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la plantilla " + templateId));
        var site = Site.of(siteId);
        if (!template.availableIn(site)) {
            throw new IllegalArgumentException("La plantilla «" + template.name() + "» es del sitio " + template.siteId()
                    + ", no de " + site);
        }
        var content = template.newPageContent(title, "es", sectionId);
        return Site.INTRANET.equals(site)
                ? itemCommands.create(reader, ItemType.PAGE, "es", content)
                : itemCommands.createInSite(reader, site, ItemType.PAGE, "es", content);
    }

    /** The seed's: with its id, once, by no one in particular. */
    @Transactional
    public void seed(PageTemplate template) {
        if (templates.find(template.id()).isEmpty()) {
            templates.save(template);
        }
    }
}
