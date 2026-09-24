package io.mateu.ecdemo1.mapping.dictionary;

import io.mateu.ecdemo1.mapping.audit.Audited;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.mapping.Translation;
import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * The dictionary of equivalences: chain-level entries with exceptions per property, versioned,
 * and in force only once a person approves them (F009).
 *
 * <p>It is deliberately strict. A code with no approved equivalent does not get a default: the
 * process that needs it waits, because a code mapped wrong puts a guest in the wrong room and is
 * not found until check-in.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Dictionary {

    final MappingEntryRepository entries;
    final Causes causes;
    final Clock clock;

    /**
     * What a CRS code is in the PMS for this hotel: the property's own exception if there is one,
     * the chain's otherwise. HOTEL codes are always chain-level.
     */
    public Optional<Translation> resolve(String hotelCode, CodeType type, String code) {
        var scope = type == CodeType.HOTEL ? null : hotelCode;
        return entries.approvedFor(type, scope, code).stream()
                .filter(e -> scope != null || e.getHotelCode() == null)
                .min(Comparator.comparing(e -> e.getHotelCode() == null ? 1 : 0))
                .map(e -> new Translation(type, code, e.getTargetCode(),
                        e.getAttributes() == null ? Map.of() : e.getAttributes()));
    }

    public record Proposal(CodeType type, String hotelCode, String sourceCode, String targetCode,
                           Map<String, String> attributes, Double confidence, String rationale) {
    }

    /** Records a proposal. It translates nothing until someone approves it. */
    @Audited("Propose mapping")
    @Transactional
    public MappingEntry propose(Proposal proposal, String proposedBy) {
        if (proposal.type() == null || blank(proposal.sourceCode()) || blank(proposal.targetCode())) {
            throw new IllegalArgumentException("A proposal needs a type, a CRS code and a PMS code");
        }
        var entry = new MappingEntry();
        entry.id = UUID.randomUUID().toString();
        entry.type = proposal.type();
        entry.hotelCode = proposal.type() == CodeType.HOTEL || blank(proposal.hotelCode()) ? null : proposal.hotelCode();
        entry.sourceCode = proposal.sourceCode();
        entry.targetCode = proposal.targetCode();
        entry.attributes = proposal.attributes();
        entry.status = EntryStatus.PROPOSED;
        entry.proposedBy = proposedBy;
        entry.confidence = proposal.confidence();
        entry.rationale = proposal.rationale();
        entry.createdAt = clock.instant();
        return entries.save(entry);
    }

    /**
     * Puts a proposal in force as the next version of its code in its scope. The version it replaces
     * is kept as SUPERSEDED — correcting an equivalence is approving a new one, never editing the
     * old in place. Resolving it resumes every process waiting for that code.
     *
     * <p>A chain-level approval can affect every property at once, which is why it is a person's
     * decision and why it is recorded with their name.
     */
    @Audited("Approve mapping")
    @Transactional
    public MappingEntry approve(String entryId, String approvedBy) {
        var entry = entries.findById(entryId).orElseThrow(() -> new NoSuchElementException("No mapping entry " + entryId));
        if (entry.status != EntryStatus.PROPOSED) {
            throw new IllegalStateException("Only a proposed entry can be approved; this one is " + entry.status);
        }
        entries.approvedInScope(entry.type, entry.hotelCode, entry.sourceCode).ifPresent(previous -> {
            previous.status = EntryStatus.SUPERSEDED;
            entries.saveAndFlush(previous);
        });
        entry.entryVersion = entries.lastVersion(entry.type, entry.hotelCode, entry.sourceCode) + 1;
        entry.status = EntryStatus.APPROVED;
        entry.decidedBy = approvedBy;
        entry.decidedAt = clock.instant();
        entries.save(entry);
        log.info("{} {} → {} approved for {} by {} (v{})", entry.type, entry.sourceCode, entry.targetCode,
                entry.scope(), approvedBy, entry.entryVersion);
        causes.mappingApproved(entry.type, entry.hotelCode, entry.sourceCode, approvedBy);
        return entry;
    }

    @Audited("Reject mapping")
    @Transactional
    public MappingEntry reject(String entryId, String rejectedBy) {
        var entry = entries.findById(entryId).orElseThrow(() -> new NoSuchElementException("No mapping entry " + entryId));
        if (entry.status != EntryStatus.PROPOSED) {
            throw new IllegalStateException("Only a proposed entry can be rejected; this one is " + entry.status);
        }
        entry.status = EntryStatus.REJECTED;
        entry.decidedBy = rejectedBy;
        entry.decidedAt = clock.instant();
        return entries.save(entry);
    }

    /** A person entering an equivalence directly: proposed and approved by them in one go. */
    @Audited("Define mapping")
    @Transactional
    public MappingEntry define(Proposal proposal, String author) {
        return approve(propose(proposal, author).id, author);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
