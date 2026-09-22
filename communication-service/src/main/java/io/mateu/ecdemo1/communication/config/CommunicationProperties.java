package io.mateu.ecdemo1.communication.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param from           the sender of every mail
 * @param defaultEmail   seeded, on an empty database, as the recipient of everything — so a fresh
 *                       deployment tells someone before anyone has configured anyone
 * @param maxAttempts    how many times a failed delivery is tried
 */
@ConfigurationProperties("communication")
public record CommunicationProperties(String from, String defaultEmail, int maxAttempts) {

    public CommunicationProperties {
        if (from == null) from = "integration@ec1.mateu.io";
        if (maxAttempts <= 0) maxAttempts = 5;
    }
}
