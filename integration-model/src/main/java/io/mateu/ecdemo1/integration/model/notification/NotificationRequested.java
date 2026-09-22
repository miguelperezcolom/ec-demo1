package io.mateu.ecdemo1.integration.model.notification;

import java.time.Instant;

/**
 * The integration asking for a person to be told something. It decides what and about which hotel;
 * the communication service decides who, and through which channel (HLA, "Notificación a las
 * personas").
 *
 * @param dedupKey the same key is one notification: a cause blocking three thousand reservations
 *                 is announced once, not three thousand times
 * @param link     where to act on it, when there is such a place
 */
public record NotificationRequested(String notificationId,
                                    NotificationType type,
                                    String hotelCode,
                                    String subject,
                                    String title,
                                    String body,
                                    String link,
                                    String dedupKey,
                                    Instant requestedAt) {
}
