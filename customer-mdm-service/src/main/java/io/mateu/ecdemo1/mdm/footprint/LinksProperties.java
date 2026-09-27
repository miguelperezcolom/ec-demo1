package io.mateu.ecdemo1.mdm.footprint;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where the systems a customer is known in answer, and where a person opens them.
 *
 * @param bookingUrl           the CRS's REST API, read for a reservation's dates and status; off when blank
 * @param frontOfficeUrl       the front office's REST API (inside the cluster), read for a guest's stays; off when blank
 * @param frontOfficePublicUrl where a person opens the front office — a stay is {@code <url>/reservas/<locator>}
 * @param salesforceUrl        where a person opens the org (Lightning); blank derives it from the org's domain
 * @param timeout              how long a screen waits for another system before showing what it has
 */
@ConfigurationProperties("mdm.links")
public record LinksProperties(String bookingUrl, String frontOfficeUrl, String frontOfficePublicUrl,
                              String salesforceUrl, Duration timeout) {

    public LinksProperties {
        timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
    }
}
