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
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ItemCommands {

    final ItemRepository items;
    final DomainEvents events;
    final EmployeeDirectory directory;
    final NamedAudiences namedAudiences;
    final Clock clock;

    @Transactional
    public String create(Reader reader, ItemType type, String defaultLanguage, ItemContent content) {
        reader.require(EditorialPermission.EDIT);
        var id = UUID.randomUUID().toString().substring(0, 8);
        var item = Item.create(id, type, reader.userId(), defaultLanguage, sanitized(content), clock.instant());
        items.save(item);
        return id;
    }

    /** As {@link #create}, with the id given — the seed's items keep theirs across resets. */
    @Transactional
    public void createWithId(String id, String author, ItemType type, String defaultLanguage, ItemContent content) {
        var item = Item.create(id, type, author, defaultLanguage, sanitized(content), clock.instant());
        items.save(item);
    }

    @Transactional
    public void edit(Reader reader, String id, ItemContent content) {
        reader.require(EditorialPermission.EDIT);
        var item = items.find(id).orElseThrow();
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
            var effective = Audience.effective(content.audience(), block.audience());
            if (employees.stream().noneMatch(e -> policy.admits(effective, e.attributes()))) {
                out.add("Nadie cumple a la vez la audiencia de la página y la del bloque «"
                        + (block.title() == null || block.title().isBlank() ? block.id() : block.title()) + "»");
            }
        }
        return out;
    }

    private static ItemContent sanitized(ItemContent content) {
        var translations = new LinkedHashMap<String, Translation>();
        content.translations().forEach((lang, t) ->
                translations.put(lang, new Translation(t.title(), t.summary(), HtmlSanitizer.sanitize(t.body()))));
        var blocks = content.blocks().stream()
                .map(b -> new Block(b.id(), b.type(), b.audience(), b.title(),
                        b.html() == null ? null : HtmlSanitizer.sanitize(b.html()), b.itemId(), b.sectionId(),
                        b.limit(), b.links(), b.mediaId()))
                .toList();
        return new ItemContent(content.sectionId(), content.audience(), translations, blocks, content.publishAt(),
                content.expireAt(), content.readTracking(), content.confirmationRequired(),
                content.confirmationDeadline(), content.url());
    }
}
