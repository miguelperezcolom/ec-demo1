package io.mateu.ecdemo1.booking.application.out.audit;

import java.util.Map;

/**
 * Where the CRS says who did what to a booking: the audit service's topic, as every service's
 * actions (AuditedAction). Written in the caller's transaction when there is one.
 */
public interface AuditTrail {

    /**
     * @param action     the auditable kind of action, e.g. "Booking cancelled"
     * @param bookingId  the booking — its locator — or null when it was never made
     * @param hotelCode  the booking's hotel
     * @param by         who did it
     * @param parameters what it was asked with
     * @param succeeded  whether it was carried out
     * @param response   what it answered, or why not
     */
    void record(String action, String bookingId, String hotelCode, String by, Map<String, Object> parameters,
                boolean succeeded, String response);

    /** Who is asking now: the person of the request, the agent for them, or the engine. */
    String actor();
}
