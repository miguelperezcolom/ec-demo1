package io.mateu.ecdemo1.integrations.usage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Where the external APIs' usage is counted: every service that calls Salesforce or Opera serves its
 * own at {@code GET /usage/{api}}, and the header adds them up.
 *
 * @param salesforce the services calling Salesforce (the customer MDM)
 * @param opera      the services calling Opera (the PMS connector)
 * @param timeout    how long one may take to answer before the header does without it
 * @param cache      how long an answer is reused: every open console asks, the services should not
 *                   hear every one of them
 */
@ConfigurationProperties("integrations.usage")
public record UsageProperties(List<String> salesforce, List<String> opera, Duration timeout, Duration cache) {

    public UsageProperties {
        salesforce = salesforce == null ? List.of() : salesforce.stream().filter(u -> !u.isBlank()).toList();
        opera = opera == null ? List.of() : opera.stream().filter(u -> !u.isBlank()).toList();
        if (timeout == null) timeout = Duration.ofSeconds(2);
        if (cache == null) cache = Duration.ofSeconds(15);
    }
}
