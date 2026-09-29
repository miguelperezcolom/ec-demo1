package io.mateu.ecdemo1.integration.model.customer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A reception notice of a customer — what the desk must know when they arrive, stay or leave — as
 * Salesforce, its master, has it now: created, changed, deactivated. Published by the MDM on the
 * {@code customer-notices} topic, keyed by the customer, once Salesforce confirmed it (a notice written
 * from the Clientes console is not one until Salesforce has it). The whole notice each time: a reader
 * keeps the one with the highest {@code version} and asks for nothing.
 *
 * @param noticeId   the MDM's id of the notice, stable for its whole life
 * @param version    grows with every change of the notice: an older one never replaces a newer
 * @param customerId the MDM customer it is about (a front office's guest or companion id). The Kafka key
 * @param text       what the desk reads
 * @param from       the first day it applies; null, since always
 * @param to         the last day it applies; null, with no end
 * @param showAt     where the desk sees it
 * @param active     false once deactivated: a reader stops showing it
 * @param salesforceId the notice's record in Salesforce (a Case)
 */
public record CustomerNoticeChanged(String eventId, Instant occurredAt, String noticeId, long version,
                                    String customerId, String text, NoticeType type, LocalDate from, LocalDate to,
                                    List<NoticeMoment> showAt, boolean active, String salesforceId) {

    public static final String TOPIC = "customer-notices";

    /** How much it matters: a BLOCKING one must be read, and said so, before the guest checks in. */
    public enum NoticeType { INFORMATIVE, IMPORTANT, BLOCKING }

    /** Where the desk sees it. */
    public enum NoticeMoment { CHECK_IN, CHECK_OUT, STAY }
}
