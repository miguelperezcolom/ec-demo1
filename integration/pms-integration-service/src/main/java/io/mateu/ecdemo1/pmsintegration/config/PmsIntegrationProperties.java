package io.mateu.ecdemo1.pmsintegration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param crsIntegrationUrl the CRS adapter, for the reservation and the partner in the integration's terms
 * @param mappingUrl        the mapping, for translations, partner profiles and causes
 * @param integrationsUrl   the integrations, for how to reach each Opera property
 * @param customerMdmUrl    the customer MDM, for who the passengers are
 * @param noShowCancellationCodes the PMS's cancellation codes that mean a no-show: a reservation
 *                                cancelled with one of them is a no-show in the front office
 * @param alertAfter        how long a step may keep failing and being retried before someone is told —
 *                          while it goes on being retried (HLA R14)
 */
@ConfigurationProperties("pms-integration")
public record PmsIntegrationProperties(String crsIntegrationUrl, String mappingUrl, String integrationsUrl,
                                       String customerMdmUrl, java.util.List<String> noShowCancellationCodes,
                                       Duration alertAfter) {

    public PmsIntegrationProperties {
        if (alertAfter == null) alertAfter = Duration.ofMinutes(10);
        if (noShowCancellationCodes == null || noShowCancellationCodes.isEmpty()) noShowCancellationCodes = java.util.List.of("NOSHOW");
    }
}
