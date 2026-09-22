package io.mateu.ecdemo1.integration.model.integration;

/**
 * How to reach one Opera property through OHIP. Each hotel's integration carries its own (HLA,
 * «Aislamiento por hotel»); the secret travels only between the integrations service and the
 * connector, never to a screen.
 */
public record OhipConnection(String pmsHotelCode, String gatewayUrl, String appKey, String clientId,
                             String clientSecret, String enterpriseId) {

    @Override
    public String toString() {
        return "OhipConnection[%s at %s, client %s, enterprise %s]".formatted(pmsHotelCode, gatewayUrl, clientId, enterpriseId);
    }
}
