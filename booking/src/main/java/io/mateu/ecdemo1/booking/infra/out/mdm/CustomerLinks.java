package io.mateu.ecdemo1.booking.infra.out.mdm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Who a booking's people are in the chain's other systems, asked of the customer MDM — which keeps
 * the cross references — for the booking's screen to link to: the customer in Clientes, the contact
 * in Salesforce, the guest profile in Opera, the stay in the front office. Only for showing: a MDM
 * that does not answer leaves the links out and nothing else.
 */
@Component
@Slf4j
public class CustomerLinks {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Passenger(int passenger, String role, String customerId, String name, String status,
                            String customerRoute, String salesforceContactId, String salesforceContactUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OperaProfile(String profileId, String customerId, String context) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FrontOfficeStay(String locator, String status, String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReservationLinks(String hotelCode, String locator, List<Passenger> passengers,
                                   List<OperaProfile> operaProfiles, FrontOfficeStay frontOffice) {
    }

    final RestClient mdm;

    public CustomerLinks(@Value("${customer-mdm.url:}") String url) {
        if (url == null || url.isBlank()) {
            this.mdm = null;
        } else {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(2));
            factory.setReadTimeout(Duration.ofSeconds(4));
            this.mdm = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        }
    }

    /** The booking's links, by the CRS's own hotel code and the booking's id — its locator. */
    public Optional<ReservationLinks> of(String hotelCode, String bookingId) {
        if (mdm == null || hotelCode == null || bookingId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(mdm.get().uri("/reservations/{hotel}/{locator}/links", hotelCode, bookingId)
                    .retrieve().body(ReservationLinks.class));
        } catch (RuntimeException e) {
            log.debug("The customer MDM did not answer for {}: {}", bookingId, e.getMessage());
            return Optional.empty();
        }
    }
}
