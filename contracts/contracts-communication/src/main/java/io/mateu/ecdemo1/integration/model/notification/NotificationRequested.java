package io.mateu.ecdemo1.integration.model.notification;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The integration asking for a person to be told something. It decides what and about which hotel;
 * the communication service decides who, and through which channel (HLA, "Notificación a las
 * personas").
 *
 * <p>Or, with {@code addressees}, a notification for those people in particular — the intranet's: a
 * new item for its readers, a confirmation that falls due. It goes into their inbox and their browsers
 * and nobody else's: the recipients table decides who hears of a hotel's affairs, not of someone's own.
 *
 * @param dedupKey   the same key is one notification: a cause blocking three thousand reservations
 *                   is announced once, not three thousand times
 * @param link       where to act on it, when there is such a place
 * @param addressees the people it is for, by username; absent (the usual) is whoever the recipients say
 */
public record NotificationRequested(String notificationId,
                                    NotificationType type,
                                    String hotelCode,
                                    String subject,
                                    String title,
                                    String body,
                                    String link,
                                    String dedupKey,
                                    Instant requestedAt,
                                    @JsonInclude(JsonInclude.Include.NON_NULL) List<String> addressees) {

    /** A notification the recipients route: what every producer but the intranet sends. */
    public NotificationRequested(String notificationId, NotificationType type, String hotelCode, String subject,
                                 String title, String body, String link, String dedupKey, Instant requestedAt) {
        this(notificationId, type, hotelCode, subject, title, body, link, dedupKey, requestedAt, null);
    }

}
