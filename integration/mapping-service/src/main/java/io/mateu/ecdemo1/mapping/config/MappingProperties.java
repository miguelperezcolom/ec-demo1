package io.mateu.ecdemo1.mapping.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

/**
 * @param crsIntegrationUrl the CRS adapter, for the reservation, the partner and the CRS catalog
 * @param pmsIntegrationUrl the PMS adapter, for the PMS catalog
 * @param integrationsUrl   the integrations, for whether a hotel's integration is active
 * @param iaAgentUrl        the agent that proposes mappings
 * @param consoleUrl        where the mapping screens are, for links in notifications
 * @param resendAfter       how long a released process may stay silent before the resume message
 *                          is sent again — it can arrive before the process reached its wait
 * @param resendFor         for how long after its release the resume message is sent again. The
 *                          engine tells a service nothing about a process it cancelled or finished,
 *                          so a process that has not answered in all that time is taken as gone: no
 *                          more resends, and it stays RELEASED for a person to discard.
 */
@ConfigurationProperties("mapping")
public record MappingProperties(String crsIntegrationUrl, String pmsIntegrationUrl, String integrationsUrl, String iaAgentUrl, String consoleUrl,
                                Duration resendAfter, Duration resendFor) {

    @ConstructorBinding
    public MappingProperties {
        if (resendAfter == null) resendAfter = Duration.ofSeconds(30);
        if (resendFor == null) resendFor = Duration.ofHours(6);
        if (consoleUrl == null) consoleUrl = "";
    }

    public MappingProperties(String crsIntegrationUrl, String pmsIntegrationUrl, String integrationsUrl, String iaAgentUrl,
                             String consoleUrl, Duration resendAfter) {
        this(crsIntegrationUrl, pmsIntegrationUrl, integrationsUrl, iaAgentUrl, consoleUrl, resendAfter, null);
    }

    /** Admin → Processes on the console: where a process the engine was not asked to cancel is cancelled by hand. */
    public String adminProcessesUrl() {
        return consoleUrl + "/workflow/processes";
    }
}
