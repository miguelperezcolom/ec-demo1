package io.mateu.ecdemo1.communication.ui.inbox;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The header's inbox: an icon button like the header's others, its words in Spanish in the tooltip
 * and the accessible name, the count as a pill over the bell.
 */
class InboxBadgeTest {

    @Test
    void itsLabelSaysHowManyWaitAndHowManyAreUrgentInSpanish() {
        assertThat(InboxBadge.label(0, 0)).isEqualTo("Bandeja");
        assertThat(InboxBadge.label(3, 0)).isEqualTo("Bandeja (3)");
        assertThat(InboxBadge.label(2, 1)).isEqualTo("Bandeja (2 · 1 urgente)");
        assertThat(InboxBadge.label(2, 2)).isEqualTo("Bandeja (2 · 2 urgentes)");
    }

    @Test
    void itIsATertiaryIconButtonWithAnOutlineBellNotALink() {
        var html = InboxBadge.html(2, 2);
        assertThat(html)
                .startsWith("<vaadin-button theme=\"tertiary icon\"")
                .contains("aria-label=\"Bandeja (2 · 2 urgentes)\"", "title=\"Bandeja (2 · 2 urgentes)\"")
                .contains("icon=\"vaadin:bell-o\"")
                .contains("'/inbox/pending'")
                .doesNotContain("<a ", "&nbsp;", "Inbox (", "vaadin:bell\"");
    }

    @Test
    void theCountIsAPillRedWhileSomethingUrgentWaits() {
        assertThat(InboxBadge.html(2, 1)).contains(">2</span>", "border-radius: 1em", "--lumo-error-color");
        assertThat(InboxBadge.html(3, 0)).contains(">3</span>", "--lumo-primary-color").doesNotContain("--lumo-error-color");
        assertThat(InboxBadge.html(120, 0)).contains(">99+</span>");
    }

    @Test
    void nothingWaitingIsJustTheBell() {
        assertThat(InboxBadge.html(0, 0)).doesNotContain("<span");
    }
}
