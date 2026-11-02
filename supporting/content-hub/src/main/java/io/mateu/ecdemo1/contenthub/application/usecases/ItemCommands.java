package io.mateu.ecdemo1.contenthub.application.usecases;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * The editorial workflow on items. Editors create, edit and send to review; only a publisher publishes
 * (with or without a prior review) and retires.
 */
@Service
public class ItemCommands {

    final ItemRepository items;
    final DomainEvents events;
    final EmployeeDirectory directory;
    final NamedAudiences namedAudiences;
    final Clock clock;
    /** Null where nothing remote is used (some tests): then a remote page or block is refused. */
    final RemoteUis remoteUis;

    @org.springframework.beans.factory.annotation.Autowired
    public ItemCommands(ItemRepository items, DomainEvents events, EmployeeDirectory directory,
                        NamedAudiences namedAudiences, Clock clock, RemoteUis remoteUis) {
        this.items = items;
        this.events = events;
        this.directory = directory;
        this.namedAudiences = namedAudiences;
        this.clock = clock;
        this.remoteUis = remoteUis;
    }

    public ItemCommands(ItemRepository items, DomainEvents events, EmployeeDirectory directory,
                        NamedAudiences namedAudiences, Clock clock) {
        this(items, events, directory, namedAudiences, clock, null);
    }

    @Transactional
    public String create(Reader reader, ItemType type, String defaultLanguage, ItemContent content) {
        reader.require(EditorialPermission.EDIT);
        var id = UUID.randomUUID().toString().substring(0, 8);
        checkRemotes(type, content);
        var item = Item.create(id, type, reader.userId(), defaultLanguage, sanitized(content), clock.instant());
        items.save(item);
        return id;
    }

    /** As {@link #create}, in a site: a newsletter's page or article. Its section is one of that site's. */
    @Transactional
    public String createInSite(Reader reader, String siteId, ItemType type, String defaultLanguage, ItemContent content) {
        reader.require(EditorialPermission.EDIT);
        var id = UUID.randomUUID().toString().substring(0, 8);
        items.save(Item.create(id, siteId, type, reader.userId(), defaultLanguage, sanitized(content), clock.instant()));
        return id;
    }

    /** As {@link #createInSite}, with the id given — the seed's. */
    @Transactional
    public void createWithIdInSite(String id, String siteId, String author, ItemType type, String defaultLanguage,
                                   ItemContent content) {
        items.save(Item.create(id, siteId, type, author, defaultLanguage, sanitized(content), clock.instant()));
    }

    /** As {@link #create}, with the id given — the seed's items keep theirs across resets. */
    @Transactional
    public void createWithId(String id, String author, ItemType type, String defaultLanguage, ItemContent content) {
        checkRemotes(type, content);
        var item = Item.create(id, type, author, defaultLanguage, sanitized(content), clock.instant());
        items.save(item);
    }

    @Transactional
    public void edit(Reader reader, String id, ItemContent content) {
        reader.require(EditorialPermission.EDIT);
        var item = items.find(id).orElseThrow();
        checkRemotes(item.type(), content);
        item.editDraft(sanitized(content), clock.instant());
        items.save(item);
    }

    /**
     * The page's blocks, replaced in its draft — the visual editor's save. Everything else in the draft
     * (texts, audience, dates) stays as it is; the blocks go through the same sanitising and validation as
     * the form's save. A block's audience may only narrow the page's ({@link Block#isNarrowing}).
     */
    @Transactional
    public void editBlocks(Reader reader, String pageId, List<Block> blocks) {
        reader.require(EditorialPermission.EDIT);
        var item = items.find(pageId).orElseThrow(() -> new IllegalArgumentException("No existe el contenido " + pageId));
        if (item.type() != ItemType.PAGE) {
            throw new IllegalStateException("Solo las páginas tienen bloques");
        }
        for (var block : blocks) {
            if (!Block.isNarrowing(block.audience())) {
                throw new IllegalArgumentException("La audiencia del bloque «" + (block.title() == null || block.title().isBlank()
                        ? block.id() : block.title()) + "» solo puede estrechar la de la página (departamentos, hoteles, puestos)");
            }
        }
        var d = item.draft();
        var content = new ItemContent(d.sectionId(), d.audience(), d.translations(), blocks, d.publishAt(), d.expireAt(),
                d.readTracking(), d.confirmationRequired(), d.confirmationDeadline(), d.url());
        checkRemotes(item.type(), content);
        item.editDraft(sanitized(content), clock.instant());
        items.save(item);
    }

    @Transactional
    public void submitForReview(Reader reader, String id) {
        reader.require(EditorialPermission.EDIT);
        var item = items.find(id).orElseThrow();
        item.submitForReview(clock.instant());
        items.save(item);
    }

    @Transactional
    public void reject(Reader reader, String id) {
        reader.require(EditorialPermission.PUBLISH);
        var item = items.find(id).orElseThrow();
        item.reject(clock.instant());
        items.save(item);
    }

    @Transactional
    public void publish(Reader reader, String id, boolean requireReconfirmation) {
        reader.require(EditorialPermission.PUBLISH);
        var item = items.find(id).orElseThrow();
        item.publish(reader.userId(), true, requireReconfirmation, clock.instant());
        items.save(item);
        events.drain(item);
    }

    @Transactional
    public void retire(Reader reader, String id) {
        reader.require(EditorialPermission.PUBLISH);
        var item = items.find(id).orElseThrow();
        item.retire(clock.instant());
        items.save(item);
        events.drain(item);
    }

    /**
     * What the editor is warned of before saving: an audience nobody in the directory meets, or a block
     * whose audience together with its page's admits nobody. Warnings, never refusals.
     */
    public List<String> warnings(ItemContent content) {
        var out = new ArrayList<String>();
        var policy = namedAudiences.policy();
        var employees = directory.all().stream().filter(Employee::enabled).toList();
        if (employees.isEmpty()) {
            return out;
        }
        if (employees.stream().noneMatch(e -> policy.admits(content.audience(), e.attributes()))) {
            out.add("Ningún empleado cumple la audiencia del item");
        }
        for (var block : content.blocks()) {
            var effective = Audience.effective(content.audience(), block.visibilityAudience());
            if (employees.stream().noneMatch(e -> policy.admits(effective, e.attributes()))) {
                out.add("Nadie cumple a la vez la audiencia de la página y la del bloque «"
                        + (block.title() == null || block.title().isBlank() ? block.id() : block.title()) + "»");
            }
        }
        return out;
    }

    /**
     * What an editor saves may only show registered UIs, in a way that fits each (a Mateu UI by route, a web
     * component by attributes…): editors choose among the sources, they never say where a UI lives.
     */
    void checkRemotes(ItemType type, ItemContent content) {
        var uses = new java.util.ArrayList<io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi>();
        if (type == ItemType.REMOTE_PAGE) {
            uses.add(io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi.parse(content.url())
                    .orElseThrow(() -> new IllegalArgumentException("Una pantalla externa necesita su UI externa")));
        } else if (io.mateu.ecdemo1.contenthub.domain.uisource.RemoteUi.isRemote(content.url())) {
            throw new IllegalArgumentException("Solo una pantalla externa muestra una UI externa por su URL");
        }
        content.blocks().stream().map(Block::remote).filter(java.util.Objects::nonNull).forEach(uses::add);
        if (uses.isEmpty()) {
            return;
        }
        if (remoteUis == null) {
            throw new IllegalArgumentException("No hay UIs externas registradas");
        }
        uses.forEach(remoteUis::requireRegistered);
    }

    private static ItemContent sanitized(ItemContent content) {
        var translations = new LinkedHashMap<String, Translation>();
        content.translations().forEach((lang, t) ->
                translations.put(lang, new Translation(t.title(), t.summary(), HtmlSanitizer.sanitize(t.body()))));
        var blocks = content.blocks().stream()
                .map(b -> b.withHtml(b.html() == null ? null : HtmlSanitizer.sanitize(b.html())))
                .toList();
        return new ItemContent(content.sectionId(), content.audience(), translations, blocks, content.publishAt(),
                content.expireAt(), content.readTracking(), content.confirmationRequired(),
                content.confirmationDeadline(), content.url(), content.notifyReaders(), content.important());
    }
}
