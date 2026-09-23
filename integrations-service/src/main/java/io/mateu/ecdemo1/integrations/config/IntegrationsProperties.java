package io.mateu.ecdemo1.integrations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param crsIntegrationUrl    the CRS adapter: the hotel's future reservations, what they use, and
 *                             starting a projection for the backfill
 * @param pmsIntegrationUrl    the connector: trying a connection, the property's catalogue
 * @param mappingUrl           the mapping: the hotel's equivalence, pending codes, the backfill's gaps,
 *                             partner profiles and the causes the activation resolves
 * @param partnersUrl          the master of partners: which partners there are, and announcing them again
 * @param consoleUrl           where the integrations screens are, for links in notifications
 * @param cryptoKey            AES-256 key, base64, the connection secrets are stored under. From a
 *                             Secret, never from a file; nothing is stored if it is missing
 * @param opera                the chain's connection to Opera: every property of the chain lives in
 *                             the same tenant (HLA R24), so a new integration starts from it — the
 *                             form arrives filled in, and it is what lists the tenant's properties.
 *                             A hotel that needs another one overrides it on its own integration
 * @param gateCheck            how often the gates of every onboarding are looked at
 * @param recheck              how often a gate that needs a look outside — the property's catalogue, the
 *                             backfill's gaps — is looked at again, besides the "Recheck" action
 * @param backfillPerTick      reservations a backfill projects per tick: the throttle that keeps it
 *                             from drowning the CRS and OHIP (HLA, Volumetría)
 * @param backfillTick         how often a backfill projects its next batch
 * @param activationWindowDays how far ahead the backfill has to have reached for the integration to be
 *                             activated (HLA R25, «ventana próxima cubierta»)
 */
@ConfigurationProperties("integrations")
public record IntegrationsProperties(String crsIntegrationUrl, String pmsIntegrationUrl, String mappingUrl,
                                     String partnersUrl, String consoleUrl, String cryptoKey, Opera opera,
                                     Duration gateCheck, Duration recheck, int backfillPerTick, Duration backfillTick,
                                     int activationWindowDays) {

    /** How to reach the chain's Opera tenant. The secret comes from a Secret, never from a file. */
    public record Opera(String gatewayUrl, String appKey, String clientId, String clientSecret, String enterpriseId) {
    }

    public IntegrationsProperties {
        if (opera == null) opera = new Opera(null, null, null, null, null);
        if (consoleUrl == null) consoleUrl = "";
        if (gateCheck == null) gateCheck = Duration.ofSeconds(5);
        if (recheck == null) recheck = Duration.ofSeconds(30);
        if (backfillPerTick <= 0) backfillPerTick = 20;
        if (backfillTick == null) backfillTick = Duration.ofSeconds(2);
        if (activationWindowDays <= 0) activationWindowDays = 30;
    }
}
