package io.mateu.ecdemo1.pmsintegration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param crsIntegrationUrl the CRS adapter, for the reservation and the partner in the integration's terms
 * @param mappingUrl        the mapping, for translations, partner profiles and causes
 * @param integrationsUrl   the integrations, for how to reach each Opera property
 * @param alertAfter        how long a step may keep failing and being retried before someone is told —
 *                          while it goes on being retried (HLA R14)
 */
@ConfigurationProperties("pms-integration")
public record PmsIntegrationProperties(String crsIntegrationUrl, String mappingUrl, String integrationsUrl,
                                       Duration alertAfter) {

    public PmsIntegrationProperties {
        if (alertAfter == null) alertAfter = Duration.ofMinutes(10);
    }
}
