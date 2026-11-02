package io.mateu.ecdemo1.contenthub.domain;

import io.mateu.ecdemo1.contenthub.domain.audience.Attribute;
import io.mateu.ecdemo1.contenthub.domain.audience.Audience;
import io.mateu.ecdemo1.contenthub.domain.item.Block;
import io.mateu.ecdemo1.contenthub.domain.item.BlockType;
import io.mateu.ecdemo1.contenthub.domain.navigation.Section;
import io.mateu.ecdemo1.contenthub.domain.newsletter.AutomaticContent;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Cadence;
import io.mateu.ecdemo1.contenthub.domain.newsletter.Newsletter;
import io.mateu.ecdemo1.contenthub.domain.newsletter.NewsletterIssue;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsletterTest {

    /** Monday 2 November 2026, 10:00 in Madrid (09:00 UTC). */
    static final Instant MONDAY_10 = Instant.parse("2026-11-02T09:00:00Z");
    static final Cadence WEEKLY = Cadence.weekly(DayOfWeek.MONDAY, LocalTime.of(9, 0));

    static Newsletter newsletter(Cadence cadence, boolean autoSend, Instant now) {
        var layout = Newsletter.Settings.defaultLayout(List.of(new Section("editorial", null, 0, Map.of("es", "Editorial")),
                new Section("noticias", null, 1, Map.of("es", "Noticias"))));
        return Newsletter.create("boletin", new Newsletter.Settings(layout,
                Map.of("noticias", List.of("rrhh", "operaciones")), cadence, autoSend), now);
    }

    // ── cadence ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void a_weekly_cadence_has_one_slot_a_week_madrid_time() {
        // Monday 08:59 Madrid: this week's slot has not come; the last one is the previous Monday
        var before = Instant.parse("2026-11-02T07:59:00Z");
        assertThat(WEEKLY.slotAtOrBefore(before)).contains(Instant.parse("2026-10-26T08:00:00Z"));
        assertThat(WEEKLY.slotAtOrBefore(MONDAY_10)).contains(Instant.parse("2026-11-02T08:00:00Z"));
        assertThat(WEEKLY.nextSlotAfter(MONDAY_10)).contains(Instant.parse("2026-11-09T08:00:00Z"));
        // across the change of time (25 October 2026): still 09:00 in Madrid
        assertThat(WEEKLY.slotAtOrBefore(Instant.parse("2026-10-20T12:00:00Z"))).contains(Instant.parse("2026-10-19T07:00:00Z"));
        assertThat(Cadence.NONE.slotAtOrBefore(MONDAY_10)).isEmpty();
    }

    @Test
    void a_monthly_cadence_uses_the_last_day_of_a_shorter_month() {
        var on31 = Cadence.monthly(31, LocalTime.of(9, 0));
        assertThat(on31.nextSlotAfter(Instant.parse("2026-11-02T09:00:00Z"))).contains(Instant.parse("2026-11-30T08:00:00Z"));
        assertThat(on31.slotAtOrBefore(Instant.parse("2026-11-02T09:00:00Z"))).contains(Instant.parse("2026-10-31T08:00:00Z"));
        assertThatThrownBy(() -> Cadence.monthly(32, LocalTime.NOON)).isInstanceOf(IllegalArgumentException.class);
        assertThat(on31.describe()).isEqualTo("Mensual, el día 31 a las 09:00");
        assertThat(WEEKLY.describe()).isEqualTo("Semanal, los lunes a las 09:00");
    }

    // ── slots, numbering, idempotency ───────────────────────────────────────────────────────────

    @Test
    void the_slot_that_had_passed_when_it_was_created_is_not_its_own() {
        var n = newsletter(WEEKLY, false, MONDAY_10);
        assertThat(n.dueSlot(MONDAY_10)).isEmpty();
        assertThat(n.dueSlot(MONDAY_10.plus(Duration.ofDays(6)))).isEmpty();
        var nextMonday = Instant.parse("2026-11-09T08:00:00Z");
        assertThat(n.dueSlot(nextMonday)).contains(nextMonday);
    }

    @Test
    void a_slot_opens_one_issue_ever_and_issues_are_numbered_in_order() {
        var n = newsletter(WEEKLY, false, MONDAY_10);
        var slot = Instant.parse("2026-11-09T08:00:00Z");
        var first = n.openIssue("Boletín", n.dueSlot(slot.plusSeconds(60)).orElseThrow(), Map.of(), slot.plusSeconds(60));
        assertThat(first.number()).isEqualTo(1);
        assertThat(first.id()).isEqualTo("boletin-20261109-0900");
        assertThat(first.subject()).isEqualTo("Boletín · nº 1");
        assertThat(first.status()).isEqualTo(NewsletterIssue.Status.DRAFT);
        // the same slot, again: nothing due, and refused if forced
        assertThat(n.dueSlot(slot.plusSeconds(120))).isEmpty();
        assertThatThrownBy(() -> n.openIssue("Boletín", slot, Map.of(), slot)).isInstanceOf(IllegalStateException.class);
        // a manual issue in between takes the next number; the next slot, the one after
        var manual = n.openIssue("Boletín", null, Map.of(), slot.plusSeconds(3600));
        assertThat(manual.number()).isEqualTo(2);
        assertThat(manual.id()).isEqualTo("boletin-n2");
        var later = Instant.parse("2026-11-16T08:00:00Z");
        assertThat(n.openIssue("Boletín", n.dueSlot(later).orElseThrow(), Map.of(), later).number()).isEqualTo(3);
    }

    @Test
    void three_weeks_down_open_one_issue_not_three() {
        var n = newsletter(WEEKLY, false, MONDAY_10);
        var back = Instant.parse("2026-11-25T10:00:00Z"); // after the 9th, 16th and 23rd
        assertThat(n.dueSlot(back)).contains(Instant.parse("2026-11-23T08:00:00Z"));
        n.openIssue("Boletín", n.dueSlot(back).orElseThrow(), Map.of(), back);
        assertThat(n.dueSlot(back)).isEmpty();
    }

    @Test
    void a_new_cadence_starts_from_now() {
        var n = newsletter(Cadence.NONE, false, MONDAY_10);
        assertThat(n.dueSlot(MONDAY_10.plus(Duration.ofDays(30)))).isEmpty();
        var tuesday = Instant.parse("2026-11-03T12:00:00Z");
        n.configure(new Newsletter.Settings(n.settings().layout(), n.settings().sources(),
                Cadence.weekly(DayOfWeek.TUESDAY, LocalTime.of(9, 0)), false), tuesday);
        assertThat(n.dueSlot(tuesday)).isEmpty(); // this Tuesday's 09:00 had passed when it was set
        assertThat(n.dueSlot(Instant.parse("2026-11-10T08:00:00Z"))).isPresent();
    }

    @Test
    void the_layout_is_copied_into_each_issue_and_the_items_go_by_section() {
        var n = newsletter(Cadence.NONE, false, MONDAY_10);
        assertThat(n.settings().layout()).extracting(Block::type)
                .containsExactly(BlockType.HTML, BlockType.NEWSLETTER_SECTION, BlockType.NEWSLETTER_SECTION);
        var issue = n.openIssue("Boletín", null, Map.of("noticias", List.of("a", "b")), MONDAY_10);
        assertThat(issue.blocks()).hasSize(3).allSatisfy(b -> assertThat(b.id()).endsWith("-1"));
        assertThat(Newsletter.sectionOf(issue.blocks().get(2))).isEqualTo("noticias");
        assertThat(issue.itemIds()).containsExactly("a", "b");
        assertThat(issue.addItems(Map.of("noticias", List.of("b", "c"), "editorial", List.of("a")), MONDAY_10)).isEqualTo(1);
        assertThat(issue.section("noticias")).containsExactly("a", "b", "c");
    }

    @Test
    void an_intranet_item_goes_to_the_first_section_that_takes_its_section_or_an_ancestor() {
        var n = newsletter(Cadence.NONE, false, MONDAY_10);
        var sections = List.of("editorial", "noticias");
        assertThat(n.sectionFor(false, "nominas", List.of("rrhh"), sections)).contains("noticias");
        assertThat(n.sectionFor(false, "cocina", List.of(), sections)).isEmpty();
        // its own items: their own section
        assertThat(n.sectionFor(true, "editorial", List.of(), sections)).contains("editorial");
        assertThat(n.sectionFor(true, "otra", List.of(), sections)).isEmpty();
    }

    // ── an issue's life ─────────────────────────────────────────────────────────────────────────

    @Test
    void draft_scheduled_sent_and_cancelled() {
        var n = newsletter(Cadence.NONE, false, MONDAY_10);
        var issue = n.openIssue("Boletín", null, Map.of("noticias", List.of("a")), MONDAY_10);
        issue.schedule(MONDAY_10);
        assertThat(issue.due(MONDAY_10)).isTrue();
        issue.unschedule();
        assertThat(issue.status()).isEqualTo(NewsletterIssue.Status.DRAFT);
        issue.schedule(MONDAY_10);
        issue.recordGroup(new NewsletterIssue.GroupSend("h", "es", 3, List.of("a"), MONDAY_10, null));
        assertThatThrownBy(issue::cancel).hasMessageContaining("salido");
        issue.markSent(MONDAY_10);
        assertThat(issue.status()).isEqualTo(NewsletterIssue.Status.SENT);
        assertThat(issue.sentAt()).isEqualTo(MONDAY_10);
        assertThatThrownBy(() -> issue.edit("Otro", null, List.of(), Map.of(), MONDAY_10)).isInstanceOf(IllegalStateException.class);

        var other = n.openIssue("Boletín", null, Map.of(), MONDAY_10);
        other.cancel();
        assertThat(other.status()).isEqualTo(NewsletterIssue.Status.CANCELLED);
        assertThatThrownBy(() -> other.schedule(MONDAY_10)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void an_empty_issue_is_not_scheduled_and_a_block_only_narrows() {
        var n = Newsletter.create("vacia", new Newsletter.Settings(List.of(Newsletter.sectionBlock("s", "noticias", "")),
                Map.of(), Cadence.NONE, false), MONDAY_10);
        var issue = n.openIssue("Vacía", null, Map.of(), MONDAY_10);
        assertThatThrownBy(() -> issue.schedule(MONDAY_10)).hasMessageContaining("vacío");
        var wide = new Block("b", BlockType.HTML, new Audience.Named("todos"), "", "<p>x</p>", null, null, null, null, null);
        assertThatThrownBy(() -> issue.edit("Vacía", null, List.of(wide), Map.of(), MONDAY_10)).hasMessageContaining("estrechar");
        var narrow = new Block("b", BlockType.HTML, Audience.in(Attribute.DEPARTMENT, "COCINA"), "", "<p>x</p>", null, null,
                null, null, null);
        issue.edit("Vacía", null, List.of(narrow), Map.of(), MONDAY_10);
        issue.schedule(MONDAY_10);
        assertThat(issue.status()).isEqualTo(NewsletterIssue.Status.SCHEDULED);
    }

    // ── automatic content ───────────────────────────────────────────────────────────────────────

    @Test
    void automatic_items_are_those_published_since_the_previous_issue_seen_by_the_audience() {
        var n = newsletter(WEEKLY, false, MONDAY_10);
        var previous = n.openIssue("Boletín", null, Map.of(), MONDAY_10);
        var now = MONDAY_10.plus(Duration.ofDays(7));
        var since = AutomaticContent.since(previous, n.settings().cadence(), now);
        assertThat(since).isEqualTo(MONDAY_10);
        assertThat(AutomaticContent.since(null, n.settings().cadence(), now)).isEqualTo(now.minus(Duration.ofDays(7)));
        var day = Duration.ofDays(1);
        var candidates = List.of(
                new AutomaticContent.Candidate("old", "intranet", "rrhh", MONDAY_10.minus(day), null),
                new AutomaticContent.Candidate("new1", "intranet", "nominas", MONDAY_10.plus(day), null),
                new AutomaticContent.Candidate("new2", "intranet", "operaciones", MONDAY_10.plus(day.multipliedBy(2)), null),
                new AutomaticContent.Candidate("hidden", "intranet", "rrhh", MONDAY_10.plus(day), null),
                new AutomaticContent.Candidate("expired", "intranet", "rrhh", MONDAY_10.plus(day), MONDAY_10.plus(day.multipliedBy(3))),
                new AutomaticContent.Candidate("future", "intranet", "rrhh", now.plus(day), null),
                new AutomaticContent.Candidate("elsewhere", "intranet", "cocina", MONDAY_10.plus(day), null),
                new AutomaticContent.Candidate("own", "boletin", "editorial", MONDAY_10.plus(day), null),
                new AutomaticContent.Candidate("otherSite", "otro", "editorial", MONDAY_10.plus(day), null));
        var picked = AutomaticContent.pick(n, List.of("editorial", "noticias"), candidates, since, now,
                s -> "nominas".equals(s) ? List.of("rrhh") : List.of(), id -> !Set.of("hidden").contains(id));
        assertThat(picked.get("noticias")).containsExactly("new2", "new1"); // newest first
        assertThat(picked.get("editorial")).containsExactly("own");
    }
}
