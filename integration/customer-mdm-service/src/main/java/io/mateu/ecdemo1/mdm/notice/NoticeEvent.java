package io.mateu.ecdemo1.mdm.notice;

import java.time.LocalDate;

/**
 * What Salesforce says of a reception notice — its event ({@code AvisoRecepcionCambiado__e}), or its
 * Case read by the poll — in Salesforce's words: the picklists' values as they are.
 *
 * @param caseId      the notice's Case
 * @param mdmNoticeId the MDM's id, when it was written from the console
 * @param mdmId       the MDM customer of the Case's contact; null if the contact is not one
 * @param showAt      the multi-select's values, ";"-separated ("Check-in;Estancia")
 * @param active      ticked and the Case not closed (nor deleted)
 */
public record NoticeEvent(String caseId, String mdmNoticeId, String mdmId, String text, String type, LocalDate from,
                          LocalDate to, String showAt, boolean active) {
}
