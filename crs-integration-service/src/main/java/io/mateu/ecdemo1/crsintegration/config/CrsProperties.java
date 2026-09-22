package io.mateu.ecdemo1.crsintegration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * @param bookingUrl  the CRS's booking API
 * @param partnersUrl the master of partners' API
 * @param routes      business event type → the process definition it starts. Configuration, not
 *                    code per use case: a new use case is a new line here and a new definition
 */
@ConfigurationProperties("crs")
public record CrsProperties(String bookingUrl, String partnersUrl, Map<String, String> routes) {

    public CrsProperties {
        if (routes == null) routes = Map.of();
    }
}
