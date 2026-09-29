package io.mateu.ecdemo1.mdm.marking;

import io.mateu.ecdemo1.mdm.footprint.Footprint;
import io.mateu.ecdemo1.mdm.store.Customer;
import io.mateu.ecdemo1.mdm.store.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Where a customer first came from: the channel of its first booking, read from the CRS once and kept
 * on the record. A call inside ec1, never to Salesforce. When the CRS does not answer it stays unknown
 * (null) and is asked again next time; when there is nothing to tell it by — no booking, or one the CRS
 * does not have — it is kept blank, and not asked again.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Origins {

    final Footprint footprint;
    final SourceRepository sources;

    /** Fills {@link Customer#origin} if it was never told; the caller saves the customer. */
    public void fill(Customer c) {
        if (c.origin != null || !footprint.readsBookings()) {
            return;
        }
        var first = footprint.codesOf(c).stream()
                .flatMap(code -> sources.findByCustomerIdOrderByFirstSeenAsc(code).stream())
                .min(java.util.Comparator.comparing(s -> s.firstSeen == null ? java.time.Instant.MAX : s.firstSeen))
                .orElse(null);
        if (first == null) {
            c.origin = "";
            return;
        }
        try {
            var origin = footprint.lookup(first.locator)
                    .map(b -> Marking.Origin.ofChannel(b.channelCode(), b.partnerCode()))
                    .orElse(null);
            c.origin = origin == null ? "" : origin.name();
        } catch (RuntimeException e) {
            log.debug("Origin of {} not told yet, the CRS did not answer: {}", c.id, e.getMessage());
        }
    }
}
