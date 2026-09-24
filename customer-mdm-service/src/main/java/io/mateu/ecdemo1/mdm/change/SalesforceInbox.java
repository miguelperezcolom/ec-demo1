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
    final ReentrantLock lock = new ReentrantLock();

    public void decided(String requestId, String decision, String how) {
        lock.lock();
        try {
            changeRequests.decided(requestId, decision, how);
        } finally {
            lock.unlock();
        }
    }

    public void contactChanged(String mdmId) {
        lock.lock();
        try {
            projection.refresh(mdmId, null, null);
        } finally {
            lock.unlock();
        }
    }

    /** The safety net for a missed decision event: asks Salesforce how the open ones stand. */
    @Scheduled(fixedDelayString = "${mdm.change-poll:30s}")
    public void poll() {
        if (!salesforce.enabled()) {
            return;
        }
        var open = requests.findByStatusOrderByRequestedAtAsc(ChangeRequest.Status.PENDING.name()).stream()
                .filter(r -> r.sentAt != null).map(r -> r.id).toList();
        try {
            salesforce.decisions(open).forEach((id, decision) -> decided(id, decision, "POLL"));
        } catch (RuntimeException e) {
            log.warn("Could not ask Salesforce about {} change request(s): {}", open.size(), e.getMessage());
        }
    }
}
