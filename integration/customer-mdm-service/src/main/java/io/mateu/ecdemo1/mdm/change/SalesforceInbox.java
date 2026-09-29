package io.mateu.ecdemo1.mdm.change;

import io.mateu.ecdemo1.mdm.salesforce.SalesforceClient;
import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.ecdemo1.mdm.store.ChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * What Salesforce says, one thing at a time. An approval arrives twice at once — the contact changed,
 * and the decision — on two subscriptions, and the poll may find it too: handled side by side, both
 * would read the customer before the other wrote it. Each one, with its transaction, runs alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesforceInbox {

    final ChangeRequests changeRequests;
    final SalesforceProjection projection;
    final ChangeRequestRepository requests;
    final SalesforceClient salesforce;
    final io.mateu.ecdemo1.mdm.store.CustomerRepository customers;
    final org.springframework.transaction.support.TransactionTemplate tx;
    final ReentrantLock lock = new ReentrantLock();

    public void decided(String requestId, String decision, String how) {
        lock.lock();
        try {
            changeRequests.decided(requestId, decision, how);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Salesforce says the contact changed: read it and project it. If it cannot be read now — the
     * daily allowance is spent, Salesforce does not answer — the customer is marked, and read when it
     * answers again: the event is not replayed, and the poll looks for merges, not for changes.
     */
    public void contactChanged(String mdmId) {
        if (customers.findById(mdmId).map(c -> c.anonymizedAt != null).orElse(false)) {
            // Its own anonymisation, announced: nothing personal to read back, and nothing to project.
            log.debug("{} was anonymised: its contact's change is not read back", mdmId);
            return;
        }
        lock.lock();
        try {
            projection.refresh(mdmId, null, null);
        } catch (RuntimeException e) {
            log.info("{} changed in Salesforce, read later ({})", mdmId, e.getMessage());
            tx.executeWithoutResult(s -> customers.findById(mdmId).ifPresent(c -> {
                c.salesforceRefreshPending = true;
                customers.save(c);
            }));
        } finally {
            lock.unlock();
        }
    }

    /** The contacts Salesforce said changed while they could not be read: read now. */
    @Scheduled(fixedDelayString = "${mdm.refresh-tick:60s}")
    public void refreshPending() {
        if (!salesforce.available()) {
            return;
        }
        for (var c : customers.findTop50BySalesforceRefreshPendingTrue()) {
            if (c.anonymizedAt != null) {
                tx.executeWithoutResult(s -> customers.findById(c.id).ifPresent(r -> {
                    r.salesforceRefreshPending = null;
                    customers.save(r);
                }));
                continue;
            }
            lock.lock();
            try {
                projection.refresh(c.id, null, null);
                tx.executeWithoutResult(s -> customers.findById(c.id).ifPresent(r -> {
                    r.salesforceRefreshPending = null;
                    customers.save(r);
                }));
            } catch (RuntimeException e) {
                log.debug("{} not read yet: {}", c.id, e.getMessage());
                return;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * The safety net for a missed decision event: asks Salesforce how the open ones stand — one query
     * for all of them, and only while there is any. Once a day, at start, and when the decisions'
     * subscription starts without a replay id: the Pub/Sub event is what brings a decision; this is for
     * one that was lost.
     */
    @org.springframework.context.event.EventListener
    public void onGap(io.mateu.ecdemo1.mdm.salesforce.SubscriptionGap gap) {
        if (io.mateu.ecdemo1.mdm.salesforce.ConsolidationEvents.DECISIONS.equals(gap.topic())) {
            poll();
        }
    }

    @Scheduled(fixedDelayString = "${mdm.change-poll:24h}", initialDelayString = "${mdm.change-poll-initial-delay:2m}")
    public void poll() {
        if (!salesforce.available()) {
            return;
        }
        var open = requests.findByStatusOrderByRequestedAtAsc(ChangeRequest.Status.PENDING.name()).stream()
                .filter(r -> r.sentAt != null).map(r -> r.id).toList();
        try {
            salesforce.decisions(open).forEach((id, decision) -> decided(id, decision, "POLL"));
        } catch (SalesforceClient.LimitExceeded e) {
            log.debug("Asking Salesforce about change requests waits for the allowance");
        } catch (RuntimeException e) {
            log.warn("Could not ask Salesforce about {} change request(s): {}", open.size(), e.getMessage());
        }
    }
}
