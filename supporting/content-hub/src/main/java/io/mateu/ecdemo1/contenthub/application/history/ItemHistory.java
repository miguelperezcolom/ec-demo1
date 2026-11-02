package io.mateu.ecdemo1.contenthub.application.history;

import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemHistoryRepository;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.domain.audience.NamedAudience;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.item.HistoryAction;
import io.mateu.ecdemo1.contenthub.domain.item.HistoryEntry;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * An item's history: every step (created, edited, sent to review, published, rejected, retired,
 * restored), who and when; any two of its states compared; and an earlier state restored into the draft.
 *
 * <p>What is kept: a publication's content is its version (the item has it); a save keeps the draft it
 * left (a snapshot per save that changed something — a few kB each). Publications from before the history
 * existed are read off the item's versions, so an item's history is never empty once published.
 *
 * <p>The states that can be compared and restored have a key: {@code draft} (the current draft),
 * {@code v<N>} (version N) and {@code h<seq>} (the draft a step left).
 */
@Service
@RequiredArgsConstructor
public class ItemHistory {

    public static final String DRAFT = "draft";

    final ItemHistoryRepository history;
    final ItemRepository items;
    final EmployeeDirectory directory;
    final NamedAudienceRepository namedAudiences;
    final NavigationRepository navigation;
    final Clock clock;

    /** One row of the history as the editor reads it. */
    public record Step(int seq, HistoryAction action, Instant at, String by, String byName, Integer version,
                       String note, String revision) {
    }

    /** A state that can be compared or restored. */
    public record Revision(String key, String label) {
    }

    public record Restored(String message, List<String> warnings) {
    }

    /** Records a step; the workflow calls it in its own transaction. */
    public void record(Item item, HistoryAction action, String by, Integer version, String note, ItemContent snapshot) {
        history.append(HistoryEntry.of(item.id(), action, clock.instant(), by, version, note, snapshot));
    }

    /** The item's steps, newest first. Viewing it is for any editorial permission. */
    public List<Step> timeline(Reader reader, String itemId) {
        requireEditorial(reader);
        var item = item(itemId);
        var entries = new ArrayList<>(history.of(itemId));
        var recorded = new HashSet<Integer>();
        entries.stream().filter(e -> e.action() == HistoryAction.PUBLISHED && e.version() != null)
                .forEach(e -> recorded.add(e.version()));
        for (var v : item.versions()) {
            if (!recorded.contains(v.number())) {
                entries.add(new HistoryEntry(itemId, 0, HistoryAction.PUBLISHED, v.publishedAt(), v.publishedBy(),
                        v.number(), null, null));
            }
        }
        entries.sort(Comparator.comparing(HistoryEntry::at).thenComparing(HistoryEntry::seq));
        var steps = new ArrayList<Step>();
        for (var e : entries.reversed()) {
            steps.add(new Step(e.seq(), e.action(), e.at(), e.by(), name(e.by()), e.version(), e.note(), key(e)));
        }
        return steps;
    }

    /** What can be compared: the current draft, every version, every saved draft — newest first. */
    public List<Revision> revisions(Reader reader, String itemId) {
        var out = new ArrayList<Revision>();
        out.add(new Revision(DRAFT, "Borrador actual"));
        for (var step : timeline(reader, itemId)) {
            if (step.revision() != null) {
                out.add(new Revision(step.revision(), label(step)));
            }
        }
        return out;
    }

    static String label(Step step) {
        var when = ContentDiff.WHEN.format(step.at());
        return step.version() != null
                ? "Versión " + step.version() + " · publicada el " + when + (step.byName() == null ? "" : " · " + step.byName())
                : "#" + step.seq() + " · " + step.action().label() + " · " + when + (step.byName() == null ? "" : " · " + step.byName());
    }

    /** The differences from {@code from} to {@code to} (keys of {@link #revisions}). */
    public List<ContentDiff.Change> compare(Reader reader, String itemId, String from, String to) {
        requireEditorial(reader);
        var item = item(itemId);
        var a = content(item, from).orElseThrow(() -> new IllegalArgumentException("No existe el estado " + from));
        var b = content(item, to).orElseThrow(() -> new IllegalArgumentException("No existe el estado " + to));
        return ContentDiff.compare(a, b,
                id -> namedAudiences.find(id).map(NamedAudience::name).orElse(id),
                id -> navigation.get().find(id).map(s -> s.name("es")).orElse(id));
    }

    /**
     * Copies an earlier state into the draft — never publishes: the draft then goes through review and
     * publication as any other change. A publication date already past is dropped (the restored draft
     * publishes when it is published, as a new one would); expiry or deadline dates already past are kept
     * and warned of, for the editor to decide.
     */
    @Transactional
    public Restored restore(Reader reader, String itemId, String revision) {
        reader.require(EditorialPermission.EDIT);
        if (revision == null || revision.isBlank() || DRAFT.equals(revision)) {
            throw new IllegalArgumentException("Elige una versión anterior");
        }
        var item = item(itemId);
        var content = content(item, revision).orElseThrow(() -> new IllegalArgumentException("No existe el estado " + revision));
        var now = clock.instant();
        var warnings = new ArrayList<String>();
        if (content.publishAt() != null && !content.publishAt().isAfter(now)) {
            content = content.withPublishAt(null);
        }
        if (content.expireAt() != null && !content.expireAt().isAfter(now)) {
            warnings.add("La caducidad restaurada (" + ContentDiff.WHEN.format(content.expireAt()) + ") ya ha pasado: cámbiala antes de publicar");
        }
        if (content.confirmationRequired() && content.confirmationDeadline() != null && !content.confirmationDeadline().isAfter(now)) {
            warnings.add("La fecha límite de confirmación restaurada ya ha pasado");
        }
        var what = describe(revision);
        item.editDraft(content, now);
        items.save(item);
        record(item, HistoryAction.RESTORED, reader.userId(), null, what, content);
        return new Restored("Restaurada la " + what + " en el borrador. Revísalo y sigue el flujo de siempre (revisión, publicación).",
                warnings);
    }

    static String describe(String revision) {
        return revision.startsWith("v") ? "versión " + revision.substring(1) : "copia #" + revision.substring(1);
    }

    Optional<ItemContent> content(Item item, String key) {
        if (key == null) {
            return Optional.empty();
        }
        if (DRAFT.equals(key)) {
            return Optional.of(item.draft());
        }
        try {
            if (key.startsWith("v")) {
                var n = Integer.parseInt(key.substring(1));
                return item.versions().stream().filter(v -> v.number() == n).findFirst().map(v -> v.content());
            }
            if (key.startsWith("h")) {
                var seq = Integer.parseInt(key.substring(1));
                return history.of(item.id()).stream().filter(e -> e.seq() == seq && e.snapshot() != null)
                        .findFirst().map(HistoryEntry::snapshot);
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    static String key(HistoryEntry e) {
        if (e.action() == HistoryAction.PUBLISHED && e.version() != null) {
            return "v" + e.version();
        }
        return e.snapshot() != null && e.seq() > 0 ? "h" + e.seq() : null;
    }

    String name(String userId) {
        if (userId == null) {
            return null;
        }
        return directory.find(userId).map(Employee::name).filter(n -> !n.isBlank()).orElse(userId);
    }

    Item item(String itemId) {
        return items.find(itemId).orElseThrow(() -> new IllegalArgumentException("No existe el contenido " + itemId));
    }

    static void requireEditorial(Reader reader) {
        if (reader.permissions().isEmpty()) {
            throw new SecurityException("Hace falta un permiso editorial");
        }
    }
}
