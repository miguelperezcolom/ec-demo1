package io.mateu.ecdemo1.mdm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param salesforce        the org the MDM cleans in; off when it has no domain
 * @param crsIntegrationUrl the CRS adapter: projecting a reservation again is how a changed customer
 *                          code travels to the PMS (HLA CRM-MDM, F005)
 * @param projectionTick    how often provisional customers are sent to Salesforce to be cleaned
 * @param poll              how often Salesforce is asked for merges the event did not bring — the
 *                          safety net under the Pub/Sub subscription
 * @param propagationTick   how often applied merges are carried to the reservations they touch
 */
@ConfigurationProperties("mdm")
public record MdmProperties(Salesforce salesforce, String crsIntegrationUrl, Duration projectionTick, Duration poll,
                            Duration propagationTick, String frontOfficeUrl) {

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
