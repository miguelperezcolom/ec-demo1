package io.mateu.ecdemo1.integrations.demo;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The demo's administration (the control plane's «Demo» page and the reset-demo process).
 *
 * @param engineDbUrl      the engine's database (workflow): the reset-demo runs are read there, and the
 *                         engine is emptied there (purge-engine). Empty: no engine here (local runs)
 * @param engineDbUsername its user
 * @param engineDbPassword its password, from a Secret
 * @param dataConsoleUrl   the data plane's console, where the engine's process pages are
 * @param bookingUrl       the CRS (booking): whether it takes bookings now
 * @param health           what check-demo-health asks: {@code name=url,…}, each url answering a
 *                         Spring Boot health ({@code "status":"UP"})
 * @param healthTimeout    how long each one may take
 */
@ConfigurationProperties("integrations.demo")
public record DemoProperties(String engineDbUrl, String engineDbUsername, String engineDbPassword,
                             String dataConsoleUrl, String bookingUrl, String health, Duration healthTimeout) {

    public DemoProperties {
        if (dataConsoleUrl == null) dataConsoleUrl = "";
        if (bookingUrl == null || bookingUrl.isBlank()) bookingUrl = "http://localhost:8108";
        if (health == null) health = "";
        if (healthTimeout == null) healthTimeout = Duration.ofSeconds(3);
    }

    /** The health checks, in order: name → url. */
    public Map<String, String> healthChecks() {
        var checks = new LinkedHashMap<String, String>();
        for (var entry : health.split(",")) {
            var parts = entry.trim().split("=", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                checks.put(parts[0].trim(), parts[1].trim());
            }
        }
        return checks;
    }

    /** The engine's page of a process, on the data plane's console (the id or the business key). */
    public String processLink(String processIdOrKey) {
        return dataConsoleUrl + "/workflow/processes/" + processIdOrKey;
    }
}
