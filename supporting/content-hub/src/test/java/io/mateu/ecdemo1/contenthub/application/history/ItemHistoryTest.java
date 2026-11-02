package io.mateu.ecdemo1.contenthub.application.history;

import io.mateu.ecdemo1.contenthub.application.events.DomainEvents;
import io.mateu.ecdemo1.contenthub.application.out.EmployeeDirectory;
import io.mateu.ecdemo1.contenthub.application.out.ItemHistoryRepository;
import io.mateu.ecdemo1.contenthub.application.out.ItemRepository;
import io.mateu.ecdemo1.contenthub.application.out.NamedAudienceRepository;
import io.mateu.ecdemo1.contenthub.application.out.NavigationRepository;
import io.mateu.ecdemo1.contenthub.application.security.EditorialPermission;
import io.mateu.ecdemo1.contenthub.application.security.Reader;
import io.mateu.ecdemo1.contenthub.application.usecases.ItemCommands;
import io.mateu.ecdemo1.contenthub.application.visibility.NamedAudiences;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.employee.Employee;
import io.mateu.ecdemo1.contenthub.domain.employee.EmployeeAttributes;
import io.mateu.ecdemo1.contenthub.domain.item.HistoryAction;
import io.mateu.ecdemo1.contenthub.domain.item.HistoryEntry;
import io.mateu.ecdemo1.contenthub.domain.item.Item;
import io.mateu.ecdemo1.contenthub.domain.item.ItemContent;
import io.mateu.ecdemo1.contenthub.domain.item.ItemStatus;
import io.mateu.ecdemo1.contenthub.domain.item.ItemType;
import io.mateu.ecdemo1.contenthub.domain.item.Translation;
import io.mateu.ecdemo1.contenthub.domain.navigation.Navigation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The history as the workflow records it, the comparison of two of its states, and restoring one. */
class ItemHistoryTest {

    static final Instant T0 = Instant.parse("2026-11-02T09:00:00Z");
    static final Reader EDITOR = new Reader("eva", "Eva", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.EDIT), null);
    static final Reader PUBLISHER = new Reader("pablo", "Pablo", EmployeeAttributes.NONE, EnumSet.of(EditorialPermission.PUBLISH), null);
    static final Reader NOBODY = new Reader("lola", "Lola", EmployeeAttributes.NONE, EnumSet.noneOf(EditorialPermission.class), null);

    /** A clock the test moves forward. */
    static class MovingClock extends Clock {
        Instant now = T0;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    /** The store, in memory. */
    static class MemoryHistory implements ItemHistoryRepository {
        final List<HistoryEntry> rows = new ArrayList<>();

        @Override
        public HistoryEntry append(HistoryEntry entry) {
            var seq = (int) rows.stream().filter(r -> r.itemId().equals(entry.itemId())).count() + 1;
            var numbered = entry.withSeq(seq);
            rows.add(numbered);
            return numbered;
        }

        @Override
        public List<HistoryEntry> of(String itemId) {
            return rows.stream().filter(r -> r.itemId().equals(itemId)).toList();
        }
    }

    final Map<String, Item> stored = new HashMap<>();
    final MemoryHistory rows = new MemoryHistory();
    final MovingClock clock = new MovingClock();
    ItemCommands commands;
    ItemHistory history;

    @BeforeEach
    void setUp() {
        var items = mock(ItemRepository.class);
        when(items.find(any())).thenAnswer(i -> Optional.ofNullable(stored.get(i.<String>getArgument(0))));
        doAnswer(i -> stored.put(i.<Item>getArgument(0).id(), i.getArgument(0))).when(items).save(any());
        var directory = mock(EmployeeDirectory.class);
        when(directory.find(any())).thenReturn(Optional.empty());
        when(directory.find("eva")).thenReturn(Optional.of(new Employee("eva", "Eva García", "eva@x", true, EmployeeAttributes.NONE)));
        var navigation = mock(NavigationRepository.class);
        when(navigation.get()).thenReturn(Navigation.empty());
        history = new ItemHistory(rows, items, directory, mock(NamedAudienceRepository.class), navigation, clock);
        commands = new ItemCommands(items, mock(DomainEvents.class), directory, mock(NamedAudiences.class), clock);
        commands.setHistory(history);
    }

    static ItemContent content(String title, String body, Instant publishAt, Instant expireAt) {
        return new ItemContent("noticias", Audience.EVERYONE, Map.of("es", new Translation(title, "", body)), List.of(),
                publishAt, expireAt, false, false, null, null);
    }

    String createPublishAndEdit() {
        var id = commands.create(EDITOR, ItemType.ARTICLE, "es", content("Turnos", "<p>Lunes</p>", null, null));
        clock.advance(Duration.ofMinutes(5));
        commands.submitForReview(EDITOR, id);
        clock.advance(Duration.ofMinutes(5));
        commands.publish(PUBLISHER, id, false);
        clock.advance(Duration.ofHours(1));
        commands.edit(EDITOR, id, content("Turnos de verano", "<p>Martes</p>", null, null));
        clock.advance(Duration.ofMinutes(1));
        // a save that changes nothing is no step
        commands.edit(EDITOR, id, content("Turnos de verano", "<p>Martes</p>", null, null));
        return id;
    }

    @Test
    void the_workflow_records_every_step_newest_first() {
        var id = createPublishAndEdit();
        clock.advance(Duration.ofMinutes(1));
        commands.submitForReview(EDITOR, id);
        commands.reject(PUBLISHER, id);
        commands.retire(PUBLISHER, id);

        var steps = history.timeline(EDITOR, id);
        assertThat(steps).extracting(ItemHistory.Step::action).containsExactly(HistoryAction.RETIRED,
                HistoryAction.REJECTED, HistoryAction.SENT_TO_REVIEW, HistoryAction.EDITED, HistoryAction.PUBLISHED,
                HistoryAction.SENT_TO_REVIEW, HistoryAction.CREATED);
        var published = steps.get(4);
        assertThat(published.version()).isEqualTo(1);
        assertThat(published.revision()).isEqualTo("v1");
        assertThat(published.by()).isEqualTo("pablo");
        assertThat(steps.get(3).revision()).isEqualTo("h4");
        assertThat(steps.get(3).byName()).isEqualTo("Eva García");
        assertThat(steps.get(5).revision()).as("a step without a draft of its own").isNull();
    }

    @Test
    void publications_from_before_the_history_are_read_off_the_versions() {
        var item = Item.create("old", ItemType.NOTICE, "eva", "es", content("Viejo", "", null, null), T0);
        item.publish("pablo", true, false, T0);
        stored.put("old", item);
        assertThat(history.timeline(EDITOR, "old")).singleElement().satisfies(s -> {
            assertThat(s.action()).isEqualTo(HistoryAction.PUBLISHED);
            assertThat(s.version()).isEqualTo(1);
            assertThat(s.seq()).isZero();
        });
        assertThat(history.revisions(EDITOR, "old")).extracting(ItemHistory.Revision::key).containsExactly("draft", "v1");
    }

    @Test
    void compares_a_version_with_the_current_draft() {
        var id = createPublishAndEdit();
        var changes = history.compare(EDITOR, id, "v1", ItemHistory.DRAFT);
        assertThat(changes).extracting(ContentDiff.Change::field).containsExactly("Título", "Cuerpo", "Publicar a partir de");
        assertThat(history.compare(EDITOR, id, "h1", "h4")).extracting(ContentDiff.Change::field).containsExactly("Título", "Cuerpo");
        assertThatThrownBy(() -> history.compare(NOBODY, id, "v1", "draft")).isInstanceOf(SecurityException.class);
    }

    @Test
    void restoring_copies_into_the_draft_and_never_publishes() {
        var id = createPublishAndEdit();
        clock.advance(Duration.ofMinutes(1));
        commands.submitForReview(EDITOR, id);
        assertThat(stored.get(id).draftInReview()).isTrue();

        var restored = history.restore(EDITOR, id, "v1");

        var item = stored.get(id);
        assertThat(restored.message()).contains("versión 1");
        assertThat(item.draft().translations().get("es").title()).isEqualTo("Turnos");
        assertThat(item.draft().publishAt()).as("a publish-from already past publishes at the next publication").isNull();
        assertThat(item.versions()).as("nothing published").hasSize(1);
        assertThat(item.status()).isEqualTo(ItemStatus.PUBLISHED);
        assertThat(item.publishedVersion().orElseThrow().content().translations().get("es").title()).isEqualTo("Turnos");
        assertThat(item.draftChanged()).isTrue();
        assertThat(item.draftInReview()).as("the restored draft goes through review again").isFalse();
        var last = history.timeline(EDITOR, id).getFirst();
        assertThat(last.action()).isEqualTo(HistoryAction.RESTORED);
        assertThat(last.note()).isEqualTo("versión 1");
        assertThat(last.by()).isEqualTo("eva");
        assertThat(last.revision()).isNotNull();
        // and then the normal workflow
        commands.publish(PUBLISHER, id, false);
        assertThat(stored.get(id).versions()).hasSize(2);
    }

    @Test
    void restoring_a_saved_draft_keeps_its_future_dates_and_warns_of_past_ones() {
        var future = T0.plus(Duration.ofDays(10));
        var id = commands.create(EDITOR, ItemType.ARTICLE, "es", content("Plan", "", future, T0.plus(Duration.ofHours(2))));
        clock.advance(Duration.ofDays(1));
        commands.edit(EDITOR, id, content("Plan B", "", null, null));

        var restored = history.restore(EDITOR, id, "h1");

        var draft = stored.get(id).draft();
        assertThat(draft.translations().get("es").title()).isEqualTo("Plan");
        assertThat(draft.publishAt()).isEqualTo(future);
        assertThat(restored.warnings()).singleElement().asString().contains("caducidad");
        assertThat(stored.get(id).status()).isEqualTo(ItemStatus.DRAFT);
    }

    @Test
    void restoring_needs_content_edit_and_an_earlier_state() {
        var id = createPublishAndEdit();
        assertThatThrownBy(() -> history.restore(PUBLISHER, id, "v1")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> history.restore(EDITOR, id, "draft")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.restore(EDITOR, id, "v9")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.restore(EDITOR, id, "h2")).as("a step without a draft").isInstanceOf(IllegalArgumentException.class);
    }
}
