package io.mateu.ecdemo1.audit.application;

import java.time.LocalDate;

/**
 * What a search of the trail asks for. Every field is optional; together they narrow it.
 *
 * @param text      looked for in every column, parameters and responses included
 * @param hotel     part of the hotel's code
 * @param user      part of who did it
 * @param action    part of the action's name
 * @param service   part of the service's name
 * @param from      the first day (in the trail's zone), inclusive
 * @param to        the last day (in the trail's zone), inclusive
 * @param succeeded carried out (true), refused (false), or either (null)
 */
public record AuditQuery(String text, String hotel, String user, String action, String service,
                         LocalDate from, LocalDate to, Boolean succeeded) {

    public static AuditQuery everything() {
        return new AuditQuery(null, null, null, null, null, null, null, null);
    }
}
