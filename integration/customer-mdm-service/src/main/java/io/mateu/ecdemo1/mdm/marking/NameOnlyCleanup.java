package io.mateu.ecdemo1.mdm.marking;

import io.mateu.ecdemo1.integration.model.customer.CustomerStatus;
import io.mateu.ecdemo1.mdm.footprint.Footprint;
import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import io.mateu.ecdemo1.mdm.store.SalesforceState;
import io.mateu.ecdemo1.mdm.store.Source;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Data minimisation (GDPR art. 5.1.c and 5.1.e): a contact that is <b>only a name</b> — nothing to reach
 * or identify the person by — and whose customer has only <b>cancelled or no-show</b> bookings serves
 * no purpose in the CRM once they are over: nobody stayed, nobody can be contacted, nobody can be told
 * apart from a namesake. After a retention period ({@code mdm.cleanup.after}, 30 days by default) of
 * no activity, its personal data are erased in Salesforce — the contact is anonymised, not deleted, so
 * nothing that points at it breaks.
 *
 * <p>The MDM keeps the reference — the customer code, the contact id — and the reason, on the
 * record: what was done, when and why can be shown. The bookings stay in the CRS, which is their
 * system of record.
 *
 * <p>Only on certainty: a booking the CRS does not answer for, or does not have, keeps the customer as
 * it is until the next run. Once a day, in batches of {@value SalesforceClient#COLLECTION} contacts per
 * call; a day with nothing to clean calls nobody.
 */
@Component
@Slf4j
public class NameOnlyCleanup {

    /** What the clean-up decided about one customer: why it is anonymised, or why not. */
    public record Decision(boolean anonymize, String reason) {

        static Decision keep(String why) {
            return new Decision(false, why);
        }
    }

    final SalesforceClient salesforce;
    final CustomerRepository customers;
    final SourceRepository sources;
    final Footprint footprint;
    final TransactionTemplate tx;
    final Clock clock;
    final Duration after;
    final boolean enabled;

    public NameOnlyCleanup(SalesforceClient salesforce, CustomerRepository customers, SourceRepository sources,
                           Footprint footprint, TransactionTemplate tx, Clock clock,
                           @Value("${mdm.cleanup.after:30d}") Duration after,
                           @Value("${mdm.cleanup.enabled:true}") boolean enabled) {
        this.salesforce = salesforce;
        this.customers = customers;
        this.sources = sources;
        this.footprint = footprint;
        this.tx = tx;
        this.clock = clock;
        this.after = after;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${mdm.cleanup.cron:0 30 3 * * *}")
    public void run() {
        if (!enabled || !salesforce.available() || !footprint.readsBookings()) {
            return;
        }
        var cutoff = clock.instant().minus(after);
        var chosen = new ArrayList<Customer>();
        for (var c : customers.findBySalesforceStateAndSalesforceContactIdNotNullAndAnonymizedAtIsNull(SalesforceState.PROJECTED)) {
            if (c.status == CustomerStatus.MERGED || Marking.quality(c) != Marking.Quality.NAME_ONLY) {
                continue;
            }
            var decision = decide(c, cutoff);
            if (decision.anonymize()) {
                c.anonymizedReason = decision.reason();
                chosen.add(c);
            } else {
                log.debug("{} kept: {}", c.id, decision.reason());
            }
        }
        if (!chosen.isEmpty()) {
            anonymize(chosen);
        }
    }

    Decision decide(Customer c, Instant cutoff) {
        var codes = footprint.codesOf(c);
        var passengers = new ArrayList<Source>();
        codes.forEach(code -> passengers.addAll(sources.findByCustomerIdOrderByFirstSeenAsc(code)));
        var locators = new LinkedHashSet<String>();
        passengers.forEach(s -> locators.add(s.locator));
        var bookings = new ArrayList<Optional<Footprint.Booking>>();
        for (var locator : locators) {
            try {
                bookings.add(footprint.lookup(locator));
            } catch (RuntimeException e) {
                return Decision.keep("the CRS did not answer for " + locator);
            }
        }
        var lastSeen = passengers.stream().map(s -> s.lastSeen).filter(java.util.Objects::nonNull)
                .max(Instant::compareTo).orElse(null);
        return decide(c, lastSeen, bookings, cutoff, after);
    }

    /**
     * The rule, on what was read: a name only; bookings, every one of them found and every one
     * cancelled or a no-show; and nothing about the customer — its record, its passengers — newer than
     * the cut-off.
     */
    static Decision decide(Customer c, Instant lastSeen, List<Optional<Footprint.Booking>> bookings, Instant cutoff,
                           Duration after) {
        if (c.anonymizedAt != null) {
            return Decision.keep("already anonymised");
        }
        if (Marking.quality(c) != Marking.Quality.NAME_ONLY) {
            return Decision.keep("it has contact data");
        }
        if (bookings.isEmpty()) {
            return Decision.keep("no bookings to tell by");
        }
        if (bookings.stream().anyMatch(Optional::isEmpty)) {
            return Decision.keep("a booking the CRS does not have");
        }
        var live = bookings.stream().map(Optional::get).filter(b -> !b.cancelledOrNoShow()).toList();
        if (!live.isEmpty()) {
            return Decision.keep("a booking that is not cancelled: " + live.get(0).id());
        }
        var activity = latest(c.updatedAt == null ? c.createdAt : c.updatedAt, lastSeen);
        if (activity != null && activity.isAfter(cutoff)) {
            return Decision.keep("activity within the retention period");
        }
        var cancelled = bookings.stream().map(Optional::get)
                .map(b -> b.id() + " " + ("NOS".equalsIgnoreCase(b.cancellationReason()) ? "no-show" : "cancelada"))
                .toList();
        return new Decision(true, "Solo nombre y solo reservas canceladas o no-show (" + String.join(", ", cancelled)
                + "); sin actividad desde " + (activity == null ? "?" : LocalDate.ofInstant(activity, ZoneOffset.UTC))
                + "; anonimizado tras " + after.toDays() + " días (RGPD: minimización y plazo de conservación)");
    }

    static Instant latest(Instant a, Instant b) {
        return a == null ? b : b == null ? a : a.isAfter(b) ? a : b;
    }

    /**
     * Marked first, then erased: the erasure changes the contact's names, which Salesforce announces as
     * a contact change — and the MDM must already know not to read it back into the golden record.
     */
    void anonymize(List<Customer> chosen) {
        var now = clock.instant();
        for (int from = 0; from < chosen.size(); from += SalesforceClient.COLLECTION) {
            var batch = chosen.subList(from, Math.min(chosen.size(), from + SalesforceClient.COLLECTION));
            batch.forEach(c -> {
                c.anonymizedAt = now;
                tx.executeWithoutResult(s -> customers.findById(c.id).ifPresent(r -> {
                    r.anonymizedAt = now;
                    r.anonymizedReason = c.anonymizedReason;
                    customers.save(r);
                }));
            });
            List<SalesforceClient.Upserted> answers;
            try {
                answers = salesforce.anonymizeContacts(batch);
            } catch (RuntimeException e) {
                log.warn("Could not anonymise {} contact(s) in Salesforce, next run: {}", batch.size(), e.getMessage());
                batch.forEach(c -> undo(c.id));
                return;
            }
            for (int i = 0; i < batch.size(); i++) {
                var c = batch.get(i);
                var answer = answers.get(i);
                if (answer.ok()) {
                    var key = Marking.of(c).key();
                    tx.executeWithoutResult(s -> customers.findById(c.id).ifPresent(r -> {
                        r.salesforceState = SalesforceState.ANONYMIZED;
                        r.markedAs = key;
                        customers.save(r);
                    }));
                    log.info("{} anonymised in Salesforce ({}): {}", c.id, c.salesforceContactId, c.anonymizedReason);
                } else {
                    log.warn("Salesforce did not anonymise {}: {}", c.id, answer.error());
                    undo(c.id);
                }
            }
        }
    }

    void undo(String id) {
        tx.executeWithoutResult(s -> customers.findById(id).ifPresent(r -> {
            r.anonymizedAt = null;
            r.anonymizedReason = null;
            customers.save(r);
        }));
    }
}
