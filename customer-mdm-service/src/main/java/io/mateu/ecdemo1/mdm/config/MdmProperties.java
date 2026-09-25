package io.mateu.ecdemo1.mdm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param salesforce        the org the MDM cleans in; off when it has no domain
 * @param projectionTick    how often provisional customers are sent to Salesforce to be cleaned
 * @param poll              how often Salesforce is asked for merges the event did not bring — the
 *                          safety net under the Pub/Sub subscription
 */
@ConfigurationProperties("mdm")
public record MdmProperties(Salesforce salesforce, Duration projectionTick, Duration poll) {

    /**
     * Client credentials: the MDM calls Salesforce machine to machine, as the user the org's
     * External Client App runs as.
     */
    public record Salesforce(String domain, String clientId, String clientSecret, String apiVersion,
                             String pubsubHost, int pubsubPort, boolean subscribe) {

        public boolean enabled() {
            return domain != null && !domain.isBlank() && clientId != null && !clientId.isBlank();
        }
    }
}
