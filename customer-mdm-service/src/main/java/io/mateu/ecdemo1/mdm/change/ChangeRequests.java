package io.mateu.ecdemo1.mdm.change;

import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.ecdemo1.mdm.store.ChangeRequestRepository;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * A hotel proposes, Salesforce decides. A proposal is kept here and opened in Salesforce as a Case on
 * the contact; the projection does not change until the contact does. The decision arrives as an
 * event (CambioClienteResuelto__e) or, if the event is missed, by asking Salesforce now and then.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChangeRequests {

    final ChangeRequestRepository requests;
    final CustomerRepository customers;
    final SalesforceClient salesforce;
    final SalesforceProjection projection;
    final TransactionTemplate tx;
    final Clock clock;

    /** What a hotel proposes; a null field is left as it is. */
    public record Proposal(String firstName, String lastName, String name, String email, String phone, String nationality,
                           LocalDate birthDate, String documentType, String documentNumber, String origin) {
    }

    @Transactional
    public ChangeRequest submit(String customerId, Proposal p) {
        var customer = survivor(customerId);
        var r = new ChangeRequest();
        r.id = "CR-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase().replace("-", "");
        r.customerId = customer.id;
        r.origin = p.origin();
        var first = p.firstName();
        var last = p.lastName();
        if (first == null && last == null && p.name() != null && !p.name().isBlank()) {
            // One name, as a front office keeps it. Split where the customer's own parts are: if it still
            // ends with their surnames, the rest is the name; if it starts with their name, the rest are the
            // surnames; otherwise the first word is the name.
            var name = p.name().trim().replaceAll("\\s+", " ");
            if (customer.lastName != null && name.endsWith(" " + customer.lastName)) {
                first = name.substring(0, name.length() - customer.lastName.length() - 1);
                last = customer.lastName;
            } else if (customer.firstName != null && name.startsWith(customer.firstName + " ")) {
                first = customer.firstName;
                last = name.substring(customer.firstName.length() + 1);
            } else {
                var parts = name.split(" ", 2);
                first = parts[0];
                last = parts.length > 1 ? parts[1] : customer.lastName;
            }
        }
        r.firstName = or(first, customer.firstName);
        r.lastName = or(last, customer.lastName);
        r.email = or(p.email(), customer.email);
        r.phone = or(p.phone(), customer.phone);
        r.nationality = or(p.nationality(), customer.nationality);
        r.birthDate = p.birthDate() == null ? customer.birthDate : p.birthDate();
        r.documentType = or(p.documentType(), customer.documentType);
        r.documentNumber = or(p.documentNumber(), customer.documentNumber);
        r.changes = changes(customer, r);
        r.status = r.changes.isEmpty() ? ChangeRequest.Status.APPROVED.name() : ChangeRequest.Status.PENDING.name();
        r.requestedAt = clock.instant();
        if (r.changes.isEmpty()) {
            // Nothing differs from what Salesforce has: nothing to decide.
            r.changes = "sin cambios";
            r.decidedAt = r.requestedAt;
        }
        log.info("{} proposed for {} from {}: {}", r.id, r.customerId, r.origin, r.changes);
        return requests.save(r);
    }

    /** Opens in Salesforce what is waiting to be opened, once the customer is a contact there. */
    @Scheduled(fixedDelayString = "${mdm.change-tick:5s}")
    public void send() {
        if (!salesforce.enabled()) {
            return;
        }
        for (var pending : requests.findByStatusOrderByRequestedAtAsc(ChangeRequest.Status.PENDING.name())) {
            if (pending.sentAt != null) {
                continue;
            }
            var customer = customers.findById(pending.customerId).orElse(null);
            if (customer == null || customer.salesforceContactId == null) {
                continue;
            }
            try {
                var caseId = salesforce.upsertChangeCase(pending, customer.salesforceContactId,
                        "Cambio de datos de cliente: " + customer.fullName(),
                        "Propuesto desde " + pending.origin + ". Cambia: " + pending.changes
                                + ". Para aplicarlo, poner Decisión en Aprobada; para descartarlo, en Rechazada.");
                tx.executeWithoutResult(s -> requests.findById(pending.id).ifPresent(r -> {
                    r.salesforceCaseId = caseId;
                    r.sentAt = clock.instant();
                    r.sendError = null;
                    requests.save(r);
                }));
                log.info("{} opened in Salesforce as Case {}", pending.id, caseId);
            } catch (RuntimeException e) {
                log.warn("{} not opened in Salesforce yet: {}", pending.id, e.getMessage());
                tx.executeWithoutResult(s -> requests.findById(pending.id).ifPresent(r -> {
                    r.sendError = e.getMessage();
                    requests.save(r);
                }));
                return;
            }
        }
    }

    /** Salesforce decided: approved, the contact already has it; rejected, it does not. Once. */
    @Transactional
    public void decided(String requestId, String decision, String how) {
        var r = requests.findById(requestId).orElse(null);
        if (r == null || !ChangeRequest.Status.PENDING.name().equals(r.status)) {
            return;
        }
        var approved = "Aprobada".equalsIgnoreCase(decision) || "APPROVED".equalsIgnoreCase(decision);
        r.status = (approved ? ChangeRequest.Status.APPROVED : ChangeRequest.Status.REJECTED).name();
        r.decidedAt = clock.instant();
        requests.save(r);
        log.info("{} {} in Salesforce ({})", r.id, r.status, how);
        projection.refresh(r.customerId, r.id, r.status);
    }

    public ChangeRequest get(String id) {
        return requests.findById(id).orElseThrow(() -> new NoSuchElementException("No change request " + id));
    }

    Customer survivor(String id) {
        var c = customers.findById(id).orElseThrow(() -> new NoSuchElementException("No customer " + id));
        while (c.aliasOf != null) {
            c = customers.findById(c.aliasOf).orElseThrow();
        }
        return c;
    }

    static String changes(Customer c, ChangeRequest r) {
        var changed = new ArrayList<String>();
        diff(changed, "nombre", c.firstName, r.firstName);
        diff(changed, "apellidos", c.lastName, r.lastName);
        diff(changed, "email", c.email, r.email);
        diff(changed, "teléfono", c.phone, r.phone);
        diff(changed, "nacionalidad", c.nationality, r.nationality);
        diff(changed, "fecha de nacimiento", c.birthDate, r.birthDate);
        diff(changed, "tipo de documento", c.documentType, r.documentType);
        diff(changed, "documento", c.documentNumber, r.documentNumber);
        return String.join(", ", changed);
    }

    static void diff(java.util.List<String> changed, String label, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changed.add(label + " " + (before == null ? "—" : before) + " → " + (after == null ? "—" : after));
        }
    }

    static String or(String value, String otherwise) {
        return value == null || value.isBlank() ? otherwise : value.trim();
    }
}
