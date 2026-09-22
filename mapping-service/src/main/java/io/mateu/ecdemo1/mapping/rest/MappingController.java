package io.mateu.ecdemo1.mapping.rest;

import io.mateu.ecdemo1.integration.model.mapping.Cause;
import io.mateu.ecdemo1.integration.model.mapping.CodeEntry;
import io.mateu.ecdemo1.integration.model.mapping.CodeType;
import io.mateu.ecdemo1.integration.model.mapping.Translation;
import io.mateu.ecdemo1.mapping.causes.Causes;
import io.mateu.ecdemo1.mapping.dictionary.Dictionary;
import io.mateu.ecdemo1.mapping.dictionary.Pending;
import io.mateu.ecdemo1.mapping.store.CauseRecord;
import io.mateu.ecdemo1.mapping.store.CauseRecordRepository;
import io.mateu.ecdemo1.mapping.store.CauseStatus;
import io.mateu.ecdemo1.mapping.store.MappingEntry;
import io.mateu.ecdemo1.mapping.store.MappingEntryRepository;
import io.mateu.ecdemo1.mapping.store.PartnerProfile;
import io.mateu.ecdemo1.mapping.store.PartnerProfileRepository;
import io.mateu.ecdemo1.mapping.store.WaiterRepository;
import io.mateu.workflow.dtos.Variable;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The mapping's API. Two audiences: the PMS adapter, which resolves codes at write time and
 * registers the causes it runs into; and the control plane — console, MCP, agent — which reads the
 * causes and the pending codes and decides the equivalences.
 */
@RestController
@RequiredArgsConstructor
public class MappingController {

    final Dictionary dictionary;
    final Pending pending;
    final Causes causes;
    final MappingEntryRepository entries;
    final CauseRecordRepository causeRecords;
    final WaiterRepository waiters;
    final PartnerProfileRepository partnerProfiles;

    public record ResolveRequest(String hotelCode, List<CodeRef> codes) {
    }

    public record CodeRef(CodeType type, String code) {
    }

    public record ResolveResult(List<Translation> translations, List<Cause> missing) {
    }

    public record WaitRequest(String processKey, String definitionId, String hotelCode, String subject,
                              List<Variable> variables, List<Cause> causes) {
    }

    public record CauseView(String key, String type, String description, String hotelCode, String status,
                            long waiting, int openings, String openedAt, String resolvedAt, String resolvedBy) {
        static CauseView of(CauseRecord c, long waiting) {
            return new CauseView(c.causeKey, c.type.name(), c.description, c.hotelCode, c.status.name(), waiting,
                    c.openings, String.valueOf(c.openedAt), String.valueOf(c.resolvedAt), c.resolvedBy);
        }
    }

    @PostMapping("/resolve")
    @Operation(summary = "Translate CRS codes for a hotel; what cannot be translated comes back as causes")
    public ResolveResult resolve(@RequestBody ResolveRequest request) {
        var translations = new ArrayList<Translation>();
        var missing = new ArrayList<Cause>();
        for (var ref : request.codes()) {
            dictionary.resolve(request.hotelCode(), ref.type(), ref.code()).ifPresentOrElse(translations::add,
                    () -> missing.add(Cause.missingMapping(request.hotelCode() == null ? "chain" : request.hotelCode(),
                            ref.type(), ref.code())));
        }
        return new ResolveResult(translations, missing);
    }

    @GetMapping("/partner-profiles/{partnerCode}")
    @Operation(summary = "The PMS profile a partner of the CRS is")
    public PartnerProfile partnerProfile(@PathVariable String partnerCode) {
        return partnerProfiles.findById(partnerCode)
                .orElseThrow(() -> new NoSuchElementException("Partner %s is not in the PMS yet".formatted(partnerCode)));
    }

    @PostMapping("/causes/wait")
    @Operation(summary = "Register that a process waits on causes it ran into")
    public void await(@RequestBody WaitRequest request) {
        causes.await(request.processKey(), request.definitionId(), request.hotelCode(), request.subject(),
                request.variables(), request.causes());
    }

    @GetMapping("/causes")
    @Operation(summary = "Causes, open ones first, with how many processes wait on each")
    public List<CauseView> causes(@RequestParam(defaultValue = "true") boolean openOnly) {
        var list = openOnly ? causeRecords.findByStatusOrderByOpenedAtAsc(CauseStatus.OPEN) : causeRecords.findAll();
        return list.stream().map(c -> CauseView.of(c, waiters.countWaitingOn(c.causeKey))).toList();
    }

    @PostMapping("/causes/resolve")
    @Operation(summary = "Resolve a cause, resuming every process that waits only on it")
    public void resolveCause(@RequestParam String key, @RequestParam(defaultValue = "admin") String by) {
        causes.resolve(key, by);
    }

    @GetMapping("/pending")
    @Operation(summary = "The CRS codes a hotel can emit that have no approved equivalent")
    public List<Pending.PendingCode> pendingCodes(@RequestParam String hotelCode) {
        return pending.pendingCodes(hotelCode);
    }

    @GetMapping("/pms-catalog")
    @Operation(summary = "The PMS's codes for a hotel, to map to")
    public List<CodeEntry> pmsCatalog(@RequestParam String hotelCode) {
        return pending.pmsCatalog(hotelCode);
    }

    @GetMapping("/entries")
    @Operation(summary = "The dictionary: every version of every equivalence")
    public List<MappingEntry> entries() {
        return entries.findAllByOrderByTypeAscSourceCodeAscEntryVersionDesc();
    }

    @PostMapping("/entries/proposals")
    @Operation(summary = "Propose an equivalence; nothing is in force until a person approves it")
    public MappingEntry propose(@RequestBody Dictionary.Proposal proposal,
                                @RequestParam(defaultValue = "admin") String by) {
        return dictionary.propose(proposal, by);
    }

    @PostMapping("/entries/{id}/approve")
    public MappingEntry approve(@PathVariable String id, @RequestParam(defaultValue = "admin") String by) {
        return dictionary.approve(id, by);
    }

    @PostMapping("/entries/{id}/reject")
    public MappingEntry reject(@PathVariable String id, @RequestParam(defaultValue = "admin") String by) {
        return dictionary.reject(id, by);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail notFound(NoSuchElementException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
