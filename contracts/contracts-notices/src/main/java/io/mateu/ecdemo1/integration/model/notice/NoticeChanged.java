package io.mateu.ecdemo1.integration.model.notice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A reception notice — what the desk must know of a customer, a reservation or a partner before the
 * guests arrive, when they check in, while they stay or when they leave — as it is now: created,
 * changed or deactivated. Published by the notices service on the {@code notices} topic, keyed by its
 * subject ({@link #key()}): the notices it keeps (a reservation's, a partner's) and the customers'
 * it takes from Salesforce through the MDM (customer-notices), which it does not edit. The whole
 * notice each time: a reader keeps the one with the highest {@code version} and asks for nothing.
 *
 * @param noticeId    stable for the notice's whole life
 * @param version     grows with every change of the notice: an older one never replaces a newer
 * @param subjectType what it is about
 * @param subjectId   the customer (MDM code, C-…), the reservation (CRS locator) or the partner (its
 *                    code in the ERP)
 * @param subjectName how the desk names the subject: a partner's name (what the PMS calls the agency
 *                    on a reservation); null when there is none
 * @param hotelCode   the CRS hotel it applies to; null, every hotel of the chain
 * @param text        what the desk reads
 * @param from        the first day it applies; null, since always
 * @param to          the last day it applies; null, with no end
 * @param moments     where the desk sees it
 * @param active      false once deactivated: a reader stops showing it
 * @param source      who masters it: {@code NOTICES} (kept by the notices service) or {@code SALESFORCE}
 * @param sourceRef   its record at the source, if another one (a Salesforce Case)
 */
public record NoticeChanged(String eventId, Instant occurredAt, String noticeId, long version,
                            SubjectType subjectType, String subjectId, String subjectName, String hotelCode,
                            String text, NoticeType type, LocalDate from, LocalDate to, List<NoticeMoment> moments,
                            boolean active, String source, String sourceRef) {

    public static final String TOPIC = "notices";

    /** What a notice is about. */
    public enum SubjectType { CUSTOMER, RESERVATION, PARTNER }

    /** How much it matters: a BLOCKING one must be read, and said so, before the guest checks in. */
    public enum NoticeType { INFORMATIVE, IMPORTANT, BLOCKING }

    /** Where the desk sees it: preparing the arrival, at check-in, while in house, at check-out. */
    public enum NoticeMoment { PRE_ARRIVAL, CHECK_IN, IN_HOUSE, CHECK_OUT }

    /** The Kafka key: the subject, {@code RESERVATION:12E45}. */
    public String key() {
        return subjectType + ":" + subjectId;
    }
}
