package io.mateu.ecdemo1.mapping.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param crsIntegrationUrl the CRS adapter, for the reservation, the partner and the CRS catalog
 * @param pmsIntegrationUrl the PMS adapter, for the PMS catalog
 * @param iaAgentUrl        the agent that proposes mappings
 * @param consoleUrl        where the mapping screens are, for links in notifications
 * @param resendAfter       how long a released process may stay silent before the resume message
 *                          is sent again — it can arrive before the process reached its wait
 */
@ConfigurationProperties("mapping")
public record MappingProperties(String crsIntegrationUrl, String pmsIntegrationUrl, String iaAgentUrl, String consoleUrl,
                                Duration resendAfter) {

    public MappingProperties {
        if (resendAfter == null) resendAfter = Duration.ofSeconds(30);
        if (consoleUrl == null) consoleUrl = "";
    }
}
