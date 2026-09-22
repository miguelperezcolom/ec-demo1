package io.mateu.ecdemo1.pmsintegration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * What the connector writes to Opera the same way for every property. How to reach each property
 * — gateway, application key, client, secret, enterprise — is not here: it is each hotel's
 * integration's (see {@code Connections}).
 *
 * @param externalSystemCode the code Opera knows the CRS by: it qualifies every external reference
 *                           this adapter writes and searches by
 * @param versionUdf         the numeric UDF of the reservation that carries the CRS version written
 *                           — the order guard (HLA R18, R28)
 * @param payAtHotelMethod   the payment method a reservation with no payment is recorded with.
 *                           Opera requires one; the CRS has none for "pays at the desk"
 */
@ConfigurationProperties("ohip")
public record OhipProperties(String externalSystemCode, String versionUdf, String payAtHotelMethod, Duration timeout) {

    public OhipProperties {
        if (externalSystemCode == null) externalSystemCode = "RIUCRS";
        if (versionUdf == null) versionUdf = "CRS_VERSION";
        if (payAtHotelMethod == null) payAtHotelMethod = "CA";
        if (timeout == null) timeout = Duration.ofSeconds(30);
    }
}
