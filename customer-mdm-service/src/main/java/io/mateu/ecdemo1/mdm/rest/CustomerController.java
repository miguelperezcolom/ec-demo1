package io.mateu.ecdemo1.mdm.rest;

import io.mateu.ecdemo1.integration.model.customer.IdentityRequest;
import io.mateu.ecdemo1.integration.model.customer.ResolvedIdentity;
import io.mateu.ecdemo1.mdm.resolution.IdentityResolution;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@RestController
@RequiredArgsConstructor
public class CustomerController {

    final IdentityResolution resolution;
    final CustomerRepository customers;
    final SourceRepository sources;
    final io.mateu.ecdemo1.mdm.store.XrefRepository xrefRepository;
    final io.mateu.ecdemo1.mdm.store.ChangeRequestRepository changeRequestRepository;
    final io.mateu.ecdemo1.mdm.change.ChangeRequests changeRequests;
    final io.mateu.ecdemo1.mdm.change.Xrefs xrefs;

    /** A change to a customer's data proposed by a hotel: Salesforce decides it; the response says where it stands. */
    @org.springframework.web.bind.annotation.PostMapping("/customers/{id}/change-requests")
    @Operation(summary = "Propose a change to a customer's data (from a hotel); it becomes the customer's data only if Salesforce approves it")
    public ChangeRequestView propose(@PathVariable String id, @org.springframework.web.bind.annotation.RequestBody io.mateu.ecdemo1.mdm.change.ChangeRequests.Proposal proposal) {
        return ChangeRequestView.of(changeRequests.submit(id, proposal));
    }

    @GetMapping("/change-requests/{id}")
    @Operation(summary = "A change request and how Salesforce decided it")
    public ChangeRequestView changeRequest(@PathVariable String id) {
        return ChangeRequestView.of(changeRequests.get(id));
    }

    /** Where a customer is known outside the MDM: a front office's guest, an Opera profile. */
    public record XrefRequest(String target, String reference, String context) {
    }

    @org.springframework.web.bind.annotation.PutMapping("/customers/{id}/xrefs")
    @Operation(summary = "Record where a customer is known outside the MDM (SALESFORCE, FRONT_OFFICE, OPERA)")
    public void xref(@PathVariable String id, @org.springframework.web.bind.annotation.RequestBody XrefRequest xref) {
        xrefs.record(resolution.survivorOf(id).id, io.mateu.ecdemo1.mdm.store.Xref.Target.valueOf(xref.target()), xref.reference(), xref.context());
    }

    public record ChangeRequestView(String id, String customerId, String status, String changes, String origin,
                                    String salesforceCaseId, java.time.Instant requestedAt, java.time.Instant decidedAt) {
        static ChangeRequestView of(io.mateu.ecdemo1.mdm.store.ChangeRequest r) {
            return new ChangeRequestView(r.id, r.customerId, r.status, r.changes, r.origin, r.salesforceCaseId, r.requestedAt, r.decidedAt);
        }
    }

    @PostMapping("/identities/resolve")
    @Operation(summary = "The customers a reservation's passengers are: existing ones when certain, provisional ones otherwise. Idempotent per reservation and passenger")
    public List<ResolvedIdentity> resolve(@RequestBody IdentityRequest request) {
        return resolution.resolve(request);
    }

    @GetMapping("/customers/{id}")
    @Operation(summary = "A customer by any code it ever had; an absorbed code answers with its survivor")
    public CustomerView customer(@PathVariable String id) {
        return view(resolution.survivorOf(id), id);
    }

    @GetMapping("/customers")
    @Operation(summary = "Customers whose name, email, document or code contains the text; merged ones are left out. "
            + "With xref=SYSTEM:REFERENCE (SALESFORCE, FRONT_OFFICE, OPERA), the customer known by that reference there")
    public List<CustomerView> search(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) String xref) {
        if (xref != null && !xref.isBlank()) {
            return byXref(xref);
        }
        var text = q.toLowerCase(Locale.ROOT);
        return customers.findAllByOrderByUpdatedAtDesc().stream()
                .filter(c -> c.aliasOf == null)
                .filter(c -> text.isEmpty() || (c.id + " " + c.fullName() + " " + c.email + " " + c.documentNumber)
                        .toLowerCase(Locale.ROOT).contains(text))
                .limit(100)
                .map(c -> view(c, c.id))
                .toList();
    }

    /**
     * The customer known by a reference in another system — «which customer is Opera profile
     * 20538296». The survivor, if the one it was recorded for was merged since.
     */
    public List<CustomerView> byXref(String xref) {
        var parts = xref.split(":", 2);
        if (parts.length != 2 || parts[1].isBlank()) {
            throw new IllegalArgumentException("xref is SYSTEM:REFERENCE, e.g. OPERA:20538296");
        }
        var system = io.mateu.ecdemo1.mdm.store.Xref.Target.valueOf(parts[0].trim().toUpperCase(Locale.ROOT)).name();
        var found = new java.util.LinkedHashMap<String, Customer>();
        for (var x : xrefRepository.findBySystemAndReference(system, parts[1].trim())) {
            var survivor = resolution.survivorOf(x.customerId);
            found.putIfAbsent(survivor.id, survivor);
        }
        return found.values().stream().map(c -> view(c, c.id)).toList();
    }

    public CustomerView view(Customer c, String requestedId) {
        var aliases = customers.findByAliasOf(c.id).stream().map(a -> a.id).toList();
        var reservations = sources.findByCustomerIdOrderByFirstSeenAsc(c.id).stream()
                .map(s -> s.hotelCode + "/" + s.locator).distinct().toList();
        var known = xrefRepository.findByCustomerIdOrderBySystemAscReferenceAsc(c.id).stream()
                .map(x -> x.system + ":" + x.reference + (x.context == null ? "" : " (" + x.context + ")")).toList();
        var changes = changeRequestRepository.findByCustomerIdOrderByRequestedAtDesc(c.id).stream()
                .map(r -> r.id + " " + r.status + " — " + r.changes).toList();
        return CustomerView.of(c, requestedId, aliases, reservations, known, changes);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NoSuchElementException e) {
        return e.getMessage();
    }
}
