package io.mateu.ecdemo1.mapping.store;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** An entry's life: proposed, then decided — and only along the transitions {@link EntryStatus} draws. */
class MappingEntryTest {

    static final Instant T0 = Instant.parse("2026-09-27T10:00:00Z");
    static final Instant T1 = Instant.parse("2026-09-27T11:00:00Z");

    static MappingEntry proposal() {
        return MappingEntry.proposed("e1", CodeType.ROOM_TYPE, "MRU01", "DBL", "XDBL", Map.of(), "agent", 0.7, "why", T0);
    }

    @Test
    void aProposalTranslatesNothingYetAndRemembersWhoProposedIt() {
        var e = proposal();
        assertThat(e.getStatus()).isEqualTo(EntryStatus.PROPOSED);
        assertThat(e.getEntryVersion()).isZero();
        assertThat(e.getProposedBy()).isEqualTo("agent");
        assertThat(e.getCreatedAt()).isEqualTo(T0);
        assertThat(e.getDecidedBy()).isNull();
        assertThat(e.scope()).isEqualTo("MRU01");
    }

    @Test
    void aHotelCodeIsAlwaysChainLevelAndAnEmptyHotelIsTheChain() {
        assertThat(MappingEntry.proposed("h", CodeType.HOTEL, "MRU01", "MRU01", "XMAR", null, "p", null, null, T0).getHotelCode()).isNull();
        assertThat(MappingEntry.proposed("c", CodeType.BOARD, " ", "AD", "BB", null, "p", null, null, T0).scope()).isEqualTo("chain");
    }

    @Test
    void aProposalNeedsATypeACrsCodeAndAPmsCode() {
        assertThatThrownBy(() -> MappingEntry.proposed("x", null, null, "AD", "BB", null, "p", null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MappingEntry.proposed("x", CodeType.BOARD, null, "AD", " ", null, "p", null, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approvingPutsItInForceAsTheNextVersionWithWhoDecided() {
        var e = proposal();
        e.approve(3, "Ana", T1);
        assertThat(e.getStatus()).isEqualTo(EntryStatus.APPROVED);
        assertThat(e.getEntryVersion()).isEqualTo(4);
        assertThat(e.getDecidedBy()).isEqualTo("Ana");
        assertThat(e.getDecidedAt()).isEqualTo(T1);
    }

    @Test
    void aCorrectionSupersedesTheVersionInForceWhichKeepsItsAuthorAsHistory() {
        var v1 = proposal();
        v1.approve(0, "Ana", T0);
        var v2 = MappingEntry.proposed("e2", CodeType.ROOM_TYPE, "MRU01", "DBL", "XDBL2", Map.of(), "Luis", null, null, T1);
        assertThat(v2.sameCodeAndScope(v1)).isTrue();

        v1.supersede();
        v2.approve(v1.getEntryVersion(), "Luis", T1);

        assertThat(v1.getStatus()).isEqualTo(EntryStatus.SUPERSEDED);
        assertThat(v1.getDecidedBy()).isEqualTo("Ana");
        assertThat(v2.getEntryVersion()).isEqualTo(2);
    }

    @Test
    void rejectingAndWithdrawingRecordWhoDecided() {
        var rejected = proposal();
        rejected.reject("Luis", T1);
        assertThat(rejected.getStatus()).isEqualTo(EntryStatus.REJECTED);
        assertThat(rejected.getDecidedBy()).isEqualTo("Luis");

        var withdrawn = proposal();
        withdrawn.approve(0, "Ana", T0);
        withdrawn.withdraw("Luis", T1);
        assertThat(withdrawn.getStatus()).isEqualTo(EntryStatus.WITHDRAWN);
        assertThat(withdrawn.getDecidedBy()).isEqualTo("Luis");
        assertThat(withdrawn.getEntryVersion()).isEqualTo(1);
    }

    @Test
    void onlyAProposalCanBeApprovedRejectedOrProposedAgain() {
        var e = proposal();
        e.approve(0, "Ana", T0);
        assertThatThrownBy(() -> e.approve(1, "Ana", T1)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a proposed entry can be approved; this one is APPROVED");
        assertThatThrownBy(() -> e.reject("Luis", T1)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a proposed entry can be rejected; this one is APPROVED");
        assertThatThrownBy(() -> e.proposedAgain(0.9, "again")).isInstanceOf(IllegalStateException.class);
        assertThat(e.getEntryVersion()).isEqualTo(1);
    }

    @Test
    void onlyWhatIsInForceCanBeWithdrawnOrSuperseded() {
        var e = proposal();
        assertThatThrownBy(() -> e.withdraw("Luis", T1)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Only an equivalence in force can be withdrawn; this one is PROPOSED");
        assertThatThrownBy(e::supersede).isInstanceOf(IllegalStateException.class);
        assertThat(e.getStatus()).isEqualTo(EntryStatus.PROPOSED);
    }

    @Test
    void rejectedSupersededAndWithdrawnAreFinal() {
        for (var end : EnumSet.of(EntryStatus.REJECTED, EntryStatus.SUPERSEDED, EntryStatus.WITHDRAWN)) {
            assertThat(end.next()).as(end.name()).isEmpty();
        }
        var e = proposal();
        e.approve(0, "Ana", T0);
        e.withdraw("Luis", T1);
        assertThatThrownBy(() -> e.withdraw("Luis", T1)).hasMessageContaining("WITHDRAWN");
        assertThatThrownBy(e::supersede).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void proposingAgainTakesTheLatestConfidenceAndRationaleButKeepsWhatIsNotSaid() {
        var e = proposal();
        e.proposedAgain(0.9, null);
        assertThat(e.getConfidence()).isEqualTo(0.9);
        assertThat(e.getRationale()).isEqualTo("why");
    }
}
