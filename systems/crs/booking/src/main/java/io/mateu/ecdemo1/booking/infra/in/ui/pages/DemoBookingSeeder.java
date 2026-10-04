package io.mateu.ecdemo1.booking.infra.in.ui.pages;

import io.mateu.ecdemo1.booking.infra.out.persistence.BookingEntityRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * The demo reset's seed (seed-demo-bookings@1): the same ten MRU01 bookings «+ 10 reservas demo»
 * makes — where flow 1 starts; without an integration they stay in the CRS — tagged
 * «demo-reset:&lt;processKey&gt;» in their comments. Once per process: run again, it finds them by the
 * tag and makes no others.
 */
@Service
@RequiredArgsConstructor
public class DemoBookingSeeder {

    public static final String TAG = "demo-reset:";

    final ObjectProvider<DemoBookingsForm> forms;
    final BookingEntityRepository bookings;

    /** @return the bookings' locators */
    public List<String> seed(String processKey) {
        var tag = TAG + processKey;
        var existing = bookings.idsCommentedWith(tag);
        if (!existing.isEmpty()) {
            return existing;
        }
        var outcome = forms.getObject().create(null, tag);
        if (outcome.created().isEmpty()) {
            throw new IllegalStateException("No demo booking could be created: "
                    + String.join("; ", outcome.failed()) + " " + String.join("; ", outcome.notes()));
        }
        return outcome.created();
    }
}
