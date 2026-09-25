package io.mateu.ecdemo1.pmsintegration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param crsIntegrationUrl the CRS adapter, for the reservation and the partner in the integration's terms
 * @param mappingUrl        the mapping, for translations, partner profiles and causes
 * @param integrationsUrl   the integrations, for how to reach each Opera property
 * @param customerMdmUrl    the customer MDM, for who the passengers are
 * @param frontOfficeUrl      the hotel's front office, which every reservation written into the PMS is
 *                            written into too; blank, none
 * @param frontOfficeHotels   the CRS hotels that have that front office
 * @param alertAfter        how long a step may keep failing and being retried before someone is told —
 *                          while it goes on being retried (HLA R14)
 */
@ConfigurationProperties("pms-integration")
public record PmsIntegrationProperties(String crsIntegrationUrl, String mappingUrl, String integrationsUrl,
                                       String customerMdmUrl, String frontOfficeUrl,
                                       java.util.List<String> frontOfficeHotels, Duration alertAfter) {

    public PmsIntegrationProperties {
        if (alertAfter == null) alertAfter = Duration.ofMinutes(10);
    }
}
