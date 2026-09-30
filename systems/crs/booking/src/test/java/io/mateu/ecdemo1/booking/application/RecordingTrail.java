package io.mateu.ecdemo1.booking.application;

import io.mateu.ecdemo1.booking.application.out.audit.AuditTrail;
import io.mateu.ecdemo1.booking.application.usecases.booking.BookingAudit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The audit trail of a test: what the use cases audited, in order, as "who" = {@link #actor}. */
public class RecordingTrail implements AuditTrail {

    public record Recorded(String action, String bookingId, String hotelCode, String by, Map<String, Object> parameters,
                           boolean succeeded, String response) {
    }

    public final List<Recorded> recorded = new ArrayList<>();
    public String actor = "ana";

    @Override
    public void record(String action, String bookingId, String hotelCode, String by, Map<String, Object> parameters,
                       boolean succeeded, String response) {
        recorded.add(new Recorded(action, bookingId, hotelCode, by, parameters, succeeded, response));
    }

    @Override
    public String actor() {
        return actor;
    }

    /** A BookingAudit over a trail nobody reads. */
    public static BookingAudit audit() {
        return new BookingAudit(new RecordingTrail(), null);
    }
}
