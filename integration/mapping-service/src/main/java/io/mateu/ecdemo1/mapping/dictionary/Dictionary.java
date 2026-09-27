package io.mateu.ecdemo1.mapping.dictionary;

import io.mateu.ecdemo1.mapping.audit.Audited;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.mapping.Translation;
import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.store.EntryStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import io.mateu.ecdemo1.mapping.store.MappingSchema;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
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
    final io.mateu.ecdemo1.mapping.outbox.Outbox outbox;

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

    /**
     * Records a proposal. It translates nothing until someone approves it.
     *
     * <p>Proposing again what is already waiting — the same code, scope and PMS code — records nothing
     * new: the waiting proposal is returned, with the latest confidence and rationale. The agent is
     * asked more than once for the same hotel (the onboarding asks, and so can a person from Pending),
     * and each answer used to add a copy of every proposal. A different PMS code for the same code is
     * an alternative, and is recorded.
     */
    @Audited("Propose mapping")
    @Transactional
    public MappingEntry propose(Proposal proposal, String proposedBy) {
        var entry = MappingEntry.proposed(UUID.randomUUID().toString(), proposal.type(), proposal.hotelCode(),
                proposal.sourceCode(), proposal.targetCode(), proposal.attributes(), proposedBy, proposal.confidence(),
                proposal.rationale(), clock.instant());
        var waiting = entries.pendingProposal(entry.getType(), entry.getHotelCode(), entry.getSourceCode(), entry.getTargetCode());
        if (!waiting.isEmpty()) {
            var same = waiting.getFirst();
            same.proposedAgain(proposal.confidence(), proposal.rationale());
            return entries.save(same);
        }
        return entries.save(entry);
    }

    /**
     * Puts a proposal in force as the next version of its code in its scope. The version it replaces
     * is kept as SUPERSEDED — correcting an equivalence is approving a new one, never editing the
     * old in place. Resolving it resumes every process waiting for that code.
     *
     * <p>A chain-level approval can affect every property at once, which is why it is a person's
     * decision and why it is recorded with their name.
     *
     * <p>One version in force per code and scope, even when two people approve at once. The entry and
     * the version in force are locked ({@code select … for update}), so a second decision on either
     * waits and then sees the first one's result; and the database refuses a second APPROVED for the
     * same code and scope ({@link MappingSchema#ONE_APPROVED_INDEX}) — the case with no version in
     * force yet, where there is no row to lock. The one that loses gets a
     * {@link ConcurrentDecisionException} and changes nothing.
     *
     * <p>The causes the approval removes are resolved in the same transaction, on purpose: the
     * waiting processes are released through the outbox, and an approval committed without them would
     * leave those processes waiting for an equivalence that is already in force.
     */
    @Audited("Approve mapping")
    @Transactional
    public MappingEntry approve(String entryId, String approvedBy) {
        var entry = entries.lockById(entryId).orElseThrow(() -> new NoSuchElementException("No mapping entry " + entryId));
        entry.checkApprovable();   // before anything else is touched
        try {
            entries.lockApprovedInScope(entry.getType(), entry.getHotelCode(), entry.getSourceCode()).ifPresent(previous -> {
                previous.supersede();
                entries.saveAndFlush(previous);   // out of force before the new one comes in: the index is checked row by row
            });
            entry.approve(entries.lastVersion(entry.getType(), entry.getHotelCode(), entry.getSourceCode()),
                    approvedBy, clock.instant());
            entries.saveAndFlush(entry);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
            if (e instanceof DataIntegrityViolationException && !oneApprovedRefused(e)) {
                throw e;
            }
            log.info("{} → {} not approved by {}: another version was approved at the same moment",
                    entry.describe(), entry.getTargetCode(), approvedBy);
            throw new ConcurrentDecisionException(entry, e);
        }
        log.info("{} {} → {} approved for {} by {} (v{})", entry.getType(), entry.getSourceCode(), entry.getTargetCode(),
                entry.scope(), approvedBy, entry.getEntryVersion());
        causes.mappingApproved(entry.getType(), entry.getHotelCode(), entry.getSourceCode(), approvedBy);
        noProposalLeft(approvedBy);
        return entry;
    }

    @Audited("Reject mapping")
    @Transactional
    public MappingEntry reject(String entryId, String rejectedBy) {
        var entry = entries.lockById(entryId).orElseThrow(() -> new NoSuchElementException("No mapping entry " + entryId));
        entry.reject(rejectedBy, clock.instant());
        entries.save(entry);
        noProposalLeft(rejectedBy);
        return entry;
    }

    /**
     * Takes an equivalence out of force without putting another in its place — a wrong one that should
     * never have been approved. The code has no equivalence again, so what needs it from now on waits
     * for a new one, as for any code nobody has mapped; what was already written stays as it was.
     * Correcting an equivalence is not this: that is approving a new version, which supersedes it.
     */
    @Audited("Withdraw mapping")
    @Transactional
    public MappingEntry withdraw(String entryId, String withdrawnBy) {
        var entry = entries.lockById(entryId).orElseThrow(() -> new NoSuchElementException("No mapping entry " + entryId));
        entry.withdraw(withdrawnBy, clock.instant());
        log.info("{} {} → {} withdrawn for {} by {} (v{})", entry.getType(), entry.getSourceCode(), entry.getTargetCode(),
                entry.scope(), withdrawnBy, entry.getEntryVersion());
        return entries.save(entry);
    }

    /** A person entering an equivalence directly: proposed and approved by them in one go. */
    @Audited("Define mapping")
    @Transactional
    public MappingEntry define(Proposal proposal, String author) {
        return approve(propose(proposal, author).getId(), author);
    }

    /** Whether it is the index of one APPROVED per code and scope that refused the write. */
    static boolean oneApprovedRefused(Throwable e) {
        for (var t = e; t != null; t = t.getCause()) {
            if (String.valueOf(t.getMessage()).contains(MappingSchema.ONE_APPROVED_INDEX)) {
                return true;
            }
        }
        return false;
    }

    /** The last proposal decided: the notifications asking to review them are done with. */
    void noProposalLeft(String by) {
        entries.flush();
        if (!entries.existsByStatus(EntryStatus.PROPOSED)) {
            outbox.appendResolution(io.mateu.ecdemo1.mapping.proposals.ProposalAnnouncer.SUBJECT, by);
        }
    }

}
