package io.mateu.ecdemo1.mdm.outbox;

import io.mateu.ecdemo1.integration.model.customer.CustomerChanged;
import io.mateu.ecdemo1.integration.model.customer.CustomersMerged;
import io.mateu.ecdemo1.integration.model.customer.GoldenRecord;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** The MDM's events about a customer, with its golden record and its reservations, into the outbox. */
@Component
@RequiredArgsConstructor
public class CustomerEvents {

    final Outbox outbox;
    final SourceRepository sources;
    final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void changed(Customer c, boolean dataChanged, String changeRequestId, String decision) {
        outbox.append(new CustomerChanged(UUID.randomUUID().toString(), clock.instant(), c.id, c.version, golden(c),
                dataChanged, changeRequestId, decision, reservations(c.id)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void merged(Customer survivor, String absorbedId) {
        outbox.append(new CustomersMerged(UUID.randomUUID().toString(), clock.instant(), survivor.id, survivor.version,
                golden(survivor), absorbedId, reservations(survivor.id)));
    }

    static GoldenRecord golden(Customer c) {
        return new GoldenRecord(c.firstName, c.lastName, c.email, c.phone, c.nationality, c.birthDate, c.documentType,
                c.documentNumber);
    }

    List<String> reservations(String customerId) {
        return sources.findByCustomerIdOrderByFirstSeenAsc(customerId).stream()
                .map(s -> s.hotelCode + "/" + s.locator).distinct().toList();
    }
}
