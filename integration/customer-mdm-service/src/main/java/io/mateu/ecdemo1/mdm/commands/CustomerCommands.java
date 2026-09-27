package io.mateu.ecdemo1.mdm.commands;

import io.mateu.ecdemo1.integration.model.command.CustomerCommand;
import io.mateu.ecdemo1.mdm.change.ChangeRequests;
import io.mateu.ecdemo1.mdm.inbox.Inbox;
import io.mateu.ecdemo1.mdm.outbox.CustomerEvents;
import io.mateu.ecdemo1.mdm.scan.ScannedIdentities;
import io.mateu.ecdemo1.mdm.store.ChangeRequest;
import io.mateu.ecdemo1.mdm.store.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the hotels tell the MDM on the {@code customer-commands} topic — a change the desk made to a
 * customer, a document it scanned. Each is taken once: its id goes into the inbox in the same
 * transaction as what it does, and a repetition does nothing. The MDM's answer goes out as its
 * events always do, on the {@code customers} topic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerCommands {

    static final String CONSUMER = "customer-commands";

    final Inbox inbox;
    final ChangeRequests changeRequests;
    final ScannedIdentities scans;
    final CustomerEvents events;
    final CustomerRepository customers;

    /** @return false for a command already taken */
    @Transactional
    public boolean handle(CustomerCommand command) {
        if (command.commandId() == null || command.commandId().isBlank()) {
            throw new IllegalArgumentException("A command needs its id: " + command);
        }
        if (!inbox.firstTime(CONSUMER, command.commandId())) {
            log.debug("Already taken: {}", command.commandId());
            return false;
        }
        switch (command) {
            case CustomerCommand.ProposeChange c -> propose(c);
            case CustomerCommand.RecordScannedIdentity c -> scans.record(c);
        }
        return true;
    }

    /**
     * The desk's change, as a change request whose id is the desk's: a retry is the same request. One
     * that changes nothing is approved at once — and the desk learns it as it learns any decision.
     */
    void propose(CustomerCommand.ProposeChange c) {
        var request = changeRequests.submit(c.customerId(), new ChangeRequests.Proposal(null, null, c.name(), c.email(),
                c.phone(), null, null, null, c.documentNumber(), c.origin()), c.commandId());
        if (ChangeRequest.Status.APPROVED.name().equals(request.status)) {
            customers.findById(request.customerId)
                    .ifPresent(customer -> events.changed(customer, false, request.id, "APPROVED", null));
        }
    }
}
