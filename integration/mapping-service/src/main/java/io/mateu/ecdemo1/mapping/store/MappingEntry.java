package io.mateu.ecdemo1.mapping.store;

import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One version of the equivalence of one CRS code. {@code hotelCode} null is a chain-level entry;
 * set, it is an exception for that property, and wins over the chain's (F009, two levels).
 *
 * <p>Its life is the one {@link EntryStatus} draws, and only the methods here move it: a
 * transition that does not apply is refused with an {@link IllegalStateException}, whoever asks.
 * At most one version of a code is APPROVED in a scope — the database holds that too, with a
 * partial unique index ({@link MappingSchema}).
 */
@Entity
@Table(name = "mapping_entry", indexes = @Index(columnList = "type, sourceCode, hotelCode, status"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class MappingEntry {

    @Id
    private String id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CodeType type;
    private String hotelCode;
    @Column(nullable = false)
    private String sourceCode;
    private String targetCode;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> attributes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryStatus status;
    /** 1 for the first approved version of this code in this scope, and one more with each. */
    private int entryVersion;
    /** Who proposed it: a person's name, or "agent". */
    private String proposedBy;
    /** 0..1, when the agent proposed it. */
    private Double confidence;
    @Column(length = 2000)
    private String rationale;
    /** Who approved, rejected or withdrew it. */
    private String decidedBy;
    private Instant decidedAt;
    private Instant createdAt;

    /**
     * A new proposal: it translates nothing until someone approves it. HOTEL codes are always
     * chain-level, and an empty hotel is the chain.
     */
    public static MappingEntry proposed(String id, CodeType type, String hotelCode, String sourceCode, String targetCode,
                                        Map<String, String> attributes, String proposedBy, Double confidence,
                                        String rationale, Instant at) {
        if (type == null || blank(sourceCode) || blank(targetCode)) {
            throw new IllegalArgumentException("A proposal needs a type, a CRS code and a PMS code");
        }
        var entry = new MappingEntry();
        entry.id = Objects.requireNonNull(id);
        entry.type = type;
        entry.hotelCode = scopeOf(type, hotelCode);
        entry.sourceCode = sourceCode;
        entry.targetCode = targetCode;
        entry.attributes = attributes;
        entry.status = EntryStatus.PROPOSED;
        entry.proposedBy = proposedBy;
        entry.confidence = confidence;
        entry.rationale = rationale;
        entry.createdAt = at;
        return entry;
    }

    /** The hotel an entry of this type is kept under: none — the chain — for HOTEL, or when empty. */
    public static String scopeOf(CodeType type, String hotelCode) {
        return type == CodeType.HOTEL || blank(hotelCode) ? null : hotelCode;
    }

    /** The same proposal made again: it is not recorded twice, it takes the latest confidence and rationale. */
    public void proposedAgain(Double confidence, String rationale) {
        if (status != EntryStatus.PROPOSED) {
            throw new IllegalStateException("Only a proposed entry can be proposed again; this one is " + status);
        }
        if (confidence != null) this.confidence = confidence;
        if (!blank(rationale)) this.rationale = rationale;
    }

    /**
     * Puts it in force as the version after {@code lastVersion} — the highest this code has had in
     * this scope, whatever became of it. The version in force until now has to be
     * {@linkplain #supersede() superseded} first: two APPROVED at once is what the index refuses.
     */
    public void approve(int lastVersion, String by, Instant at) {
        checkApprovable();
        move(EntryStatus.APPROVED, "");
        entryVersion = lastVersion + 1;
        decidedBy = by;
        decidedAt = at;
    }

    /** Refuses, as {@link #approve} would, an entry that cannot be approved: checked before the version in force is touched. */
    public void checkApprovable() {
        if (status == null || !status.canBecome(EntryStatus.APPROVED)) {
            throw new IllegalStateException("Only a proposed entry can be approved; this one is " + status);
        }
    }

    /** A newer version of the same code in the same scope is being approved: this one leaves force, and stays as history. */
    public void supersede() {
        move(EntryStatus.SUPERSEDED, "Only an equivalence in force can be superseded; this one is " + status);
    }

    public void reject(String by, Instant at) {
        move(EntryStatus.REJECTED, "Only a proposed entry can be rejected; this one is " + status);
        decidedBy = by;
        decidedAt = at;
    }

    /** Out of force with nothing in its place: the code has no equivalence again. */
    public void withdraw(String by, Instant at) {
        move(EntryStatus.WITHDRAWN, "Only an equivalence in force can be withdrawn; this one is " + status);
        decidedBy = by;
        decidedAt = at;
    }

    private void move(EntryStatus next, String refusal) {
        if (status == null || !status.canBecome(next)) {
            throw new IllegalStateException(refusal);
        }
        status = next;
    }

    /** Same code of the same type, under the same hotel (or both chain-level). */
    public boolean sameCodeAndScope(MappingEntry other) {
        return type == other.type && sourceCode.equals(other.sourceCode) && Objects.equals(hotelCode, other.hotelCode);
    }

    /** "chain", or the hotel it is an exception for. */
    public String scope() {
        return hotelCode == null ? "chain" : hotelCode;
    }

    /** "CHANNEL TTOO (MRU01)": how people name it in a message. */
    public String describe() {
        return type + " " + sourceCode + " (" + scope() + ")";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
