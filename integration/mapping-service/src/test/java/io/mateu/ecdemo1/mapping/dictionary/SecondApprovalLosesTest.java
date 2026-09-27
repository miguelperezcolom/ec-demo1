package io.mateu.ecdemo1.mapping.dictionary;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.outbox.Outbox;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One version in force per code and scope, when two people approve at once. The version in force is
 * superseded and flushed before the new one comes in (the index is checked row by row); the approval
 * that loses the race is refused by the index, and gets a clear conflict — not a constraint error —
 * having changed nothing and resumed nothing.
 */
class SecondApprovalLosesTest {

    static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    final MappingEntryRepository entries = mock(MappingEntryRepository.class);
    /** Who resolved the causes of which code: an approval that lost resolves none. */
    final List<String> resolved = new ArrayList<>();
    final Causes causes = new Causes(null, null, null, null, null, null) {
        @Override
        public void mappingApproved(CodeType type, String hotelCode, String code, String approvedBy) {
            resolved.add(type + " " + code + " " + hotelCode + " by " + approvedBy);
        }
    };
    final Outbox outbox = new Outbox(null, null, null, null) {
        @Override
        public void appendResolution(String subject, String by) {
        }
    };
    final Dictionary dictionary = new Dictionary(entries, causes, Clock.fixed(NOW, ZoneOffset.UTC), outbox);

    static MappingEntry proposal(String id, String target) {
        return MappingEntry.proposed(id, CodeType.ROOM_TYPE, "MRU01", "DBL", target, Map.of(), "agent", null, null, NOW);
    }

    @Test
    void theSecondApprovalOfTheSameCodeLosesWithAClearConflictAndChangesNothing() {
        var first = proposal("a", "XDBL");
        var second = proposal("b", "YDBL");
        when(entries.lockById("a")).thenReturn(Optional.of(first));
        when(entries.lockById("b")).thenReturn(Optional.of(second));
        // Both read "nothing in force" — the second before the first committed: there was no row to lock.
        when(entries.lockApprovedInScope(CodeType.ROOM_TYPE, "MRU01", "DBL")).thenReturn(Optional.empty());
        when(entries.lastVersion(CodeType.ROOM_TYPE, "MRU01", "DBL")).thenReturn(0);
        when(entries.saveAndFlush(first)).thenReturn(first);
        when(entries.saveAndFlush(second)).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint \"mapping_entry_one_approved\"]"));

        assertThat(dictionary.approve("a", "Ana").getStatus()).isEqualTo(EntryStatus.APPROVED);
        assertThatThrownBy(() -> dictionary.approve("b", "Luis"))
                .isInstanceOf(ConcurrentDecisionException.class)
                .isInstanceOf(IllegalStateException.class)   // a 409 through the API, a message in the console
                .hasMessageStartingWith("Another version of ROOM_TYPE DBL (MRU01) was approved at the same moment")
                .hasMessageContaining("changed nothing");

        assertThat(resolved).containsExactly("ROOM_TYPE DBL MRU01 by Ana");
    }

    @Test
    void theVersionInForceLeavesForceBeforeTheNewOneComesIn() {
        var inForce = proposal("v1", "XDBL");
        inForce.approve(0, "Ana", NOW);
        var correction = proposal("v2", "YDBL");
        when(entries.lockById("v2")).thenReturn(Optional.of(correction));
        when(entries.lockApprovedInScope(CodeType.ROOM_TYPE, "MRU01", "DBL")).thenReturn(Optional.of(inForce));
        when(entries.lastVersion(CodeType.ROOM_TYPE, "MRU01", "DBL")).thenReturn(1);

        var approved = dictionary.approve("v2", "Luis");

        assertThat(inForce.getStatus()).isEqualTo(EntryStatus.SUPERSEDED);
        assertThat(approved.getStatus()).isEqualTo(EntryStatus.APPROVED);
        assertThat(approved.getEntryVersion()).isEqualTo(2);
        InOrder order = inOrder(entries);
        order.verify(entries).lockById("v2");
        order.verify(entries).saveAndFlush(inForce);
        order.verify(entries).saveAndFlush(correction);
    }

    @Test
    void approvingWhatIsNoLongerAProposalTouchesNothingElse() {
        var already = proposal("a", "XDBL");
        already.approve(0, "Ana", NOW);
        when(entries.lockById("a")).thenReturn(Optional.of(already));

        assertThatThrownBy(() -> dictionary.approve("a", "Luis"))
                .isNotInstanceOf(ConcurrentDecisionException.class)
                .hasMessage("Only a proposed entry can be approved; this one is APPROVED");
        verify(entries, never()).lockApprovedInScope(any(), any(), any());
        assertThat(resolved).isEmpty();
    }

    @Test
    void anotherIntegrityErrorIsNotPassedOffAsAConcurrentApproval() {
        var e = proposal("a", "XDBL");
        when(entries.lockById("a")).thenReturn(Optional.of(e));
        when(entries.lockApprovedInScope(any(), any(), any())).thenReturn(Optional.empty());
        when(entries.saveAndFlush(e)).thenThrow(new DataIntegrityViolationException("value too long for type character varying(255)"));

        assertThatThrownBy(() -> dictionary.approve("a", "Ana")).isExactlyInstanceOf(DataIntegrityViolationException.class);
    }
}
