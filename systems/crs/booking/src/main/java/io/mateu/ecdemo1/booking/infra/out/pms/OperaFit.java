package io.mateu.ecdemo1.booking.infra.out.pms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Whether Opera would sell a demo booking's rooms for its stay — so that the bookings the demo creates
 * get into Opera instead of waiting on «no rooms of the type left». Only the demo's generator asks:
 * the CRS sells what it sells, Opera's room is the integration's business. Its room types become
 * Opera's by the pairs the demo's CRS catalog was generated with (crs-catalog/<hotel>-demo-pms.json):
 * the bookings are created before the onboarding approves any mapping. Asked to pms-integration-service,
 * read-only. Anything it cannot tell — no pairs, no answer — is «fits»: the demo goes on as before.
 */
@Component
@Slf4j
public class OperaFit {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pairs(String hotel, String pmsHotel, Map<String, String> roomTypes) {
    }

    final RestClient pms;

    public OperaFit(@Value("${pms-integration.url:}") String url) {
        if (url == null || url.isBlank()) {
            this.pms = null;
        } else {
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(2));
            factory.setReadTimeout(Duration.ofSeconds(8));
            this.pms = RestClient.builder().baseUrl(url).requestFactory(factory).build();
        }
    }

    /** Whether these rooms (a CRS room type once per room) fit in Opera for the stay. */
    public boolean fits(String hotelCode, LocalDate arrival, LocalDate departure, List<String> roomTypes) {
        if (pms == null) {
            return true;
        }
        var pairs = pairs(hotelCode);
        if (pairs.isEmpty()) {
            return true;
        }
        var opera = roomTypes.stream().map(t -> pairs.get().roomTypes().get(t)).toList();
        if (opera.contains(null)) {
            return true;
        }
        try {
            var answer = pms.get().uri(b -> b.path("/availability/{hotel}/fits").queryParam("arrival", arrival)
                    .queryParam("departure", departure).queryParam("rooms", opera.toArray()).build(pairs.get().pmsHotel()))
                    .retrieve().body(Boolean.class);
            return answer == null || answer;
        } catch (RuntimeException e) {
            log.debug("Opera's room for {} {}..{} not asked: {}", roomTypes, arrival, departure, e.getMessage());
            return true;
        }
    }

    static Optional<Pairs> pairs(String hotelCode) {
        try (InputStream in = OperaFit.class.getResourceAsStream("/crs-catalog/" + hotelCode + "-demo-pms.json")) {
            return in == null ? Optional.empty() : Optional.of(new ObjectMapper().readValue(in, Pairs.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
