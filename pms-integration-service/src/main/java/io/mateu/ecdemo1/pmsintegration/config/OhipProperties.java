package io.mateu.ecdemo1.pmsintegration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * How to reach Opera through OHIP. The credentials come from the environment, never from a file in
 * the repository.
 *
 * @param externalSystemCode the code Opera knows the CRS by: it qualifies every external reference
 *                           this adapter writes and searches by
 * @param hotels             the properties this client may see. OHIP gives no way to list them
 *                           without naming a hotel or a hub first, so they are configuration
 * @param versionUdf         the numeric UDF of the reservation that carries the CRS version written
 *                           — the order guard (HLA R18, R28)
 * @param payAtHotelMethod   the payment method a reservation with no payment is recorded with.
 *                           Opera requires one; the CRS has none for "pays at the desk"
 */
@ConfigurationProperties("ohip")
public record OhipProperties(String url, String appKey, String clientId, String clientSecret, String enterpriseId,
                             String externalSystemCode, List<String> hotels, String versionUdf,
                             String payAtHotelMethod, Duration timeout) {

    public OhipProperties {
        if (externalSystemCode == null) externalSystemCode = "RIUCRS";
        if (hotels == null) hotels = List.of();
        if (versionUdf == null) versionUdf = "CRS_VERSION";
        if (payAtHotelMethod == null) payAtHotelMethod = "CA";
        if (timeout == null) timeout = Duration.ofSeconds(30);
    }
}
